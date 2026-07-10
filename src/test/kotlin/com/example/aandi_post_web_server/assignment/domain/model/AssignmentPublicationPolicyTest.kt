package com.example.aandi_post_web_server.assignment.domain.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AssignmentPublicationPolicyTest : StringSpec({
    val now = Instant.parse("2026-07-10T00:00:00Z")
    val policy = AssignmentPublicationPolicy(Clock.fixed(now, ZoneOffset.UTC))

    "공개 시작 전 PUBLISHED 과제는 응답에서 DRAFT로 해석한다" {
        policy.resolve(
            status = AssignmentStatus.PUBLISHED,
            startAt = now.plusSeconds(1),
            publishedAt = now.plusSeconds(1),
        ) shouldBe EffectiveAssignmentPublication(
            status = AssignmentStatus.DRAFT,
            publishedAt = null,
        )
    }

    "공개 시작 시각부터 PUBLISHED 과제로 해석한다" {
        policy.resolve(
            status = AssignmentStatus.PUBLISHED,
            startAt = now,
            publishedAt = null,
        ) shouldBe EffectiveAssignmentPublication(
            status = AssignmentStatus.PUBLISHED,
            publishedAt = now,
        )
    }

    "명시된 공개 시각은 유효 응답에 유지한다" {
        val publishedAt = now.minusSeconds(3600)

        policy.resolve(
            status = AssignmentStatus.PUBLISHED,
            startAt = now.minusSeconds(7200),
            publishedAt = publishedAt,
        ) shouldBe EffectiveAssignmentPublication(
            status = AssignmentStatus.PUBLISHED,
            publishedAt = publishedAt,
        )
    }

    "DRAFT 과제는 공개 시각을 노출하지 않는다" {
        policy.resolve(
            status = AssignmentStatus.DRAFT,
            startAt = now.minusSeconds(7200),
            publishedAt = now.minusSeconds(3600),
        ) shouldBe EffectiveAssignmentPublication(
            status = AssignmentStatus.DRAFT,
            publishedAt = null,
        )
    }

    "초기 공개 시각은 예약 공개면 시작 시각, 즉시 공개면 현재 시각이다" {
        policy.initialPublishedAt(now.plusSeconds(1)) shouldBe now.plusSeconds(1)
        policy.initialPublishedAt(now.minusSeconds(1)) shouldBe now
    }
})
