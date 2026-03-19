package com.example.aandi_post_web_server.user.service

import com.example.aandi_post_web_server.user.entity.ReportUser
import com.example.aandi_post_web_server.user.event.AuthUserEvent
import com.example.aandi_post_web_server.user.event.AuthUserEventType
import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
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
