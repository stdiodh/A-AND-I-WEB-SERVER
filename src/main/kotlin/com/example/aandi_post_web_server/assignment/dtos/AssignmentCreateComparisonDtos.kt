package com.example.aandi_post_web_server.assignment.dtos

import com.example.aandi_post_web_server.assignment.enum.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.enum.AssignmentTestCaseVisibility
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "과제 생성 비교 응답")
data class AssignmentCreateComparisonResponse(
    @field:Schema(description = "비교 기준 원본 과제. 신규 생성만 비교할 때는 null 일 수 있습니다.")
    val source: AssignmentCreateComparisonItemResponse? = null,
    @field:Schema(description = "생성될 과제 초안")
    val generated: AssignmentCreateComparisonItemResponse,
)

@Schema(description = "과제 생성 비교용 스냅샷")
data class AssignmentCreateComparisonItemResponse(
    @field:Schema(description = "과제 UUID", example = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111")
    val assignmentId: String? = null,
    @field:Schema(description = "주차 번호", example = "1")
    val weekNo: Int,
    @field:Schema(description = "주차 내 순서", example = "1")
    val orderInWeek: Int,
    @field:Schema(description = "시작 시각(KST(Asia/Seoul))")
    val startAt: Instant,
    @field:Schema(description = "종료 시각(KST(Asia/Seoul))")
    val endAt: Instant,
    @field:Schema(description = "과제 본문 메타데이터")
    val metadata: AssignmentCreateComparisonMetadataResponse,
)

@Schema(description = "과제 생성 비교용 메타데이터")
data class AssignmentCreateComparisonMetadataResponse(
    @field:Schema(description = "과제 제목", example = "터미널 계산기")
    val title: String,
    @field:Schema(description = "난이도", example = "MID")
    val difficulty: AssignmentDifficulty? = null,
    @field:Schema(description = "문제 설명", example = "# 문제 설명")
    val problemDescription: String,
    @field:Schema(description = "문제 요구 사항")
    val requirements: List<AssignmentRequirementResponse> = emptyList(),
    @field:Schema(description = "학습 정리 목표")
    val learningGoals: List<AssignmentLearningGoalResponse> = emptyList(),
    @field:Schema(description = "테스트 케이스")
    val testCases: List<AssignmentCreateComparisonTestCaseResponse> = emptyList(),
    @field:Schema(description = "문제 상세 정보")
    val problemDetail: AssignmentProblemDetailResponse? = null,
    @field:Schema(description = "제출 가이드")
    val submissionGuide: AssignmentSubmissionGuideResponse? = null,
    @field:Schema(description = "언어별 코드 템플릿")
    val codeTemplates: List<AssignmentCodeTemplateResponse> = emptyList(),
    @field:Schema(description = "확장 메타데이터")
    val attributes: Map<String, Any?> = emptyMap(),
)

@Schema(description = "과제 생성 비교용 테스트 케이스 응답")
data class AssignmentCreateComparisonTestCaseResponse(
    @field:Schema(description = "테스트 케이스 순번", example = "1")
    val seq: Int,
    @field:Schema(description = "입력 인자 목록", example = """["ADD 1", "CLOSE"]""")
    val inputValues: List<String>,
    @field:Schema(description = "출력 예시", example = "+1")
    val outputText: String,
    @field:Schema(description = "공개 여부", example = "PUBLIC")
    val visibility: AssignmentTestCaseVisibility,
)

fun CreateAssignmentRequest.toCreateComparisonItemResponse(): AssignmentCreateComparisonItemResponse =
    AssignmentCreateComparisonItemResponse(
        assignmentId = null,
        weekNo = weekNo,
        orderInWeek = orderInWeek,
        startAt = startAt,
        endAt = endAt,
        metadata = metadata.toCreateComparisonMetadataResponse(),
    )

fun AssignmentDetailResponse.toCreateComparisonItemResponse(): AssignmentCreateComparisonItemResponse =
    AssignmentCreateComparisonItemResponse(
        assignmentId = id,
        weekNo = weekNo,
        orderInWeek = orderInWeek,
        startAt = startAt,
        endAt = endAt,
        metadata = AssignmentCreateComparisonMetadataResponse(
            title = metadata.title,
            difficulty = metadata.difficulty,
            problemDescription = metadata.description,
            requirements = metadata.requirements,
            learningGoals = metadata.learningGoals,
            testCases = metadata.testCases.map {
                AssignmentCreateComparisonTestCaseResponse(
                    seq = it.seq,
                    inputValues = it.inputValues,
                    outputText = it.outputText,
                    visibility = it.visibility,
                )
            },
            problemDetail = metadata.problemDetail,
            submissionGuide = metadata.submissionGuide,
            codeTemplates = metadata.codeTemplates,
            attributes = metadata.attributes,
        ),
    )

private fun AssignmentMetadataPayload.toCreateComparisonMetadataResponse(): AssignmentCreateComparisonMetadataResponse =
    AssignmentCreateComparisonMetadataResponse(
        title = title.orEmpty(),
        difficulty = difficulty,
        problemDescription = description.orEmpty(),
        requirements = requirements.map {
            AssignmentRequirementResponse(
                sortOrder = it.sortOrder,
                requirementText = it.requirementText,
            )
        },
        learningGoals = learningGoals.map {
            AssignmentLearningGoalResponse(
                sortOrder = it.sortOrder,
                learningGoalText = it.learningGoalText,
            )
        },
        testCases = testCases.map {
            AssignmentCreateComparisonTestCaseResponse(
                seq = it.seq,
                inputValues = it.inputValues,
                outputText = it.outputText,
                visibility = it.visibility,
            )
        },
        problemDetail = problemDetail?.toResponse(),
        submissionGuide = submissionGuide?.toResponse(),
        codeTemplates = codeTemplates.map { it.toResponse() },
        attributes = attributes,
    )

private fun AssignmentProblemDetailPayload.toResponse(): AssignmentProblemDetailResponse =
    AssignmentProblemDetailResponse(
        inputDescription = inputDescription,
        outputDescription = outputDescription,
        classification = classification?.toResponse(),
    )

private fun AssignmentProblemClassificationPayload.toResponse(): AssignmentProblemClassificationResponse =
    AssignmentProblemClassificationResponse(
        algorithmStep = algorithmStep,
        difficultyStep = difficultyStep,
    )

private fun AssignmentSubmissionGuidePayload.toResponse(): AssignmentSubmissionGuideResponse =
    AssignmentSubmissionGuideResponse(
        title = title,
        description = description,
        commentSections = commentSections,
    )

private fun AssignmentCodeTemplatePayload.toResponse(): AssignmentCodeTemplateResponse =
    AssignmentCodeTemplateResponse(
        language = language,
        commentTemplate = commentTemplate,
        functionTemplate = functionTemplate,
        runnableTemplate = runnableTemplate,
    )
