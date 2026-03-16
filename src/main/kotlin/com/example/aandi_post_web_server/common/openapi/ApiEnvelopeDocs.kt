package com.example.aandi_post_web_server.common.openapi

import com.example.aandi_post_web_server.assignment.dtos.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentSubmissionConfigResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentSummaryResponse
import com.example.aandi_post_web_server.course.dtos.CourseEnrollmentResponse
import com.example.aandi_post_web_server.course.dtos.CourseOutlineResponse
import com.example.aandi_post_web_server.course.dtos.CourseResponse
import com.example.aandi_post_web_server.course.dtos.CourseWeekResponse
import com.example.aandi_post_web_server.submission.dtos.AssignmentSubmissionAcceptedResponse
import com.example.aandi_post_web_server.submission.dtos.AssignmentSubmissionResultResponse
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "공통 실패 응답",
    example =
        """
        {
          "success": false,
          "data": null,
          "error": {
            "code": "ERROR_CODE",
            "message": "에러 메시지"
          },
          "timestamp": "2026-03-09T12:00:00+09:00"
        }
        """,
)
data class ErrorEnvelopeDoc(
    @field:Schema(description = "요청 성공 여부", example = "false")
    val success: Boolean = false,
    @field:Schema(description = "실패 시 null", nullable = true, example = "null")
    val data: Any? = null,
    @field:Schema(description = "실패 정보")
    val error: ApiErrorPayload = ApiErrorPayload(
        code = "ERROR_CODE",
        message = "에러 메시지",
    ),
    @field:Schema(description = "응답 시각(Asia/Seoul)", example = "2026-03-09T12:00:00+09:00")
    val timestamp: String = "2026-03-09T12:00:00+09:00",
)

@Schema(
    description = "코스 목록 성공 응답",
    example = """{"success":true,"data":[{"id":"course-1","slug":"fl-basic","fieldTag":"FL","startDate":"2026-03-02","endDate":"2026-03-30","metadata":{"title":"FL 기초","description":"프론트엔드 트랙 기초 과정","phase":"BASIC","attributes":{}},"status":"PUBLISHED","createdAt":"2026-03-01T09:00:00Z","updatedAt":"2026-03-01T09:00:00Z"}],"error":null,"timestamp":"2026-03-09T12:00:00+09:00"}""",
)
data class CourseListEnvelopeDoc(
    @field:Schema(example = "true")
    val success: Boolean = true,
    val data: List<CourseResponse> = emptyList(),
    @field:Schema(nullable = true, example = "null")
    val error: ApiErrorPayload? = null,
    @field:Schema(example = "2026-03-09T12:00:00+09:00")
    val timestamp: String = "2026-03-09T12:00:00+09:00",
)

@Schema(
    description = "코스 단건 성공 응답",
    example = """{"success":true,"data":{"id":"course-1","slug":"fl-basic","fieldTag":"FL","startDate":"2026-03-02","endDate":"2026-03-30","metadata":{"title":"FL 기초","description":"프론트엔드 트랙 기초 과정","phase":"BASIC","attributes":{}},"status":"PUBLISHED","createdAt":"2026-03-01T09:00:00Z","updatedAt":"2026-03-01T09:00:00Z"},"error":null,"timestamp":"2026-03-09T12:00:00+09:00"}""",
)
data class CourseEnvelopeDoc(
    @field:Schema(example = "true")
    val success: Boolean = true,
    val data: CourseResponse? = null,
    @field:Schema(nullable = true, example = "null")
    val error: ApiErrorPayload? = null,
    @field:Schema(example = "2026-03-09T12:00:00+09:00")
    val timestamp: String = "2026-03-09T12:00:00+09:00",
)

@Schema(
    description = "수강 단건 성공 응답",
    example = """{"success":true,"data":{"courseId":"course-1","courseSlug":"fl-basic","userId":"user-uuid-1","publicCode":"#OR402","username":"string","status":"ENABLED","joinedAt":"2026-03-05T09:20:18Z","bannedAt":null,"banReason":null,"updatedAt":"2026-03-05T09:20:18Z"},"error":null,"timestamp":"2026-03-09T12:00:00+09:00"}""",
)
data class CourseEnrollmentEnvelopeDoc(
    @field:Schema(example = "true")
    val success: Boolean = true,
    val data: CourseEnrollmentResponse? = null,
    @field:Schema(nullable = true, example = "null")
    val error: ApiErrorPayload? = null,
    @field:Schema(example = "2026-03-09T12:00:00+09:00")
    val timestamp: String = "2026-03-09T12:00:00+09:00",
)

