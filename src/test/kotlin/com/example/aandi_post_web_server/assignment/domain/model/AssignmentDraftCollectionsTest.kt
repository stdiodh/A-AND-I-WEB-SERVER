package com.example.aandi_post_web_server.assignment.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class AssignmentDraftCollectionsTest : StringSpec({
    "AssignmentRequirementDrafts는 draft를 엔티티로 변환한다" {
        val drafts = AssignmentRequirementDrafts.from(
            listOf(
                AssignmentRequirementDraft(sortOrder = 1, requirementText = "함수 분리"),
                AssignmentRequirementDraft(sortOrder = 2, requirementText = "예외 처리"),
            )
        )
        val now = Instant.parse("2026-03-01T00:00:00Z")

        val entities = drafts.toEntities("assignment-1", now)

        entities.size shouldBe 2
        entities.first().assignmentId shouldBe "assignment-1"
        entities.first().sortOrder shouldBe 1
        entities.first().createdAt shouldBe now
    }

    "AssignmentTestCaseDrafts는 seq 중복을 거부한다" {
        shouldThrow<IllegalArgumentException> {
            AssignmentTestCaseDrafts.from(
                listOf(
                    AssignmentTestCaseDraft(1, listOf("1"), "1", AssignmentTestCaseVisibility.PUBLIC),
                    AssignmentTestCaseDraft(1, listOf("2"), "2", AssignmentTestCaseVisibility.PUBLIC),
                )
            )
        }
    }

    "AssignmentTestCaseDrafts는 draft를 엔티티로 변환한다" {
        val drafts = AssignmentTestCaseDrafts.from(
            listOf(
                AssignmentTestCaseDraft(1, listOf("ADD 1"), "+1", AssignmentTestCaseVisibility.PUBLIC),
            )
        )
        val now = Instant.parse("2026-03-01T00:00:00Z")

        val entities = drafts.toEntities("assignment-1", now)

        entities.size shouldBe 1
        entities.first().seq shouldBe 1
        entities.first().visibility shouldBe AssignmentTestCaseVisibility.PUBLIC
        entities.first().description shouldBe null
        entities.first().createdAt shouldBe now
    }
})
