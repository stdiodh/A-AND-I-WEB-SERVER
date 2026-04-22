package com.example.aandi_post_web_server.assignment.api.v2.dto

import com.example.aandi_post_web_server.course.domain.model.EnrollmentStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "관리자용 과제 제출 현황 목록 응답")
data class AdminAssignmentSubmissionStatusesResponse(
    @field:Schema(description = "과제 UUID", example = "7fbe8f62-9d89-4c74-b1e4-3ad3b9d7f001")
    val assignmentId: String,
    @field:Schema(description = "코스를 구분하는 슬러그", example = "back-basic")
    val courseSlug: String,
    @field:Schema(description = "코스에 등록된 수강생 수", example = "30")
    val totalEnrolled: Int,
    @field:Schema(description = "projection 이 존재하는 제출 완료 수강생 수", example = "18")
    val submittedCount: Int,
    @field:Schema(description = "projection 이 없어 미제출로 간주되는 수강생 수", example = "12")
    val notSubmittedCount: Int,
    @field:Schema(description = "수강생별 제출 현황 목록")
    val items: List<AdminAssignmentSubmissionStatusItemResponse>,
)

@Schema(description = "관리자용 수강생별 과제 제출 현황")
data class AdminAssignmentSubmissionStatusItemResponse(
    @field:Schema(description = "사용자 UUID", example = "user-1")
    val userId: String,
    @field:Schema(description = "유저 publicCode", example = "#BE301")
    val publicCode: String,
    @field:Schema(description = "사용자 닉네임. 닉네임이 없으면 username 이 반환됩니다.", example = "메카존")
    val username: String,
    @field:Schema(description = "수강 상태", example = "ENABLED")
    val enrollmentStatus: EnrollmentStatus,
    @field:Schema(description = "현재 기준 채점 완료 projection 존재 여부", example = "true")
    val submitted: Boolean,
    @field:Schema(description = "OJ가 발행한 최고 점수 기준 결과 점수. projection 이 없으면 null.", example = "90")
    val score: Int? = null,
    @field:Schema(description = "OJ가 발행한 최고 점수 기준 결과의 통과 케이스 수. projection 이 없으면 null.", example = "9")
    val passedCases: Int? = null,
    @field:Schema(description = "OJ가 발행한 최고 점수 기준 결과의 전체 케이스 수. projection 이 없으면 null.", example = "10")
    val totalCases: Int? = null,
    @field:Schema(description = "최고 점수 기준 결과가 기록된 `JUDGE_COMPLETED` 이벤트 시각(UTC ISO-8601). projection 이 없으면 null.")
    val completedAt: Instant? = null,
)