@Schema(
    description = "수강 목록 성공 응답",
    example = """{"success":true,"data":[{"courseId":"course-1","courseSlug":"fl-basic","userId":"user-uuid-1","publicCode":"#OR402","username":"string","status":"ENABLED","joinedAt":"2026-03-05T09:20:18Z","bannedAt":null,"banReason":null,"updatedAt":"2026-03-05T09:20:18Z"}],"error":null,"timestamp":"2026-03-09T12:00:00+09:00"}""",
)
data class CourseEnrollmentListEnvelopeDoc(
    @field:Schema(example = "true")
    val success: Boolean = true,
    val data: List<CourseEnrollmentResponse> = emptyList(),
    @field:Schema(nullable = true, example = "null")
    val error: ApiErrorPayload? = null,
    @field:Schema(example = "2026-03-09T12:00:00+09:00")
    val timestamp: String = "2026-03-09T12:00:00+09:00",
)

@Schema(
    description = "주차 목록 성공 응답",
    example = """{"success":true,"data":[{"id":"week-1","weekNo":1,"title":"1주차 - Kotlin 기본","startDate":"2026-03-02","endDate":"2026-03-08","createdAt":"2026-03-01T09:00:00Z","updatedAt":"2026-03-01T09:00:00Z"}],"error":null,"timestamp":"2026-03-09T12:00:00+09:00"}""",
)
data class CourseWeekListEnvelopeDoc(
    @field:Schema(example = "true")
    val success: Boolean = true,
    val data: List<CourseWeekResponse> = emptyList(),
    @field:Schema(nullable = true, example = "null")
    val error: ApiErrorPayload? = null,
    @field:Schema(example = "2026-03-09T12:00:00+09:00")
    val timestamp: String = "2026-03-09T12:00:00+09:00",
)

@Schema(
    description = "코스 목차 성공 응답",
    example = """{"success":true,"data":{"course":{"id":"course-1","slug":"cs-basic-fl","fieldTag":"FL","title":"기초 CS 과정","description":"Computer Science Fundamentals","phase":"BASIC"},"totalAssignments":3,"assignments":[{"assignmentId":"8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111","weekNo":1,"orderInWeek":1,"title":"터미널 계산기","difficulty":"MID","startAt":"2026-03-03T00:00:00Z","endAt":"2026-03-11T00:00:00Z","checked":true}]},"error":null,"timestamp":"2026-03-09T12:00:00+09:00"}""",
)
data class CourseOutlineEnvelopeDoc(
    @field:Schema(example = "true")
    val success: Boolean = true,
    val data: CourseOutlineResponse? = null,
    @field:Schema(nullable = true, example = "null")
    val error: ApiErrorPayload? = null,
    @field:Schema(example = "2026-03-09T12:00:00+09:00")
    val timestamp: String = "2026-03-09T12:00:00+09:00",
)

@Schema(
    description = "과제 목록 성공 응답",
    example = """{"success":true,"data":[{"assignmentId":"8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111","weekNo":1,"orderInWeek":1,"startAt":"2026-03-03T00:00:00Z","endAt":"2026-03-11T00:00:00Z","status":"PUBLISHED","metadata":{"title":"터미널 계산기","difficulty":"MID","description":"# 문제 설명","requirements":[{"sortOrder":1,"requirementText":"함수 분리 필수"}],"learningGoals":[{"sortOrder":1,"learningGoalText":"함수 분리"}],"examples":[{"seq":1,"inputText":"ADD 1\\nCLOSE","outputText":"+1"}],"problemDetail":{"inputDescription":"입력이 없다.","outputDescription":"Hello World!를 출력한다.","classification":{"algorithmStep":"STEP0","difficultyStep":1}},"submissionGuide":{"title":"문제 풀이 템플릿","description":"제출 코드 상단에는 문제-해석-풀이 주석을 작성해야 합니다.","commentSections":["문제","해석","풀이"]},"codeTemplates":[{"language":"KOTLIN","commentTemplate":"/* ... */","functionTemplate":"fun solution(): String { ... }","runnableTemplate":"fun solution(): String { ... }"}],"attributes":{"language":"kotlin"}}}],"error":null,"timestamp":"2026-03-09T12:00:00+09:00"}""",
)
data class AssignmentSummaryListEnvelopeDoc(
    @field:Schema(example = "true")
    val success: Boolean = true,
    val data: List<AssignmentSummaryResponse> = emptyList(),
    @field:Schema(nullable = true, example = "null")
    val error: ApiErrorPayload? = null,
    @field:Schema(example = "2026-03-09T12:00:00+09:00")
    val timestamp: String = "2026-03-09T12:00:00+09:00",
)

