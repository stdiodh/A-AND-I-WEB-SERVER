package com.example.aandi_post_web_server.assignment.application.mapper

import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentRequirementRequest
import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentTestCaseRequest
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentRequirementDraft
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseDraft

internal fun CreateAssignmentRequirementRequest.toDraft(): AssignmentRequirementDraft =
    AssignmentRequirementDraft(
        sortOrder = sortOrder,
        requirementText = requirementText,
    )

internal fun CreateAssignmentTestCaseRequest.toDraft(): AssignmentTestCaseDraft =
    AssignmentTestCaseDraft(
        seq = seq,
        inputValues = inputValues,
        outputText = outputText,
        visibility = visibility,
    )
