package com.example.aandi_post_web_server.assignment.domain.model

import com.example.aandi_post_web_server.assignment.api.dto.AssignmentCodeTemplatePayload
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentCodeTemplateResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentDetailMetadataResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentMetadataPayload
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentMetadataResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentRequirementResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentLearningGoalResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentTestCaseResponse
import com.example.aandi_post_web_server.assignment.entity.AssignmentCodeTemplate
import com.example.aandi_post_web_server.assignment.entity.AssignmentMetadata

fun AssignmentMetadataPayload.toEntity(): AssignmentMetadata {
    return AssignmentMetadata(
        title = title?.trim().orEmpty(),
        difficulty = difficulty,
        description = description?.trim().orEmpty(),
        timeLimitMinutes = 60,
        learningGoals = learningGoals.sortedBy { it.sortOrder }.map { it.learningGoalText.trim() },
        codeTemplates = resolveCodeTemplates(codeTemplates),
    )
}

fun AssignmentMetadata.toResponse(
    requirements: List<AssignmentRequirementResponse> = emptyList(),
    testCases: List<AssignmentTestCaseResponse> = emptyList(),
): AssignmentMetadataResponse = AssignmentMetadataResponse(
    title = title,
    difficulty = difficulty,
    description = description,
    requirements = requirements,
    learningGoals = learningGoals.mapIndexed { index, learningGoal ->
        AssignmentLearningGoalResponse(
            sortOrder = index + 1,
            learningGoalText = learningGoal,
        )
    },
    testCases = testCases,
    codeTemplates = codeTemplates.map { it.toResponse() },
)

fun AssignmentMetadata.toDetailResponse(
    requirements: List<AssignmentRequirementResponse> = emptyList(),
    testCases: List<AssignmentTestCaseResponse> = emptyList(),
): AssignmentDetailMetadataResponse = AssignmentDetailMetadataResponse(
    title = title,
    difficulty = difficulty,
    description = description,
    requirements = requirements,
    learningGoals = learningGoals.mapIndexed { index, learningGoal ->
        AssignmentLearningGoalResponse(
            sortOrder = index + 1,
            learningGoalText = learningGoal,
        )
    },
    testCases = testCases,
    codeTemplates = codeTemplates.map { it.toResponse() },
)

private fun resolveCodeTemplates(payloads: List<AssignmentCodeTemplatePayload>): List<AssignmentCodeTemplate> =
    payloads.map { it.toEntity() }

private fun AssignmentCodeTemplatePayload.toEntity(): AssignmentCodeTemplate =
    AssignmentCodeTemplate(
        language = language,
        functionTemplate = functionTemplate.trim(),
    )

private fun AssignmentCodeTemplate.toResponse(): AssignmentCodeTemplateResponse =
    AssignmentCodeTemplateResponse(
        language = language,
        functionTemplate = functionTemplate,
    )
