package com.example.aandi_post_web_server.assignment.v2.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "현재 사용자의 과제 제출 여부 projection 응답. 제출 횟수 집계가 아니라 채점 완료 제출 이력 존재 여부를 반환합니다.")
data class AssignmentSubmissionStatusResponse(
    @field:Schema(description = "과제 UUID", example = "7fbe8f62-9d89-4c74-b1e4-3ad3b9d7f001")
    val assignmentId: String,
    @field:Schema(description = "`true` 이면 현재 사용자 기준 채점 완료 제출 이력이 최소 1건 이상 있다는 뜻입니다. projection 이 없으면 `false` 를 반환합니다.", example = "true")
    val submitted: Boolean,
    @field:Schema(description = "projection 이 존재할 때의 최초 채점 완료 시각", nullable = true, example = "2026-04-13T08:20:11Z")
    val firstCompletedAt: Instant? = null,
    @field:Schema(description = "projection 이 존재할 때의 마지막 채점 완료 시각", nullable = true, example = "2026-04-13T08:20:11Z")
    val lastCompletedAt: Instant? = null,
    @field:Schema(description = "가장 최신 JUDGE_COMPLETED 이벤트 기준 점수", nullable = true, example = "80")
    val latestScore: Int? = null,
    @field:Schema(description = "가장 최신 JUDGE_COMPLETED 이벤트 기준 통과 케이스 수", nullable = true, example = "8")
    val passedCases: Int? = null,
    @field:Schema(description = "가장 최신 JUDGE_COMPLETED 이벤트 기준 전체 케이스 수", nullable = true, example = "10")
    val totalCases: Int? = null,
)
