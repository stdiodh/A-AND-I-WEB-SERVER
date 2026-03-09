package com.example.aandi_post_web_server.assignment.dtos
import com.example.aandi_post_web_server.assignment.enum.AssignmentDeliveryStatus
import com.example.aandi_post_web_server.assignment.enum.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.Instant

@Schema(description = "과제 메타데이터")
data class AssignmentMetadataPayload(
    @field:NotBlank
    @field:Schema(description = "과제 제목", example = "터미널 계산기")
    val title: String,
    @field:Schema(description = "난이도", example = "MID")
    val difficulty: AssignmentDifficulty,
    @field:NotBlank
    @field:Schema(description = "과제 설명", example = "# 문제 설명")
    val description: String,
    @field:Min(1)
    @field:Schema(description = "제한 시간(분)", example = "60")
    val timeLimitMinutes: Int = 60,
    @field:Schema(description = "학습 목표")
    val learningGoals: List<String> = emptyList(),
    @field:Schema(description = "확장 메타데이터")
    val attributes: Map<String, Any?> = emptyMap(),
)

@Schema(description = "과제 요구사항 생성 요청")
data class CreateAssignmentRequirementRequest(
    @field:Min(1)
    @field:Schema(description = "요구사항 정렬 순서", example = "1")
    val sortOrder: Int,
    @field:NotBlank
    @field:Schema(description = "요구사항 내용", example = "함수 분리 필수")
    val requirementText: String,
)

@Schema(description = "과제 예시 입출력 생성 요청")
data class CreateAssignmentExampleRequest(
    @field:Min(1)
    @field:Schema(description = "예시 순번", example = "1")
    val seq: Int,
    @field:NotBlank
    @field:Schema(description = "입력 예시", example = "ADD 1\\nCLOSE")
    val inputText: String,
    @field:NotBlank
    @field:Schema(description = "출력 예시", example = "+1")
    val outputText: String,
    @field:Schema(description = "예시 설명", example = "기본 동작")
    val description: String? = null,
)

@Schema(description = "과제 생성 요청")
data class CreateAssignmentRequest(
    @field:Min(1)
    @field:Schema(description = "주차 번호", example = "1")
    val weekNo: Int,
    @field:Min(1)
    @field:Schema(description = "주차 내 순서", example = "1")
    val orderInWeek: Int,
    @field:Schema(description = "시작 시각(KST(Asia/Seoul))", example = "2026-03-03T09:00:00+09:00")
    val startAt: Instant,
    @field:Schema(description = "종료 시각(KST(Asia/Seoul))", example = "2026-03-11T08:59:59+09:00")
    val endAt: Instant,
    @field:Schema(description = "과제 메타데이터")
    val metadata: AssignmentMetadataPayload,
    @field:Schema(description = "요구사항 목록")
    val requirements: List<CreateAssignmentRequirementRequest> = emptyList(),
    @field:Schema(description = "예시 입출력 목록")
    val examples: List<CreateAssignmentExampleRequest> = emptyList(),
)

@Schema(description = "과제 수정 요청")
data class UpdateAssignmentRequest(
    @field:Min(1)
    @field:Schema(description = "주차 번호(옵션)", example = "1")
    val weekNo: Int? = null,
    @field:Min(1)
    @field:Schema(description = "주차 내 순서(옵션)", example = "1")
    val orderInWeek: Int? = null,
    @field:Schema(description = "시작 시각(옵션, KST(Asia/Seoul))", example = "2026-03-03T09:00:00+09:00")
    val startAt: Instant? = null,
    @field:Schema(description = "종료 시각(옵션, KST(Asia/Seoul))", example = "2026-03-11T08:59:59+09:00")
    val endAt: Instant? = null,
    @field:Schema(description = "과제 메타데이터(전체 교체, 옵션)")
    val metadata: AssignmentMetadataPayload? = null,
    @field:Schema(description = "요구사항 목록(전체 교체, 옵션)")
    val requirements: List<CreateAssignmentRequirementRequest>? = null,
    @field:Schema(description = "예시 입출력 목록(전체 교체, 옵션)")
    val examples: List<CreateAssignmentExampleRequest>? = null,
)

@Schema(description = "과제 메타데이터 응답")
data class AssignmentMetadataResponse(
    @field:Schema(description = "과제 제목", example = "터미널 계산기")
    val title: String,
    @field:Schema(description = "난이도", example = "MID")
    val difficulty: AssignmentDifficulty,
    @field:Schema(description = "과제 설명")
    val description: String,
    @field:Schema(description = "제한 시간(분)", example = "60")
    val timeLimitMinutes: Int,
    @field:Schema(description = "학습 목표")
    val learningGoals: List<String>,
    @field:Schema(description = "확장 메타데이터")
    val attributes: Map<String, Any?>,
)

