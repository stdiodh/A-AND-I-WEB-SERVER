package com.example.aandi_post_web_server.user.controller

import com.example.aandi_post_web_server.common.openapi.ApiEnvelope
import com.example.aandi_post_web_server.common.openapi.ErrorEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.UserSyncEnvelopeDoc
import com.example.aandi_post_web_server.user.dtos.UserSyncRequest
import com.example.aandi_post_web_server.user.dtos.UserSyncResponse
import com.example.aandi_post_web_server.user.service.AdminUserSyncService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@Tag(name = "유저 관리자 API", description = "관리자 전용 유저 동기화 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/v1/admin/users")
class AdminUserController(
    private val adminUserSyncService: AdminUserSyncService,
) {
    @Operation(summary = "유저 동기화", description = "publicCode로 auth 서버 사용자를 찾아 report 서버 사용자 정보로 동기화합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "동기화 성공", content = [Content(schema = Schema(implementation = UserSyncEnvelopeDoc::class))]),
            ApiResponse(responseCode = "400", description = "잘못된 publicCode", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "auth 서버 사용자를 찾을 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
        ],
    )
    @PostMapping("/sync")
    fun syncUser(
        @Valid @RequestBody request: UserSyncRequest,
    ): Mono<ApiEnvelope<UserSyncResponse>> =
        adminUserSyncService.syncByPublicCode(request).map { ApiEnvelope.success(it) }
}
