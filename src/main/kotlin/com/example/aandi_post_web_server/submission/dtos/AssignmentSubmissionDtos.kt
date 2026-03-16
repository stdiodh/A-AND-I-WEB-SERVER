package com.example.aandi_post_web_server.submission.dtos

import com.example.aandi_post_web_server.submission.enum.AssignmentSubmissionLanguage
import com.example.aandi_post_web_server.submission.enum.AssignmentSubmissionStatus
import com.example.aandi_post_web_server.submission.enum.AssignmentSubmissionTestCaseStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

@Schema(description = "과제 제출 생성 요청")
data class CreateAssignmentSubmissionRequest(
    @field:Schema(description = "제출 언어", example = "KOTLIN")
    val language: AssignmentSubmissionLanguage,
    @field:NotBlank
    @field:Size(max = 65_536)
    @field:Schema(description = "제출 코드", example = "fun solution(input: String): String = input")
    val code: String,
    @field:Schema(description = "실시간 채점 스트림 사용 여부", example = "true")
    val realtimeFeedback: Boolean = true,
)

@Schema(description = "과제 제출 접수 응답")
data class AssignmentSubmissionAcceptedResponse(
    @field:Schema(description = "제출 ID", example = "67d3a37e5f0c1e42c0f4a123")
    val submissionId: String,
    @field:Schema(description = "과제 UUID", example = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111")
    val assignmentId: String,
    @field:Schema(description = "현재 제출 상태", example = "PENDING")
    val status: AssignmentSubmissionStatus,
    @field:Schema(description = "실시간 채점 스트림 URL")
    val streamUrl: String,
    @field:Schema(description = "제출 결과 조회 URL")
    val resultUrl: String,
    @field:Schema(description = "제출 접수 시각(KST/Asia/Seoul)")
    val createdAt: Instant,
)

@Schema(description = "테스트 케이스 채점 결과")
data class AssignmentSubmissionTestCaseResultResponse(
    @field:Schema(description = "테스트 케이스 번호", example = "1")
    val caseId: Int,
    @field:Schema(description = "테스트 케이스 상태", example = "PASSED")
    val status: AssignmentSubmissionTestCaseStatus,
    @field:Schema(description = "실행 시간(ms)", example = "12.3")
    val timeMs: Double,
    @field:Schema(description = "메모리 사용량(MB)", example = "4.2")
    val memoryMb: Double,
    @field:Schema(description = "실행 출력", nullable = true, example = "8")
    val output: String? = null,
    @field:Schema(description = "에러 메시지", nullable = true, example = "컴파일 오류")
    val error: String? = null,
)

@Schema(description = "과제 제출 결과 응답")
data class AssignmentSubmissionResultResponse(
    @field:Schema(description = "제출 ID", example = "67d3a37e5f0c1e42c0f4a123")
    val submissionId: String,
    @field:Schema(description = "과제 UUID", example = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111")
    val assignmentId: String,
    @field:Schema(description = "제출 언어", example = "KOTLIN")
    val language: AssignmentSubmissionLanguage,
    @field:Schema(description = "현재 제출 상태", example = "ACCEPTED")
    val status: AssignmentSubmissionStatus,
    @field:Schema(description = "테스트 케이스 결과 목록")
    val testCases: List<AssignmentSubmissionTestCaseResultResponse> = emptyList(),
    @field:Schema(description = "제출 접수 시각(KST/Asia/Seoul)")
    val createdAt: Instant,
    @field:Schema(description = "채점 완료 시각(KST/Asia/Seoul)", nullable = true)
    val completedAt: Instant? = null,
)
