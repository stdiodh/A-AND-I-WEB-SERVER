package com.example.aandi_post_web_server.submission.dtos

import com.example.aandi_post_web_server.submission.enum.AssignmentSubmissionLanguage
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

@Schema(description = "과제 제출 생성 요청")
data class CreateAssignmentSubmissionRequest(
    @field:NotNull
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
    @field:Schema(description = "OJ 제출 ID", example = "a6ef9d5d-2fe3-4d4b-a41e-4483e6f0d9e2")
    val submissionId: String,
    @field:Schema(description = "OJ SSE 스트림 URL", example = "/v1/submissions/a6ef9d5d-2fe3-4d4b-a41e-4483e6f0d9e2/stream")
    val streamUrl: String,
)
