package com.example.aandi_post_web_server.user.application.service

import com.example.aandi_post_web_server.user.entity.ReportUser
import com.example.aandi_post_web_server.user.infrastructure.event.AuthUserEvent
import com.example.aandi_post_web_server.user.infrastructure.event.AuthUserEventType
import com.mongodb.client.result.UpdateResult
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import org.bson.BsonString
import org.bson.Document
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.time.temporal.ChronoUnit

class ReportUserSyncServiceTest : StringSpec({
    "profile event atomically upserts an active user with a delete-aware freshness condition" {
        val fixture = ReportUserSyncFixture()
        val sourceEventTime = Instant.parse("2026-03-20T01:00:42.922398600Z")
        val persistedEventTime = sourceEventTime.truncatedTo(ChronoUnit.MILLIS)
        fixture.stubSuccessfulUpsert()

        StepVerifier.create(fixture.service.sync(userProfileUpdatedEvent(sourceEventTime)))
            .expectNext(ReportUserSyncOutcome.UPSERTED)
            .verifyComplete()

        val (query, update) = fixture.captureUpsert()
        query.queryObject["_id"] shouldBe "user-1"
        val freshnessConditions = query.queryObject["\$or"] as List<*>
        freshnessConditions.shouldHaveSize(3)
        val updatedAtConditions = freshnessConditions
            .mapNotNull { (it as Document)["updatedAt"] as? Document }
        updatedAtConditions.mapNotNull { it["\$lt"] }.single() shouldBe persistedEventTime
        updatedAtConditions.mapNotNull { it["\$exists"] }.single() shouldBe false
        val equalActiveCondition = freshnessConditions
            .map { it as Document }
            .single { it.containsKey("\$and") }["\$and"] as List<*>
        equalActiveCondition.map { it as Document }.single { it.containsKey("updatedAt") }["updatedAt"] shouldBe
            persistedEventTime
        equalActiveCondition.map { it as Document }.single { it.containsKey("deletedAt") }["deletedAt"] shouldBe null

        val set = update.updateObject["\$set"] as Document
        set["publicCode"] shouldBe "#FL301"
        set["username"] shouldBe "mekazon"
        set["role"] shouldBe "USER"
        set["updatedAt"] shouldBe persistedEventTime
        val unset = update.updateObject["\$unset"] as Document
        unset shouldContainKey "deletedAt"

        Mockito.verify(fixture.template, Mockito.never()).updateFirst(
            ArgumentMatchers.any(Query::class.java),
            ArgumentMatchers.any(Update::class.java),
            ArgumentMatchers.eq(ReportUser::class.java),
        )
        Mockito.verify(fixture.template, Mockito.never()).insert(ArgumentMatchers.any(ReportUser::class.java))
        Mockito.verify(fixture.template, Mockito.never()).remove(
            ArgumentMatchers.any(Query::class.java),
            ArgumentMatchers.eq(ReportUser::class.java),
        )
    }

    "delete tombstone prevents an older profile event from resurrecting the user" {
        val fixture = ReportUserSyncFixture()
        val profileTime = Instant.parse("2026-03-20T01:00:00Z")
        val deleteTime = profileTime.plusSeconds(60)
        val duplicate = DuplicateKeyException("duplicate id")
        fixture.stubUpsertFailure(duplicate)
        Mockito.`when`(fixture.template.findById("user-1", ReportUser::class.java))
            .thenReturn(Mono.just(tombstonedUser(deleteTime)))

        StepVerifier.create(fixture.service.sync(userProfileUpdatedEvent(profileTime)))
            .expectNext(ReportUserSyncOutcome.IGNORED_STALE)
            .verifyComplete()

        Mockito.verify(fixture.template).findById("user-1", ReportUser::class.java)
        Mockito.verify(fixture.template, Mockito.never()).insert(ArgumentMatchers.any(ReportUser::class.java))
    }

    "delete wins when profile and delete events have the same timestamp" {
        val fixture = ReportUserSyncFixture()
        val eventTime = Instant.parse("2026-03-20T01:00:00Z")
        fixture.stubUpsertFailure(DuplicateKeyException("duplicate id"))
        Mockito.`when`(fixture.template.findById("user-1", ReportUser::class.java))
            .thenReturn(Mono.just(tombstonedUser(eventTime)))

        StepVerifier.create(fixture.service.sync(userProfileUpdatedEvent(eventTime)))
            .expectNext(ReportUserSyncOutcome.IGNORED_STALE)
            .verifyComplete()
    }

    "delete wins within the same MongoDB millisecond bucket" {
        val fixture = ReportUserSyncFixture()
        val deleteTime = Instant.parse("2026-03-20T01:00:42.922398600Z")
        val laterProfileTime = Instant.parse("2026-03-20T01:00:42.922399000Z")
        val persistedTime = deleteTime.truncatedTo(ChronoUnit.MILLIS)
        fixture.stubUpsertFailure(DuplicateKeyException("duplicate id"))
        Mockito.`when`(fixture.template.findById("user-1", ReportUser::class.java))
            .thenReturn(Mono.just(tombstonedUser(persistedTime)))

        StepVerifier.create(fixture.service.sync(userProfileUpdatedEvent(laterProfileTime)))
            .expectNext(ReportUserSyncOutcome.IGNORED_STALE)
            .verifyComplete()
    }

    "profile event newer than a tombstone can reactivate the user" {
        val fixture = ReportUserSyncFixture()
        val profileTime = Instant.parse("2026-03-20T01:10:00Z")
        fixture.stubSuccessfulUpsert()

        StepVerifier.create(fixture.service.sync(userProfileUpdatedEvent(profileTime)))
            .expectNext(ReportUserSyncOutcome.UPSERTED)
            .verifyComplete()

        val (_, update) = fixture.captureUpsert()
        val unset = update.updateObject["\$unset"] as Document
        unset shouldContainKey "deletedAt"
    }

    "delete event atomically upserts a profile-cleared tombstone even when the user is absent" {
        val fixture = ReportUserSyncFixture()
        val eventTime = Instant.parse("2026-03-20T02:00:00Z")
        fixture.stubSuccessfulUpsert(upsertedId = "user-1")

        StepVerifier.create(fixture.service.sync(userDeletedEvent(eventTime)))
            .expectNext(ReportUserSyncOutcome.DELETED)
            .verifyComplete()

        val (query, update) = fixture.captureUpsert()
        query.queryObject["_id"] shouldBe "user-1"
        val freshnessConditions = query.queryObject["\$or"] as List<*>
        val updatedAtConditions = freshnessConditions.map { (it as Document)["updatedAt"] as Document }
        updatedAtConditions.mapNotNull { it["\$lte"] }.single() shouldBe eventTime

        val set = update.updateObject["\$set"] as Document
        set["publicCode"] shouldBe "__deleted__:user-1"
        set["username"] shouldBe "__deleted__"
        set["role"] shouldBe "DELETED"
        set["updatedAt"] shouldBe eventTime
        set["deletedAt"] shouldBe eventTime
        val unset = update.updateObject["\$unset"] as Document
        unset shouldContainKey "nickname"
        unset shouldContainKey "profileImageUrl"

        Mockito.verify(fixture.template, Mockito.never()).remove(
            ArgumentMatchers.any(Query::class.java),
            ArgumentMatchers.eq(ReportUser::class.java),
        )
    }

    "delete older than the active user is ignored" {
        val fixture = ReportUserSyncFixture()
        val deleteTime = Instant.parse("2026-03-20T01:00:00Z")
        fixture.stubUpsertFailure(DuplicateKeyException("duplicate id"))
        Mockito.`when`(fixture.template.findById("user-1", ReportUser::class.java))
            .thenReturn(Mono.just(activeUser(deleteTime.plusSeconds(60))))

        StepVerifier.create(fixture.service.sync(userDeletedEvent(deleteTime)))
            .expectNext(ReportUserSyncOutcome.IGNORED_STALE)
            .verifyComplete()
    }

    "delete duplicate-key conflict at the same timestamp is propagated" {
        val fixture = ReportUserSyncFixture()
        val eventTime = Instant.parse("2026-03-20T01:00:00Z")
        val failure = DuplicateKeyException("reserved tombstone publicCode collision")
        fixture.stubUpsertFailure(failure)
        Mockito.`when`(fixture.template.findById("user-1", ReportUser::class.java))
            .thenReturn(Mono.just(activeUser(eventTime)))

        StepVerifier.create(fixture.service.sync(userDeletedEvent(eventTime)))
            .expectErrorMatches { it === failure }
            .verify()
    }

    "profile duplicate-key conflict against same-time active data is propagated" {
        val fixture = ReportUserSyncFixture()
        val eventTime = Instant.parse("2026-03-20T01:00:00Z")
        val failure = DuplicateKeyException("publicCode collision")
        fixture.stubUpsertFailure(failure)
        Mockito.`when`(fixture.template.findById("user-1", ReportUser::class.java))
            .thenReturn(Mono.just(activeUser(eventTime)))

        StepVerifier.create(fixture.service.sync(userProfileUpdatedEvent(eventTime)))
            .expectErrorMatches { it === failure }
            .verify()
    }

    "duplicate key without the same user id is propagated" {
        val fixture = ReportUserSyncFixture()
        val failure = DuplicateKeyException("duplicate publicCode")
        fixture.stubUpsertFailure(failure)
        Mockito.`when`(fixture.template.findById("user-1", ReportUser::class.java))
            .thenReturn(Mono.empty())

        StepVerifier.create(fixture.service.sync(userProfileUpdatedEvent(Instant.parse("2026-03-20T01:00:00Z"))))
            .expectErrorMatches { it === failure }
            .verify()
    }

    "duplicate key against an older same-id document is propagated for retry" {
        val fixture = ReportUserSyncFixture()
        val eventTime = Instant.parse("2026-03-20T01:00:00Z")
        val failure = DuplicateKeyException("duplicate id")
        fixture.stubUpsertFailure(failure)
        Mockito.`when`(fixture.template.findById("user-1", ReportUser::class.java))
            .thenReturn(Mono.just(activeUser(eventTime.minusSeconds(1))))

        StepVerifier.create(fixture.service.sync(userProfileUpdatedEvent(eventTime)))
            .expectErrorMatches { it === failure }
            .verify()
    }

    "MongoDB upsert failure is propagated without a fallback insert" {
        val fixture = ReportUserSyncFixture()
        val failure = IllegalStateException("mongo unavailable")
        fixture.stubUpsertFailure(failure)

        StepVerifier.create(fixture.service.sync(userProfileUpdatedEvent(Instant.parse("2026-03-20T01:00:00Z"))))
            .expectErrorMatches { it === failure }
            .verify()

        Mockito.verify(fixture.template, Mockito.never()).insert(ArgumentMatchers.any(ReportUser::class.java))
        Mockito.verify(fixture.template, Mockito.never()).findById("user-1", ReportUser::class.java)
    }

    "event timestamp is required before any MongoDB call" {
        val fixture = ReportUserSyncFixture()
        val event = userProfileUpdatedEvent(Instant.parse("2026-03-20T01:00:00Z"))
            .copy(updatedAt = null, occurredAt = null)

        shouldThrow<IllegalArgumentException> {
            fixture.service.sync(event)
        }

        Mockito.verifyNoInteractions(fixture.template)
    }

    "profile publicCode cannot use the tombstone sentinel namespace" {
        val fixture = ReportUserSyncFixture()
        val event = userProfileUpdatedEvent(Instant.parse("2026-03-20T01:00:00Z"))
            .copy(publicCode = "__deleted__:another-user")

        shouldThrow<IllegalArgumentException> {
            fixture.service.sync(event)
        }

        Mockito.verifyNoInteractions(fixture.template)
    }
})

