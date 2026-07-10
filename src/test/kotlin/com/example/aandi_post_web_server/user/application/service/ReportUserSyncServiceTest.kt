package com.example.aandi_post_web_server.user.application.service

import com.example.aandi_post_web_server.user.entity.ReportUser
import com.example.aandi_post_web_server.user.infrastructure.event.AuthUserEvent
import com.example.aandi_post_web_server.user.infrastructure.event.AuthUserEventType
import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import org.bson.Document
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
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

class ReportUserSyncServiceTest : StringSpec({
    "기존 사용자가 있으면 최신 사용자 이벤트로 업데이트한다" {
        val template = Mockito.mock(ReactiveMongoTemplate::class.java)
        val service = ReportUserSyncService(template)
        val event = userProfileUpdatedEvent(updatedAt = Instant.parse("2026-03-20T01:00:00Z"))

        Mockito.`when`(
            template.updateFirst(
                ArgumentMatchers.any(Query::class.java),
                ArgumentMatchers.any(Update::class.java),
                ArgumentMatchers.eq(ReportUser::class.java),
            )
        ).thenReturn(Mono.just(UpdateResult.acknowledged(1L, 1L, null)))

        StepVerifier.create(service.sync(event))
            .expectNext(ReportUserSyncOutcome.UPSERTED)
            .verifyComplete()

        Mockito.verify(template, Mockito.never()).insert(ArgumentMatchers.any(ReportUser::class.java))
    }

    "더 오래된 이벤트가 나중에 오면 stale 로 무시한다" {
        val template = Mockito.mock(ReactiveMongoTemplate::class.java)
        val service = ReportUserSyncService(template)
        val event = userProfileUpdatedEvent(updatedAt = Instant.parse("2026-03-20T01:00:00Z"))

        Mockito.`when`(
            template.updateFirst(
                ArgumentMatchers.any(Query::class.java),
                ArgumentMatchers.any(Update::class.java),
                ArgumentMatchers.eq(ReportUser::class.java),
            )
        ).thenReturn(Mono.just(UpdateResult.acknowledged(0L, 0L, null)))
        Mockito.`when`(template.insert(ArgumentMatchers.any(ReportUser::class.java)))
            .thenReturn(Mono.error(DuplicateKeyException("duplicate publicCode")))
        Mockito.`when`(template.findById("user-1", ReportUser::class.java))
            .thenReturn(
                Mono.just(
                    ReportUser(
                        id = "user-1",
                        publicCode = "#FL302",
                        username = "mekazon",
                        role = "USER",
                        updatedAt = Instant.parse("2026-03-20T01:10:00Z"),
                    )
                )
            )

        StepVerifier.create(service.sync(event))
            .expectNext(ReportUserSyncOutcome.IGNORED_STALE)
            .verifyComplete()
    }

    "insert 중복 충돌 시 동일 ID 사용자가 없으면 예외를 전파한다" {
        val template = Mockito.mock(ReactiveMongoTemplate::class.java)
        val service = ReportUserSyncService(template)
        val event = userProfileUpdatedEvent(updatedAt = Instant.parse("2026-03-20T01:00:00Z"))
        val failure = DuplicateKeyException("duplicate publicCode")

        Mockito.`when`(
            template.updateFirst(
                ArgumentMatchers.any(Query::class.java),
                ArgumentMatchers.any(Update::class.java),
                ArgumentMatchers.eq(ReportUser::class.java),
            )
        ).thenReturn(Mono.just(UpdateResult.acknowledged(0L, 0L, null)))
        Mockito.`when`(template.insert(ArgumentMatchers.any(ReportUser::class.java)))
            .thenReturn(Mono.error(failure))
        Mockito.`when`(template.findById("user-1", ReportUser::class.java))
            .thenReturn(Mono.empty())

        StepVerifier.create(service.sync(event))
            .expectErrorMatches { it === failure }
            .verify()
    }

    "insert 중복 충돌 시 동일 ID 사용자가 더 오래됐으면 예외를 전파한다" {
        val template = Mockito.mock(ReactiveMongoTemplate::class.java)
        val service = ReportUserSyncService(template)
        val eventTime = Instant.parse("2026-03-20T01:00:00Z")
        val event = userProfileUpdatedEvent(updatedAt = eventTime)
        val failure = DuplicateKeyException("duplicate id")

        Mockito.`when`(
            template.updateFirst(
                ArgumentMatchers.any(Query::class.java),
                ArgumentMatchers.any(Update::class.java),
                ArgumentMatchers.eq(ReportUser::class.java),
            )
        ).thenReturn(Mono.just(UpdateResult.acknowledged(0L, 0L, null)))
        Mockito.`when`(template.insert(ArgumentMatchers.any(ReportUser::class.java)))
            .thenReturn(Mono.error(failure))
        Mockito.`when`(template.findById("user-1", ReportUser::class.java))
            .thenReturn(
                Mono.just(
                    ReportUser(
                        id = "user-1",
                        publicCode = "#FL301",
                        username = "mekazon",
                        role = "USER",
                        updatedAt = eventTime.minusSeconds(1),
                    )
                )
            )

        StepVerifier.create(service.sync(event))
            .expectErrorMatches { it === failure }
            .verify()
    }

    "사용자 업데이트 저장 실패를 전파하고 insert 하지 않는다" {
        val template = Mockito.mock(ReactiveMongoTemplate::class.java)
        val service = ReportUserSyncService(template)
        val event = userProfileUpdatedEvent(updatedAt = Instant.parse("2026-03-20T01:00:00Z"))
        val failure = IllegalStateException("mongo unavailable")

        Mockito.`when`(
            template.updateFirst(
                ArgumentMatchers.any(Query::class.java),
                ArgumentMatchers.any(Update::class.java),
                ArgumentMatchers.eq(ReportUser::class.java),
            )
        ).thenReturn(Mono.error(failure))

        StepVerifier.create(service.sync(event))
            .expectErrorMatches { it === failure }
            .verify()

        Mockito.verify(template, Mockito.never()).insert(ArgumentMatchers.any(ReportUser::class.java))
    }

    "삭제 이벤트는 최신성 조건을 만족할 때만 문서를 제거한다" {
        val template = Mockito.mock(ReactiveMongoTemplate::class.java)
        val service = ReportUserSyncService(template)
        val event = AuthUserEvent(
            eventType = AuthUserEventType.UserDeleted,
            eventId = "evt-3",
            occurredAt = Instant.parse("2026-03-20T02:00:00Z"),
            id = "user-1",
        )

        Mockito.`when`(
            template.remove(
                ArgumentMatchers.any(Query::class.java),
                ArgumentMatchers.eq(ReportUser::class.java),
            )
        ).thenReturn(Mono.just(DeleteResult.acknowledged(1L)))

        StepVerifier.create(service.sync(event))
            .expectNext(ReportUserSyncOutcome.DELETED)
            .verifyComplete()
    }

    "삭제가 적용되지 않으면 stale 로 분류하고 최신성 조건을 사용한다" {
        val template = Mockito.mock(ReactiveMongoTemplate::class.java)
        val service = ReportUserSyncService(template)
        val eventTime = Instant.parse("2026-03-20T01:00:00Z")
        val event = AuthUserEvent(
            eventType = AuthUserEventType.UserDeleted,
            eventId = "evt-4",
            occurredAt = eventTime,
            id = "user-1",
        )
        val queryCaptor = ArgumentCaptor.forClass(Query::class.java)

        Mockito.`when`(
            template.remove(
                ArgumentMatchers.any(Query::class.java),
                ArgumentMatchers.eq(ReportUser::class.java),
            )
        ).thenReturn(Mono.just(DeleteResult.acknowledged(0L)))

        StepVerifier.create(service.sync(event))
            .expectNext(ReportUserSyncOutcome.IGNORED_STALE)
            .verifyComplete()

        Mockito.verify(template).remove(
            queryCaptor.capture(),
            ArgumentMatchers.eq(ReportUser::class.java),
        )
        val queryObject = queryCaptor.value.queryObject
        queryObject["_id"] shouldBe "user-1"
        @Suppress("UNCHECKED_CAST")
        val freshnessConditions = queryObject["\$or"] as List<Document>
        freshnessConditions shouldHaveSize 2
        val updatedAtConditions = freshnessConditions.map { it["updatedAt"] as Document }
        updatedAtConditions.mapNotNull { it["\$lte"] }.single() shouldBe eventTime
        updatedAtConditions.mapNotNull { it["\$exists"] }.single() shouldBe false
    }

    "이벤트 시각이 없으면 MongoDB 호출 전에 거부한다" {
        val template = Mockito.mock(ReactiveMongoTemplate::class.java)
        val service = ReportUserSyncService(template)
        val event = userProfileUpdatedEvent(updatedAt = Instant.parse("2026-03-20T01:00:00Z"))
            .copy(updatedAt = null, occurredAt = null)

        shouldThrow<IllegalArgumentException> {
            service.sync(event)
        }

        Mockito.verifyNoInteractions(template)
    }
}) {
    companion object {
        private fun userProfileUpdatedEvent(updatedAt: Instant) = AuthUserEvent(
            eventType = AuthUserEventType.UserProfileUpdated,
            eventId = "evt-1",
            occurredAt = updatedAt,
            id = "user-1",
            publicCode = "#FL301",
            username = "mekazon",
            role = "USER",
            nickname = "메카존",
            profileImageUrl = "https://example.com/profile.png",
            updatedAt = updatedAt,
        )
    }
}
