package com.example.aandi_post_web_server.assignment.v2.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "현재 사용자의 과제 제출 여부 projection 응답")
data class AssignmentSubmissionStatusResponse(
    @field:Schema(description = "과제 UUID", example = "7fbe8f62-9d89-4c74-b1e4-3ad3b9d7f001")
    val assignmentId: String,
    @field:Schema(description = "채점 완료된 제출 이력이 존재하는지 여부", example = "true")
    val submitted: Boolean,
    @field:Schema(description = "최초 채점 완료 시각", nullable = true, example = "2026-04-13T08:20:11Z")
    val firstCompletedAt: Instant? = null,
    @field:Schema(description = "마지막 채점 완료 시각", nullable = true, example = "2026-04-13T08:20:11Z")
    val lastCompletedAt: Instant? = null,
    @field:Schema(description = "가장 최신 이벤트 기준 점수", nullable = true, example = "80")
    val latestScore: Int? = null,
    @field:Schema(description = "가장 최신 이벤트 기준 통과 케이스 수", nullable = true, example = "8")
    val passedCases: Int? = null,
    @field:Schema(description = "가장 최신 이벤트 기준 전체 케이스 수", nullable = true, example = "10")
    val totalCases: Int? = null,
)
