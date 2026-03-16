package com.example.aandi_post_web_server.user.dtos

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import java.time.Instant

@Schema(description = "유저 동기화 요청(auth 서버 사용자를 report 서버 users 컬렉션으로 동기화)")
data class UserSyncRequest(
    @field:NotBlank
    @field:Schema(description = "동기화할 사용자의 publicCode(#이 없으면 자동으로 붙여 정규화합니다.)", example = "#AD001")
    val publicCode: String,
)

@Schema(description = "유저 동기화 응답")
data class UserSyncResponse(
    @field:Schema(description = "사용자 UUID", example = "user-uuid-1")
    val userId: String,
    @field:Schema(description = "유저 publicCode", example = "#AD001")
    val publicCode: String,
    @field:Schema(description = "사용자 이름", example = "string")
    val username: String,
    @field:Schema(description = "동기화 성공 여부", example = "true")
    val synced: Boolean,
    @field:Schema(description = "동기화 소스", example = "AUTH_SERVER")
    val source: String,
    @field:Schema(description = "동기화 시각", example = "2026-03-14T11:00:00+09:00")
    val syncedAt: Instant,
)
