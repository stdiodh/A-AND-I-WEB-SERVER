package com.example.aandi_post_web_server.assignment.api.v2.controller

import com.example.aandi_post_web_server.assignment.api.v2.dto.AssignmentActivationResponse
import com.example.aandi_post_web_server.assignment.api.v2.dto.UpdateAssignmentActivationRequest
import com.example.aandi_post_web_server.assignment.application.activation.AssignmentActivationService
import com.example.aandi_post_web_server.common.api.envelope.V2ApiEnvelope
import com.example.aandi_post_web_server.common.api.factory.V2ApiResponseFactory
import com.example.aandi_post_web_server.common.openapi.V2AssignmentActivationEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.V2ErrorEnvelopeDoc
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@Tag(
    name = "과제 활성화 관리자 v2 API",
    description = "전역 과제 활성화 상태를 토글하는 관리자 전용 API입니다. ADMIN 권한이 필요하며 비활성화 시 USER/ORGANIZER 호출은 503 `ASSIGNMENT_DEACTIVATED` 로 차단됩니다.",
)
@SecurityRequirement(name = "v2Authenticate")
@RestController
@RequestMapping("/v2/admin/assignments/activation")
class AdminAssignmentActivationV2Controller(
    private val activationService: AssignmentActivationService,
) {

    @Operation(
        summary = "과제 활성화 상태 조회",
        description = "전역 과제 활성화 여부와 마지막 변경 시각/사용자 정보를 반환합니다. 도큐먼트가 없으면 기본 active=true 로 응답합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = V2AssignmentActivationEnvelopeDoc::class))]),
            ApiResponse(responseCode = "401", description = "토큰 없음 또는 인증 실패", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 없음", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
        ],
    )
    @GetMapping
    fun getActivation(): Mono<V2ApiEnvelope<AssignmentActivationResponse>> =
        activationService.getActivation().map(V2ApiResponseFactory::success)

    @Operation(
        summary = "과제 활성화 상태 변경",
        description = "전역 과제 활성화 여부를 갱신합니다. false 로 설정하면 USER/ORGANIZER 권한으로 호출되는 모든 과제 API 가 503 `ASSIGNMENT_DEACTIVATED` 로 차단되며, ADMIN 호출은 그대로 동작합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "변경 성공", content = [Content(schema = Schema(implementation = V2AssignmentActivationEnvelopeDoc::class))]),
            ApiResponse(responseCode = "400", description = "요청값 오류 (active 누락)", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "401", description = "토큰 없음 또는 인증 실패", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 없음", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
        ],
    )
    @PutMapping
    fun setActivation(
        @Valid @RequestBody request: UpdateAssignmentActivationRequest,
        authentication: Authentication,
    ): Mono<V2ApiEnvelope<AssignmentActivationResponse>> =
        activationService.setActivation(
            active = request.active ?: error("active must not be null after validation"),
            updatedBy = authentication.name,
        ).map(V2ApiResponseFactory::success)
}
