package com.example.aandi_post_web_server.assignment.dtos
import com.fasterxml.jackson.annotation.JsonProperty
import com.example.aandi_post_web_server.assignment.enum.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.enum.AssignmentProblemStep
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import com.example.aandi_post_web_server.assignment.enum.AssignmentTemplateLanguage
import com.example.aandi_post_web_server.assignment.enum.AssignmentTestCaseVisibility
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.Instant

@Schema(description = "문제 분류 요청")
data class AssignmentProblemClassificationPayload(
    @field:Schema(description = "문제 단계", example = "STEP0")
    val algorithmStep: AssignmentProblemStep,
    @field:Min(1)
    @field:Max(3)
    @field:Schema(description = "세부 난이도 단계", example = "1")
    val difficultyStep: Int,
)

@Schema(description = "문제 분류 응답")
data class AssignmentProblemClassificationResponse(
    @field:Schema(description = "문제 단계", example = "STEP0")
    val algorithmStep: AssignmentProblemStep,
    @field:Schema(description = "세부 난이도 단계", example = "1")
    val difficultyStep: Int,
)

@Schema(description = "문제 상세 정보 요청")
data class AssignmentProblemDetailPayload(
    @field:Schema(description = "입력 설명", example = "입력이 없다.")
    val inputDescription: String? = null,
    @field:Schema(description = "출력 설명", example = "Hello World!를 출력한다.")
    val outputDescription: String? = null,
    @field:Valid
    @field:Schema(description = "문제 분류")
    val classification: AssignmentProblemClassificationPayload? = null,
)

@Schema(description = "문제 상세 정보 응답")
data class AssignmentProblemDetailResponse(
    @field:Schema(description = "입력 설명", example = "입력이 없다.")
    val inputDescription: String? = null,
    @field:Schema(description = "출력 설명", example = "Hello World!를 출력한다.")
    val outputDescription: String? = null,
    @field:Schema(description = "문제 분류")
    val classification: AssignmentProblemClassificationResponse? = null,
)

@Schema(description = "제출 가이드 요청")
data class AssignmentSubmissionGuidePayload(
    @field:Schema(description = "가이드 제목", example = "문제 풀이 템플릿")
    val title: String = "문제 풀이 템플릿",
    @field:Schema(description = "가이드 설명", example = "제출 코드 상단에는 문제-해석-풀이 주석을 작성해야 합니다.")
    val description: String = "제출 코드 상단에는 문제-해석-풀이 주석을 작성해야 합니다.",
    @field:Schema(description = "주석 섹션 목록", example = "[\"문제\",\"해석\",\"풀이\"]")
    val commentSections: List<String> = listOf("문제", "해석", "풀이"),
)

@Schema(description = "제출 가이드 응답")
data class AssignmentSubmissionGuideResponse(
    @field:Schema(description = "가이드 제목", example = "문제 풀이 템플릿")
    val title: String,
    @field:Schema(description = "가이드 설명", example = "제출 코드 상단에는 문제-해석-풀이 주석을 작성해야 합니다.")
    val description: String,
    @field:Schema(description = "주석 섹션 목록")
    val commentSections: List<String>,
)

@Schema(description = "언어별 코드 템플릿 요청")
data class AssignmentCodeTemplatePayload(
    @field:Schema(description = "언어", example = "KOTLIN")
    val language: AssignmentTemplateLanguage,
    @field:Schema(description = "상단 주석 템플릿")
    val commentTemplate: String,
    @field:Schema(description = "함수 템플릿")
    val functionTemplate: String,
    @field:Schema(description = "실행 가능한 전체 템플릿")
    val runnableTemplate: String,
)

@Schema(description = "언어별 코드 템플릿 응답")
data class AssignmentCodeTemplateResponse(
    @field:Schema(description = "언어", example = "KOTLIN")
    val language: AssignmentTemplateLanguage,
    @field:Schema(description = "상단 주석 템플릿")
    val commentTemplate: String,
    @field:Schema(description = "함수 템플릿")
    val functionTemplate: String,
    @field:Schema(description = "실행 가능한 전체 템플릿")
    val runnableTemplate: String,
)

