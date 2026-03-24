package com.example.aandi_post_web_server.assignment.domain

import com.example.aandi_post_web_server.assignment.dtos.CreateAssignmentExampleRequest
import com.example.aandi_post_web_server.assignment.dtos.CreateAssignmentRequirementRequest
import com.example.aandi_post_web_server.assignment.entity.AssignmentDelivery
import com.example.aandi_post_web_server.assignment.enum.AssignmentTestCaseVisibility
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.Instant

class AssignmentDraftCollectionsTest : StringSpec({
    "AssignmentRequirementDrafts는 요청을 엔티티로 변환한다" {
        val drafts = AssignmentRequirementDrafts.fromRequests(
            listOf(
                CreateAssignmentRequirementRequest(sortOrder = 1, requirementText = "함수 분리"),
                CreateAssignmentRequirementRequest(sortOrder = 2, requirementText = "예외 처리"),
            )
        )
        val now = Instant.parse("2026-03-01T00:00:00Z")

        val entities = drafts.toEntities("assignment-1", now)

        entities.size shouldBe 2
        entities.first().assignmentId shouldBe "assignment-1"
        entities.first().sortOrder shouldBe 1
        entities.first().createdAt shouldBe now
    }

    "AssignmentExampleDrafts는 seq 중복을 거부한다" {
        shouldThrow<IllegalArgumentException> {
            AssignmentExampleDrafts.fromRequests(
                listOf(
                    CreateAssignmentExampleRequest(seq = 1, inputValues = listOf("1"), outputText = "1"),
                    CreateAssignmentExampleRequest(seq = 1, inputValues = listOf("2"), outputText = "2"),
                )
            )
        }
    }

    "AssignmentExampleDrafts는 요청을 엔티티로 변환한다" {
        val drafts = AssignmentExampleDrafts.fromRequests(
            listOf(
                CreateAssignmentExampleRequest(seq = 1, inputValues = listOf("ADD 1"), outputText = "+1"),
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

    "DeliveredAssignmentIds는 assignmentId를 중복 제거해 보관한다" {
        val ids = DeliveredAssignmentIds.fromDeliveries(
            listOf(
                AssignmentDelivery(assignmentId = "a-1", userId = "u-1"),
                AssignmentDelivery(assignmentId = "a-1", userId = "u-2"),
                AssignmentDelivery(assignmentId = "a-2", userId = "u-3"),
            )
        )

        ids.isEmpty() shouldBe false
        ids.asCollection().toList().sorted() shouldContainExactly listOf("a-1", "a-2")
    }
})
