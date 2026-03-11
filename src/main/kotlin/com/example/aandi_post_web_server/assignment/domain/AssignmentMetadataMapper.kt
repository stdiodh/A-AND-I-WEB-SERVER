package com.example.aandi_post_web_server.assignment.domain

import com.example.aandi_post_web_server.assignment.dtos.AssignmentCodeTemplatePayload
import com.example.aandi_post_web_server.assignment.dtos.AssignmentCodeTemplateResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentImportSourcePayload
import com.example.aandi_post_web_server.assignment.dtos.AssignmentMetadataPayload
import com.example.aandi_post_web_server.assignment.dtos.AssignmentMetadataResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentProblemClassificationPayload
import com.example.aandi_post_web_server.assignment.dtos.AssignmentProblemClassificationResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentProblemDetailPayload
import com.example.aandi_post_web_server.assignment.dtos.AssignmentProblemDetailResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentProblemSourceResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentSubmissionGuidePayload
import com.example.aandi_post_web_server.assignment.dtos.AssignmentSubmissionGuideResponse
import com.example.aandi_post_web_server.assignment.entity.AssignmentCodeTemplate
import com.example.aandi_post_web_server.assignment.entity.AssignmentMetadata
import com.example.aandi_post_web_server.assignment.entity.AssignmentProblemClassification
import com.example.aandi_post_web_server.assignment.entity.AssignmentProblemDetail
import com.example.aandi_post_web_server.assignment.entity.AssignmentProblemSource
import com.example.aandi_post_web_server.assignment.entity.AssignmentSubmissionGuide
import com.example.aandi_post_web_server.assignment.enum.AssignmentSourcePlatform

fun AssignmentMetadataPayload.source(): AssignmentImportSourcePayload? = problemDetail?.source

fun AssignmentMetadataPayload.toEntity(): AssignmentMetadata {
    val source = problemDetail?.source?.toEntity()
    return AssignmentMetadata(
        title = title?.trim().orEmpty(),
        difficulty = difficulty,
        description = description?.trim().orEmpty(),
        timeLimitMinutes = timeLimitMinutes,
        learningGoals = learningGoals.map(String::trim),
        problemDetail = problemDetail?.toEntity(source),
        submissionGuide = submissionGuide?.toEntity() ?: if (source?.platform == AssignmentSourcePlatform.BOJ) AssignmentTemplateDefaults.submissionGuide() else null,
        codeTemplates = resolveCodeTemplates(codeTemplates, problemDetail?.source),
        attributes = attributes,
    )
}

fun AssignmentMetadataPayload.withImportedContent(imported: ImportedAssignmentContent): AssignmentMetadataPayload =
    copy(
        title = title?.takeIf { it.isNotBlank() } ?: imported.title,
        description = description?.takeIf { it.isNotBlank() } ?: imported.description,
        problemDetail = (problemDetail ?: AssignmentProblemDetailPayload()).copy(
            source = problemDetail?.source,
            inputDescription = problemDetail?.inputDescription?.takeIf { it.isNotBlank() } ?: imported.inputDescription,
            outputDescription = problemDetail?.outputDescription?.takeIf { it.isNotBlank() } ?: imported.outputDescription,
            classification = problemDetail?.classification,
        ),
    )

fun AssignmentMetadata.toResponse(): AssignmentMetadataResponse = AssignmentMetadataResponse(
    title = title,
    difficulty = difficulty,
    description = description,
    timeLimitMinutes = timeLimitMinutes,
    learningGoals = learningGoals,
    problemDetail = problemDetail?.toResponse(),
    submissionGuide = submissionGuide?.toResponse(),
    codeTemplates = codeTemplates.map { it.toResponse() },
    attributes = attributes,
)

private fun AssignmentImportSourcePayload.toEntity(): AssignmentProblemSource =
    AssignmentProblemSource(
        platform = platform,
        problemId = problemId,
        url = when (platform) {
            AssignmentSourcePlatform.BOJ -> "https://www.acmicpc.net/problem/$problemId"
        },
    )

private fun AssignmentProblemDetailPayload.toEntity(source: AssignmentProblemSource?): AssignmentProblemDetail =
    AssignmentProblemDetail(
        source = source,
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

private fun resolveCodeTemplates(
    payloads: List<AssignmentCodeTemplatePayload>,
    source: AssignmentImportSourcePayload?,
): List<AssignmentCodeTemplate> {
    if (payloads.isNotEmpty()) {
        return payloads.map { it.toEntity() }
    }
    if (source?.platform == AssignmentSourcePlatform.BOJ && source.autoFillTemplates) {
        return AssignmentTemplateDefaults.codeTemplates()
    }
    return emptyList()
}

private fun AssignmentCodeTemplatePayload.toEntity(): AssignmentCodeTemplate =
    AssignmentCodeTemplate(
        language = language,
        commentTemplate = commentTemplate.trim(),
        functionTemplate = functionTemplate.trim(),
        runnableTemplate = runnableTemplate.trim(),
    )

private fun AssignmentProblemDetail.toResponse(): AssignmentProblemDetailResponse =
    AssignmentProblemDetailResponse(
        source = source?.toResponse(),
        inputDescription = inputDescription,
        outputDescription = outputDescription,
        classification = classification?.toResponse(),
    )

private fun AssignmentProblemSource.toResponse(): AssignmentProblemSourceResponse =
    AssignmentProblemSourceResponse(
        platform = platform,
        problemId = problemId,
        url = url,
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
