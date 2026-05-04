package com.example.aandi_post_web_server.assignment.api.dto

import com.example.aandi_post_web_server.assignment.domain.model.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseVisibility
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "과제 메타데이터 응답")
data class AssignmentMetadataResponse(
    @field:Schema(description = "과제 제목", example = "터미널 계산기")
    val title: String,
    @field:Schema(description = "난이도", example = "MID")
    val difficulty: AssignmentDifficulty,
    @field:Schema(description = "과제 설명")
    val description: String,
    @field:Schema(description = "문제 요구 사항")
    val requirements: List<AssignmentRequirementResponse> = emptyList(),
    @field:Schema(description = "학습 목표")
    val learningGoals: List<AssignmentLearningGoalResponse> = emptyList(),
    @field:Schema(description = "테스트 케이스")
    val testCases: List<AssignmentTestCaseResponse> = emptyList(),
    @field:Schema(description = "언어별 코드 템플릿")
    val codeTemplates: List<AssignmentCodeTemplateResponse> = emptyList(),
) {
    @get:JsonIgnore
    val examples: List<AssignmentTestCaseResponse>
        get() = testCases
}

@Schema(description = "과제 상세 메타데이터 응답")
data class AssignmentDetailMetadataResponse(
    @field:Schema(description = "과제 제목", example = "터미널 계산기")
    val title: String,
    @field:Schema(description = "난이도", example = "MID")
    val difficulty: AssignmentDifficulty,
    @field:Schema(description = "과제 설명")
    val description: String,
    @field:Schema(description = "문제 요구 사항")
    val requirements: List<AssignmentRequirementResponse> = emptyList(),
    @field:Schema(description = "학습 목표")
    val learningGoals: List<AssignmentLearningGoalResponse> = emptyList(),
    @field:Schema(description = "테스트 케이스")
    val testCases: List<AssignmentTestCaseResponse> = emptyList(),
    @field:Schema(description = "언어별 코드 템플릿")
    val codeTemplates: List<AssignmentCodeTemplateResponse> = emptyList(),
) {
    @get:JsonIgnore
    val examples: List<AssignmentTestCaseResponse>
        get() = testCases
}

@Schema(description = "과제 요구사항 응답")
data class AssignmentRequirementResponse(
    @field:Schema(description = "정렬 순서", example = "1")
    val sortOrder: Int,
    @field:Schema(description = "요구사항 내용", example = "함수 분리 필수")
    val requirementText: String,
)

@Schema(description = "과제 학습 목표 응답")
data class AssignmentLearningGoalResponse(
    @field:Schema(description = "정렬 순서", example = "1")
    val sortOrder: Int,
    @field:Schema(description = "학습 목표 내용", example = "함수 분리")
    val learningGoalText: String,
)

@Schema(description = "과제 테스트 케이스 응답")
data class AssignmentTestCaseResponse(
    @field:Schema(description = "테스트 케이스 순번", example = "1")
    val seq: Int,
    @field:Schema(description = "입력 인자 목록", example = """["ADD 1", "CLOSE"]""")
    val inputValues: List<String>,
    @field:Schema(description = "출력 예시", example = "+1")
    val outputText: String,
    @field:Schema(description = "공개 여부", example = "PUBLIC")
    val visibility: AssignmentTestCaseVisibility = AssignmentTestCaseVisibility.PUBLIC,
)

@Schema(description = "과제 요약 응답")
data class AssignmentSummaryResponse(
    @field:Schema(description = "과제 UUID", example = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111")
    @get:JsonProperty("assignmentId")
    val id: String,
    @field:Schema(description = "주차 번호", example = "1")
    val weekNo: Int,
    @field:Schema(description = "주차 내 순서", example = "1")
    val orderInWeek: Int,
    @field:Schema(description = "공개 시작 시각(KST/Asia/Seoul)")
    val startAt: Instant,
    @field:Schema(description = "마감 시각(KST/Asia/Seoul)")
    val endAt: Instant,
    @field:Schema(description = "과제 상태(startAt이 지나면 사용자에게는 PUBLISHED로 보임)", example = "PUBLISHED")
    val status: AssignmentStatus,
    @field:Schema(description = "과제 메타데이터")
    val metadata: AssignmentMetadataResponse,
)

@Schema(description = "과제 상세 응답")
data class AssignmentDetailResponse(
    @field:Schema(description = "과제 UUID", example = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111")
    @get:JsonProperty("assignmentId")
    val id: String,
    @field:Schema(description = "코스를 구분하는 슬러그", example = "back-basic")
    val courseSlug: String,
    @field:Schema(description = "주차 번호", example = "1")
    val weekNo: Int,
    @field:Schema(description = "주차 내 순서", example = "1")
    val orderInWeek: Int,
    @field:Schema(description = "공개 시작 시각(KST/Asia/Seoul)")
    val startAt: Instant,
    @field:Schema(description = "마감 시각(KST/Asia/Seoul)")
    val endAt: Instant,
    @field:Schema(description = "과제 상태(startAt이 지나면 사용자에게는 PUBLISHED로 보임)", example = "DRAFT")
    val status: AssignmentStatus,
    @field:Schema(description = "사용자에게 공개된 시각(KST/Asia/Seoul)")
    val publishedAt: Instant?,
    @field:Schema(description = "과제 메타데이터")
    val metadata: AssignmentDetailMetadataResponse,
)
