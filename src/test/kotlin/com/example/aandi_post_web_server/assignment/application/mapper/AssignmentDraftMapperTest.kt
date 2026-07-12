package com.example.aandi_post_web_server.assignment.application.mapper

import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentRequirementRequest
import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentTestCaseRequest
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentRequirementDraft
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseDraft
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseVisibility
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AssignmentDraftMapperTest : StringSpec({
    "requirement request의 모든 필드를 draft로 옮긴다" {
        val request = CreateAssignmentRequirementRequest(
            sortOrder = 2,
            requirementText = "예외 처리",
        )

        request.toDraft() shouldBe AssignmentRequirementDraft(
            sortOrder = 2,
            requirementText = "예외 처리",
        )
    }

    "test case request의 모든 필드를 draft로 옮긴다" {
        val request = CreateAssignmentTestCaseRequest(
            seq = 3,
            inputValues = listOf("ADD 1", "CLOSE"),
            outputText = "+1",
            visibility = AssignmentTestCaseVisibility.HIDDEN,
        )

        request.toDraft() shouldBe AssignmentTestCaseDraft(
            seq = 3,
            inputValues = listOf("ADD 1", "CLOSE"),
            outputText = "+1",
            visibility = AssignmentTestCaseVisibility.HIDDEN,
        )
    }
})