@Schema(description = "과제 메타데이터")
data class AssignmentMetadataPayload(
    @field:Schema(description = "과제 제목", example = "터미널 계산기")
    val title: String? = null,
    @field:Schema(description = "난이도", example = "MID")
    val difficulty: AssignmentDifficulty,
    @field:Schema(description = "과제 설명", example = "# 문제 설명")
    val description: String? = null,
    @field:Schema(description = "문제 요구 사항")
    val requirements: List<CreateAssignmentRequirementRequest> = emptyList(),
    @field:Schema(description = "학습 목표")
    val learningGoals: List<CreateAssignmentLearningGoalRequest> = emptyList(),
    @field:Schema(description = "테스트 케이스")
    val testCases: List<CreateAssignmentTestCaseRequest> = emptyList(),
    @field:Valid
    @field:Schema(description = "문제 상세 정보")
    val problemDetail: AssignmentProblemDetailPayload? = null,
    @field:Valid
    @field:Schema(description = "제출 가이드")
    val submissionGuide: AssignmentSubmissionGuidePayload? = null,
    @field:Valid
    @field:Schema(description = "언어별 코드 템플릿")
    val codeTemplates: List<AssignmentCodeTemplatePayload> = emptyList(),
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

@Schema(description = "과제 학습 목표 생성 요청")
data class CreateAssignmentLearningGoalRequest(
    @field:Min(1)
    @field:Schema(description = "학습 목표 정렬 순서", example = "1")
    val sortOrder: Int,
    @field:NotBlank
    @field:Schema(description = "학습 목표 내용", example = "함수 분리")
    val learningGoalText: String,
)

@Schema(description = "과제 테스트 케이스 생성 요청")
data class CreateAssignmentTestCaseRequest(
    @field:Min(1)
    @field:Schema(description = "테스트 케이스 순번", example = "1")
    val seq: Int,
    @field:NotBlank
    @field:Schema(description = "입력 예시", example = "ADD 1\\nCLOSE")
    val inputText: String,
    @field:NotBlank
    @field:Schema(description = "출력 예시", example = "+1")
    val outputText: String,
    @field:Schema(description = "공개 여부", example = "PUBLIC")
    val visibility: AssignmentTestCaseVisibility = AssignmentTestCaseVisibility.PUBLIC,
)

@Schema(
    description = "과제 생성 요청",
    example =
        """
        {
          "weekNo": 1,
          "orderInWeek": 1,
          "startAt": "2026-03-03T09:00:00+09:00",
          "endAt": "2026-03-11T08:59:59+09:00",
          "metadata": {
            "title": "터미널 계산기",
            "difficulty": "MID",
            "description": "# 문제 설명",
            "requirements": [
              {
                "sortOrder": 1,
                "requirementText": "함수 분리 필수"
              }
            ],
            "learningGoals": [
              {
                "sortOrder": 1,
                "learningGoalText": "함수 분리"
              }
            ],
            "testCases": [
              {
                "seq": 1,
                "inputText": "ADD 1\\nCLOSE",
                "outputText": "+1",
                "visibility": "PUBLIC"
              }
            ],
            "problemDetail": {
              "inputDescription": "입력이 없다.",
              "outputDescription": "Hello World!를 출력한다.",
              "classification": {
                "algorithmStep": "STEP0",
                "difficultyStep": 1
              }
            },
            "submissionGuide": {
              "title": "문제 풀이 템플릿",
              "description": "제출 코드 상단에는 문제-해석-풀이 주석을 작성해야 합니다.",
              "commentSections": ["문제", "해석", "풀이"]
            },
            "codeTemplates": [
              {
                "language": "KOTLIN",
                "commentTemplate": "/*\\n[문제]\\n> 이해한 방식으로 문제를 다시 정의해요\\n[해석]\\n> 문제의 요구사항을 분석해요\\n[풀이]\\n> 적용할 풀이를 작성해요\\n*/",
                "functionTemplate": "fun solution(): String {\\n    var answer = \\\"\\\"\\n    return answer\\n}",
                "runnableTemplate": "fun solution(): String {\\n    var answer = \\\"Hello World!\\\"\\n    return answer\\n}\\n\\nfun main() {\\n    println(solution())\\n}"
              }
            ],
            "attributes": {
              "language": "kotlin"
            }
          }
        }
        """,
)
data class CreateAssignmentRequest(
    @field:Min(1)
    @field:Schema(description = "주차 번호", example = "1")
    val weekNo: Int,
    @field:Min(1)
    @field:Schema(description = "주차 내 순서", example = "1")
    val orderInWeek: Int,
    @field:Schema(description = "공개 시작 시각(KST/Asia/Seoul)", example = "2026-03-03T09:00:00+09:00")
    val startAt: Instant,
    @field:Schema(description = "마감 시각(KST/Asia/Seoul)", example = "2026-03-11T08:59:59+09:00")
    val endAt: Instant,
    @field:Schema(description = "과제 메타데이터")
    val metadata: AssignmentMetadataPayload,
)

@Schema(
    description = "과제 수정 요청",
    example =
        """
        {
          "orderInWeek": 2,
          "startAt": "2026-03-04T09:00:00+09:00",
          "endAt": "2026-03-12T08:59:59+09:00",
          "metadata": {
            "title": "터미널 계산기 응용",
            "difficulty": "HIGH",
            "description": "# 문제 설명(수정)",
            "requirements": [
              {
                "sortOrder": 1,
                "requirementText": "함수 분리 필수"
              }
            ],
            "learningGoals": [
              {
                "sortOrder": 1,
                "learningGoalText": "입력 파싱"
              },
              {
                "sortOrder": 2,
                "learningGoalText": "함수 분리"
              }
            ],
            "testCases": [
              {
                "seq": 1,
                "inputText": "ADD 1\\nCLOSE",
                "outputText": "+1",
                "visibility": "PUBLIC"
              }
            ],
            "problemDetail": {
              "inputDescription": "문자열 명령이 주어진다.",
              "outputDescription": "계산 결과를 출력한다.",
              "classification": {
                "algorithmStep": "STEP1",
                "difficultyStep": 2
              }
            },
            "submissionGuide": {
              "title": "문제 풀이 템플릿",
              "description": "제출 코드 상단에는 문제-해석-풀이 주석을 작성해야 합니다.",
              "commentSections": ["문제", "해석", "풀이"]
            },
            "attributes": {
              "language": "kotlin"
            }
          }
        }
        """,
)
data class UpdateAssignmentRequest(
    @field:Min(1)
    @field:Schema(description = "주차 번호(옵션)", example = "1")
    val weekNo: Int? = null,
    @field:Min(1)
    @field:Schema(description = "주차 내 순서(옵션)", example = "1")
    val orderInWeek: Int? = null,
    @field:Schema(description = "공개 시작 시각(옵션, KST/Asia/Seoul)", example = "2026-03-03T09:00:00+09:00")
    val startAt: Instant? = null,
    @field:Schema(description = "마감 시각(옵션, KST/Asia/Seoul)", example = "2026-03-11T08:59:59+09:00")
    val endAt: Instant? = null,
    @field:Schema(description = "과제 메타데이터(전체 교체, 옵션)")
    val metadata: AssignmentMetadataPayload? = null,
)

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
    @field:Schema(description = "문제 상세 정보")
    val problemDetail: AssignmentProblemDetailResponse? = null,
    @field:Schema(description = "제출 가이드")
    val submissionGuide: AssignmentSubmissionGuideResponse? = null,
    @field:Schema(description = "언어별 코드 템플릿")
    val codeTemplates: List<AssignmentCodeTemplateResponse> = emptyList(),
    @field:Schema(description = "확장 메타데이터")
    val attributes: Map<String, Any?>,
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
    @field:Schema(description = "문제 상세 정보")
    val problemDetail: AssignmentProblemDetailResponse? = null,
    @field:Schema(description = "제출 가이드")
    val submissionGuide: AssignmentSubmissionGuideResponse? = null,
    @field:Schema(description = "언어별 코드 템플릿")
    val codeTemplates: List<AssignmentCodeTemplateResponse> = emptyList(),
    @field:Schema(description = "확장 메타데이터")
    val attributes: Map<String, Any?>,
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
    @field:Schema(description = "입력 예시", example = "ADD 1\\nCLOSE")
    val inputText: String,
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
