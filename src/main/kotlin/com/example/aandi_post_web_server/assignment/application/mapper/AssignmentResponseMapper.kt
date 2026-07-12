package com.example.aandi_post_web_server.assignment.application.mapper

import com.example.aandi_post_web_server.assignment.api.dto.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentRequirementResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentSummaryResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentTestCaseResponse
import com.example.aandi_post_web_server.assignment.domain.model.EffectiveAssignmentPublication
import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.entity.AssignmentRequirement
import com.example.aandi_post_web_server.assignment.entity.AssignmentTestCase

internal fun AssignmentRequirement.toResponse(): AssignmentRequirementResponse =
    AssignmentRequirementResponse(
        sortOrder = sortOrder,
        requirementText = requirementText,
    )

internal fun AssignmentTestCase.toResponse(): AssignmentTestCaseResponse =
    AssignmentTestCaseResponse(
        seq = seq,
        inputValues = inputValues,
        outputText = outputText,
        visibility = visibility,
    )

internal fun Assignment.toSummaryResponse(
    publication: EffectiveAssignmentPublication,
    requirements: List<AssignmentRequirementResponse>,
    testCases: List<AssignmentTestCaseResponse>,
): AssignmentSummaryResponse = AssignmentSummaryResponse(
    id = requireNotNull(id),
    weekNo = weekNo,
    orderInWeek = orderInWeek,
    startAt = startAt,
    endAt = endAt,
    status = publication.status,
    publishedAt = publication.publishedAt,
    metadata = metadata.toResponse(requirements, testCases),
)

internal fun Assignment.toDetailResponse(
    courseSlug: String,
    publication: EffectiveAssignmentPublication,
    requirements: List<AssignmentRequirementResponse>,
    testCases: List<AssignmentTestCaseResponse>,
): AssignmentDetailResponse = AssignmentDetailResponse(
    id = requireNotNull(id),
    courseSlug = courseSlug,
    weekNo = weekNo,
    orderInWeek = orderInWeek,
    startAt = startAt,
    endAt = endAt,
    status = publication.status,
    publishedAt = publication.publishedAt,
    metadata = metadata.toDetailResponse(requirements, testCases),
)