private class ReportUserSyncFixture {
    val template: ReactiveMongoTemplate = Mockito.mock(ReactiveMongoTemplate::class.java)
    val service = ReportUserSyncService(template)

    fun stubSuccessfulUpsert(upsertedId: String? = null) {
        val upsertedBsonId = upsertedId?.let(::BsonString)
        Mockito.`when`(
            template.upsert(
                ArgumentMatchers.any(Query::class.java),
                ArgumentMatchers.any(Update::class.java),
                ArgumentMatchers.eq(ReportUser::class.java),
            )
        ).thenReturn(Mono.just(UpdateResult.acknowledged(0L, 1L, upsertedBsonId)))
    }

    fun stubUpsertFailure(failure: Throwable) {
        Mockito.`when`(
            template.upsert(
                ArgumentMatchers.any(Query::class.java),
                ArgumentMatchers.any(Update::class.java),
                ArgumentMatchers.eq(ReportUser::class.java),
            )
        ).thenReturn(Mono.error(failure))
    }

    fun captureUpsert(): Pair<Query, Update> {
        val queryCaptor = ArgumentCaptor.forClass(Query::class.java)
        val updateCaptor = ArgumentCaptor.forClass(Update::class.java)
        Mockito.verify(template).upsert(
            queryCaptor.capture(),
            updateCaptor.capture(),
            ArgumentMatchers.eq(ReportUser::class.java),
        )
        return queryCaptor.value to updateCaptor.value
    }
}

private fun userProfileUpdatedEvent(updatedAt: Instant) = AuthUserEvent(
    eventType = AuthUserEventType.UserProfileUpdated,
    eventId = "evt-profile",
    occurredAt = updatedAt,
    id = "user-1",
    publicCode = "#FL301",
    username = "mekazon",
    role = "USER",
    nickname = "메카존",
    profileImageUrl = "https://example.com/profile.png",
    updatedAt = updatedAt,
)

private fun userDeletedEvent(occurredAt: Instant) = AuthUserEvent(
    eventType = AuthUserEventType.UserDeleted,
    eventId = "evt-delete",
    occurredAt = occurredAt,
    id = "user-1",
)

private fun activeUser(updatedAt: Instant) = ReportUser(
    id = "user-1",
    publicCode = "#FL301",
    username = "mekazon",
    role = "USER",
    updatedAt = updatedAt,
)

private fun tombstonedUser(deletedAt: Instant) = ReportUser(
    id = "user-1",
    publicCode = "__deleted__:user-1",
    username = "__deleted__",
    role = "DELETED",
    updatedAt = deletedAt,
    deletedAt = deletedAt,
)