@Schema(
    description = "과제 상세 성공 응답",
    example = """{"success":true,"data":{"assignmentId":"8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111","courseSlug":"fl-basic","weekNo":1,"orderInWeek":1,"startAt":"2026-03-03T00:00:00Z","endAt":"2026-03-11T00:00:00Z","status":"PUBLISHED","publishedAt":"2026-03-03T00:00:00Z","metadata":{"title":"터미널 계산기","difficulty":"MID","description":"# 문제 설명","requirements":[{"sortOrder":1,"requirementText":"함수 분리 필수"}],"learningGoals":[{"sortOrder":1,"learningGoalText":"함수 분리"}],"examples":[{"seq":1,"inputText":"ADD 1\\nCLOSE","outputText":"+1"}],"problemDetail":{"inputDescription":"입력이 없다.","outputDescription":"Hello World!를 출력한다.","classification":{"algorithmStep":"STEP0","difficultyStep":1}},"attributes":{"language":"kotlin"}}},"error":null,"timestamp":"2026-03-09T12:00:00+09:00"}""",
)
data class AssignmentDetailEnvelopeDoc(
    @field:Schema(example = "true")
    val success: Boolean = true,
    val data: AssignmentDetailResponse? = null,
    @field:Schema(nullable = true, example = "null")
    val error: ApiErrorPayload? = null,
    @field:Schema(example = "2026-03-09T12:00:00+09:00")
    val timestamp: String = "2026-03-09T12:00:00+09:00",
)

@Schema(
    description = "과제 제출 설정 성공 응답",
    example = """{"success":true,"data":{"assignmentId":"8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111","courseSlug":"fl-basic","submissionGuide":{"title":"문제 풀이 템플릿","description":"제출 코드 상단에는 문제-해석-풀이 주석을 작성해야 합니다.","commentSections":["문제","해석","풀이"]},"codeTemplates":[{"language":"KOTLIN","commentTemplate":"/* ... */","functionTemplate":"fun solution(): String { ... }","runnableTemplate":"fun solution(): String { ... }"},{"language":"DART","commentTemplate":"/* ... */","functionTemplate":"String solution() { ... }","runnableTemplate":"String solution() { ... }"}],"supportedLanguages":["KOTLIN","DART"]},"error":null,"timestamp":"2026-03-09T12:00:00+09:00"}""",
)
data class AssignmentSubmissionConfigEnvelopeDoc(
    @field:Schema(example = "true")
    val success: Boolean = true,
    val data: AssignmentSubmissionConfigResponse? = null,
    @field:Schema(nullable = true, example = "null")
    val error: ApiErrorPayload? = null,
    @field:Schema(example = "2026-03-09T12:00:00+09:00")
    val timestamp: String = "2026-03-09T12:00:00+09:00",
)

@Schema(
    description = "과제 제출 접수 성공 응답",
    example = """{"success":true,"data":{"submissionId":"67d3a37e5f0c1e42c0f4a123","assignmentId":"8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111","status":"PENDING","streamUrl":"/v1/courses/fl-basic/assignments/8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111/submissions/67d3a37e5f0c1e42c0f4a123/stream","resultUrl":"/v1/courses/fl-basic/assignments/8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111/submissions/67d3a37e5f0c1e42c0f4a123","createdAt":"2026-03-15T12:00:00+09:00"},"error":null,"timestamp":"2026-03-15T12:00:00+09:00"}""",
)
data class AssignmentSubmissionAcceptedEnvelopeDoc(
    @field:Schema(example = "true")
    val success: Boolean = true,
    val data: AssignmentSubmissionAcceptedResponse? = null,
    @field:Schema(nullable = true, example = "null")
    val error: ApiErrorPayload? = null,
    @field:Schema(example = "2026-03-15T12:00:00+09:00")
    val timestamp: String = "2026-03-15T12:00:00+09:00",
)

@Schema(
    description = "과제 제출 결과 성공 응답",
    example = """{"success":true,"data":{"submissionId":"67d3a37e5f0c1e42c0f4a123","assignmentId":"8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111","language":"KOTLIN","status":"ACCEPTED","testCases":[{"caseId":1,"status":"PASSED","timeMs":12.3,"memoryMb":4.2,"output":"8","error":null}],"createdAt":"2026-03-15T12:00:00+09:00","completedAt":"2026-03-15T12:00:10+09:00"},"error":null,"timestamp":"2026-03-15T12:00:10+09:00"}""",
)
data class AssignmentSubmissionResultEnvelopeDoc(
    @field:Schema(example = "true")
    val success: Boolean = true,
    val data: AssignmentSubmissionResultResponse? = null,
    @field:Schema(nullable = true, example = "null")
    val error: ApiErrorPayload? = null,
    @field:Schema(example = "2026-03-15T12:00:10+09:00")
    val timestamp: String = "2026-03-15T12:00:10+09:00",
)

@Schema(
    description = "본문 없는 성공 응답",
    example = """{"success":true,"data":null,"error":null,"timestamp":"2026-03-09T12:00:00+09:00"}""",
)
data class EmptyEnvelopeDoc(
    @field:Schema(example = "true")
    val success: Boolean = true,
    @field:Schema(nullable = true, example = "null")
    val data: Any? = null,
    @field:Schema(nullable = true, example = "null")
    val error: ApiErrorPayload? = null,
    @field:Schema(example = "2026-03-09T12:00:00+09:00")
    val timestamp: String = "2026-03-09T12:00:00+09:00",
)
