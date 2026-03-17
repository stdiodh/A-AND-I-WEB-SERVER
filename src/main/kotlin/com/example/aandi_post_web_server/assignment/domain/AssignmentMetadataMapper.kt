package com.example.aandi_post_web_server.assignment.domain

import com.example.aandi_post_web_server.assignment.dtos.AssignmentCodeTemplatePayload
import com.example.aandi_post_web_server.assignment.dtos.AssignmentCodeTemplateResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentDetailMetadataResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentMetadataPayload
import com.example.aandi_post_web_server.assignment.dtos.AssignmentMetadataResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentProblemClassificationPayload
import com.example.aandi_post_web_server.assignment.dtos.AssignmentProblemClassificationResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentProblemDetailPayload
import com.example.aandi_post_web_server.assignment.dtos.AssignmentProblemDetailResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentRequirementResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentExampleResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentLearningGoalResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentSubmissionGuidePayload
import com.example.aandi_post_web_server.assignment.dtos.AssignmentSubmissionGuideResponse
import com.example.aandi_post_web_server.assignment.entity.AssignmentCodeTemplate
import com.example.aandi_post_web_server.assignment.entity.AssignmentHiddenTestCase
import com.example.aandi_post_web_server.assignment.entity.AssignmentMetadata
import com.example.aandi_post_web_server.assignment.entity.AssignmentProblemClassification
import com.example.aandi_post_web_server.assignment.entity.AssignmentProblemDetail
import com.example.aandi_post_web_server.assignment.entity.AssignmentSubmissionGuide

fun AssignmentMetadataPayload.toEntity(): AssignmentMetadata {
    return AssignmentMetadata(
        title = title?.trim().orEmpty(),
        difficulty = difficulty,
        description = description?.trim().orEmpty(),
        timeLimitMinutes = 60,
        learningGoals = learningGoals.sortedBy { it.sortOrder }.map { it.learningGoalText.trim() },
        problemDetail = problemDetail?.toEntity(),
        submissionGuide = submissionGuide?.toEntity(),
        codeTemplates = resolveCodeTemplates(codeTemplates),
        hiddenTestCases = hiddenTestCases.sortedBy { it.seq }.map { it.toHiddenTestCase() },
        attributes = attributes,
    )
}

fun AssignmentMetadata.toResponse(
    requirements: List<AssignmentRequirementResponse> = emptyList(),
    examples: List<AssignmentExampleResponse> = emptyList(),
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
    examples = examples,
    problemDetail = problemDetail?.toResponse(),
    submissionGuide = submissionGuide?.toResponse(),
    codeTemplates = codeTemplates.map { it.toResponse() },
    attributes = attributes,
)

fun AssignmentMetadata.toDetailResponse(
    requirements: List<AssignmentRequirementResponse> = emptyList(),
    examples: List<AssignmentExampleResponse> = emptyList(),
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
    examples = examples,
    problemDetail = problemDetail?.toResponse(),
    attributes = attributes,
)

private fun AssignmentProblemDetailPayload.toEntity(): AssignmentProblemDetail =
    AssignmentProblemDetail(
        inputDescription = inputDescription?.trim(),
        outputDescription = outputDescription?.trim(),
        classification = classification?.toEntity(),
    )

private fun AssignmentProblemClassificationPayload.toEntity(): AssignmentProblemClassification =
    AssignmentProblemClassification(
        algorithmStep = algorithmStep,
        difficultyStep = difficultyStep,
    )

private fun AssignmentSubmissionGuidePayload.toEntity(): AssignmentSubmissionGuide =
    AssignmentSubmissionGuide(
        title = title.trim(),
        description = description.trim(),
        commentSections = commentSections.map(String::trim),
    )

private fun resolveCodeTemplates(payloads: List<AssignmentCodeTemplatePayload>): List<AssignmentCodeTemplate> =
    payloads.map { it.toEntity() }

private fun AssignmentCodeTemplatePayload.toEntity(): AssignmentCodeTemplate =
    AssignmentCodeTemplate(
        language = language,
        commentTemplate = commentTemplate.trim(),
        functionTemplate = functionTemplate.trim(),
        runnableTemplate = runnableTemplate.trim(),
    )

private fun com.example.aandi_post_web_server.assignment.dtos.CreateAssignmentExampleRequest.toHiddenTestCase(): AssignmentHiddenTestCase =
    AssignmentHiddenTestCase(
        seq = seq,
        inputText = inputText.trim(),
        outputText = outputText.trim(),
    )

private fun AssignmentProblemDetail.toResponse(): AssignmentProblemDetailResponse =
    AssignmentProblemDetailResponse(
        inputDescription = inputDescription,
        outputDescription = outputDescription,
        classification = classification?.toResponse(),
    )

private fun AssignmentProblemClassification.toResponse(): AssignmentProblemClassificationResponse =
    AssignmentProblemClassificationResponse(
        algorithmStep = algorithmStep,
        difficultyStep = difficultyStep,
    )

private fun AssignmentSubmissionGuide.toResponse(): AssignmentSubmissionGuideResponse =
    AssignmentSubmissionGuideResponse(
        title = title,
        description = description,
        commentSections = commentSections,
    )

private fun AssignmentCodeTemplate.toResponse(): AssignmentCodeTemplateResponse =
    AssignmentCodeTemplateResponse(
        language = language,
        commentTemplate = commentTemplate,
        functionTemplate = functionTemplate,
        runnableTemplate = runnableTemplate,
    )
