package com.example.aandi_post_web_server.assignment.application.service

import com.example.aandi_post_web_server.assignment.api.dto.AssignmentMetadataPayload
import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentRequest
import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentTestCaseRequest
import com.example.aandi_post_web_server.assignment.api.dto.UpdateAssignmentRequest
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseValidator
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

@Service
class AssignmentCommandRequestResolver(
    private val assignmentTestCaseValidator: AssignmentTestCaseValidator,
) {
    fun resolveCreateRequest(request: CreateAssignmentRequest): Mono<CreateAssignmentRequest> {
        return Mono.just(validateResolvedCreateRequest(request))
    }

    fun resolveUpdateRequest(
        request: UpdateAssignmentRequest,
        replaceTestCases: Boolean,
    ): Mono<UpdateAssignmentRequest> {
        return Mono.just(validateResolvedUpdateRequest(request, replaceTestCases))
    }

    fun shouldReplaceTestCases(metadata: AssignmentMetadataPayload): Boolean {
        return metadata.testCasesProvided
    }

    private fun validateResolvedCreateRequest(request: CreateAssignmentRequest): CreateAssignmentRequest {
        if (request.metadata.title.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "과제 제목이 필요합니다.")
        }
        if (request.metadata.description.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "과제 설명이 필요합니다.")
        }
        validateTestCases(request.metadata.testCases)
        return request
    }

    private fun validateResolvedUpdateRequest(
        request: UpdateAssignmentRequest,
        replaceTestCases: Boolean,
    ): UpdateAssignmentRequest {
        val metadata = request.metadata ?: return request
        if (metadata.title != null && metadata.title.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "과제 제목이 비어 있을 수 없습니다.")
        }
        if (metadata.description != null && metadata.description.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "과제 설명이 비어 있을 수 없습니다.")
        }
        if (replaceTestCases) {
            validateTestCases(metadata.testCases)
        }
        return request
    }

    private fun validateTestCases(requests: List<CreateAssignmentTestCaseRequest>) {
        runCatching {
            assignmentTestCaseValidator.validate(requests)
        }.getOrElse { error ->
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, error.message ?: "잘못된 요청입니다.")
        }
    }
}
