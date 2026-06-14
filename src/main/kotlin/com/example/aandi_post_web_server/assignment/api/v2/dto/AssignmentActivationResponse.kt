package com.example.aandi_post_web_server.assignment.api.v2.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "과제 활성화 상태 응답")
data class AssignmentActivationResponse(
    @field:Schema(description = "전역 과제 활성화 여부", example = "true")
    val active: Boolean,
    @field:Schema(description = "마지막으로 활성화 상태가 갱신된 시각", example = "2026-06-12T01:00:00Z")
    val updatedAt: Instant,
    @field:Schema(description = "마지막으로 토글한 관리자 사용자 ID. 토글 이력이 없으면 null", example = "1fd3abf7-5ea4-403f-bcf8-8b3f9d8df502")
    val updatedBy: String?,
)