@Schema(description = "과제 요구사항 응답")
data class AssignmentRequirementResponse(
    @field:Schema(description = "정렬 순서", example = "1")
    val sortOrder: Int,
    @field:Schema(description = "요구사항 내용", example = "함수 분리 필수")
    val requirementText: String,
)

@Schema(description = "과제 예시 입출력 응답")
data class AssignmentExampleResponse(
    @field:Schema(description = "예시 순번", example = "1")
    val seq: Int,
    @field:Schema(description = "입력 예시", example = "ADD 1\\nCLOSE")
    val inputText: String,
    @field:Schema(description = "출력 예시", example = "+1")
    val outputText: String,
    @field:Schema(description = "예시 설명")
    val description: String?,
)

@Schema(description = "과제 요약 응답")
data class AssignmentSummaryResponse(
    @field:Schema(description = "과제 ID", example = "assignment-1")
    val id: String,
    @field:Schema(description = "주차 번호", example = "1")
    val weekNo: Int,
    @field:Schema(description = "주차 내 순서", example = "1")
    val orderInWeek: Int,
    @field:Schema(description = "시작 시각(KST(Asia/Seoul))")
    val startAt: Instant,
    @field:Schema(description = "종료 시각(KST(Asia/Seoul))")
    val endAt: Instant,
    @field:Schema(description = "과제 상태", example = "PUBLISHED")
    val status: AssignmentStatus,
    @field:Schema(description = "과제 메타데이터")
    val metadata: AssignmentMetadataResponse,
)

@Schema(description = "과제 상세 응답")
data class AssignmentDetailResponse(
    @field:Schema(description = "과제 ID", example = "assignment-1")
    val id: String,
    @field:Schema(description = "코스 슬러그", example = "back-basic")
    val courseSlug: String,
    @field:Schema(description = "주차 번호", example = "1")
    val weekNo: Int,
    @field:Schema(description = "주차 내 순서", example = "1")
    val orderInWeek: Int,
    @field:Schema(description = "시작 시각(KST(Asia/Seoul))")
    val startAt: Instant,
    @field:Schema(description = "종료 시각(KST(Asia/Seoul))")
    val endAt: Instant,
    @field:Schema(description = "과제 상태", example = "DRAFT")
    val status: AssignmentStatus,
    @field:Schema(description = "게시 시각(KST(Asia/Seoul))")
    val publishedAt: Instant?,
    @field:Schema(description = "과제 메타데이터")
    val metadata: AssignmentMetadataResponse,
    @field:Schema(description = "요구사항 목록")
    val requirements: List<AssignmentRequirementResponse>,
    @field:Schema(description = "예시 입출력 목록")
    val examples: List<AssignmentExampleResponse>,
)

@Schema(description = "과제 게시 응답")
data class PublishAssignmentResponse(
    @field:Schema(description = "과제 ID", example = "assignment-1")
    val assignmentId: String,
    @field:Schema(description = "코스 슬러그", example = "back-basic")
    val courseSlug: String,
    @field:Schema(description = "게시 후 상태", example = "PUBLISHED")
    val status: AssignmentStatus,
    @field:Schema(description = "게시 시각(KST(Asia/Seoul))")
    val publishedAt: Instant?,
)

@Schema(description = "과제 배포 트리거 응답")
data class TriggerDeliveriesResponse(
    @field:Schema(description = "과제 ID", example = "assignment-1")
    val assignmentId: String,
    @field:Schema(description = "코스 슬러그", example = "back-basic")
    val courseSlug: String,
    @field:Schema(description = "배포 대상 수", example = "42")
    val targetCount: Int,
    @field:Schema(description = "배포 성공 수", example = "40")
    val deliveredCount: Int,
    @field:Schema(description = "배포 실패 수", example = "2")
    val failedCount: Int,
)

@Schema(description = "배포 결과 응답")
data class AssignmentDeliveryResponse(
    @field:Schema(description = "유저 ID", example = "user-1")
    val userId: String,
    @field:Schema(description = "배포 상태", example = "DELIVERED")
    val status: AssignmentDeliveryStatus,
    @field:Schema(description = "배포 시각(KST(Asia/Seoul))")
    val deliveredAt: Instant?,
    @field:Schema(description = "실패 사유")
    val failureReason: String?,
)
