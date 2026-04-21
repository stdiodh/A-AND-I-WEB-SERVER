package com.example.aandi_post_web_server.common.openapi

import com.example.aandi_post_web_server.assignment.api.dto.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentSummaryResponse
import com.example.aandi_post_web_server.assignment.api.v2.dto.AdminAssignmentSubmissionStatusesResponse
import com.example.aandi_post_web_server.common.api.envelope.V2ApiError
import com.example.aandi_post_web_server.course.api.dto.CourseEnrollmentResponse
import com.example.aandi_post_web_server.course.api.dto.CourseOutlineResponse
import com.example.aandi_post_web_server.course.api.dto.CourseResponse
import com.example.aandi_post_web_server.course.api.dto.CourseWeekResponse
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "A&I v2 공통 실패 응답",
    example =
        """
        {
          "success": false,
          "data": null,
          "error": {
            "code": 40301,
            "message": "timestamp 헤더는 ISO-8601 또는 epoch milliseconds 형식이어야 합니다.",
            "value": "VALIDATE_ERROR",
            "alert": "입력값 형식이 올바르지 않습니다."
          },
          "timestamp": "2026-04-13T18:00:00+09:00"
        }
        """,
)
data class V2ErrorEnvelopeDoc(
    @field:Schema(example = "false")
    val success: Boolean = false,
    @field:Schema(nullable = true, example = "null")
    val data: Any? = null,
    val error: V2ApiError = V2ApiError(
        code = 40301,
        message = "timestamp 헤더는 ISO-8601 또는 epoch milliseconds 형식이어야 합니다.",
        value = "VALIDATE_ERROR",
        alert = "입력값 형식이 올바르지 않습니다.",
    ),
    @field:Schema(example = "2026-04-13T18:00:00+09:00")
    val timestamp: String = "2026-04-13T18:00:00+09:00",
)

@Schema(
    description = "A&I v2 코스 목록 성공 응답",
    example = """{"success":true,"data":[{"id":"course-1","slug":"fl-basic","fieldTag":"FL","startDate":"2026-03-02","endDate":"2026-03-30","metadata":{"title":"FL 기초","description":"프론트엔드 트랙 기초 과정","phase":"BASIC","attributes":{}},"status":"PUBLISHED","createdAt":"2026-03-01T09:00:00Z","updatedAt":"2026-03-01T09:00:00Z"}],"error":null,"timestamp":"2026-04-13T18:00:00+09:00"}""",
)
data class V2CourseListEnvelopeDoc(
    @field:Schema(example = "true")
    val success: Boolean = true,
    val data: List<CourseResponse> = emptyList(),
    @field:Schema(nullable = true, example = "null")
    val error: V2ApiError? = null,
    @field:Schema(example = "2026-04-13T18:00:00+09:00")
    val timestamp: String = "2026-04-13T18:00:00+09:00",
)

@Schema(
    description = "A&I v2 코스 단건 성공 응답",
    example = """{"success":true,"data":{"id":"course-1","slug":"fl-basic","fieldTag":"FL","startDate":"2026-03-02","endDate":"2026-03-30","metadata":{"title":"FL 기초","description":"프론트엔드 트랙 기초 과정","phase":"BASIC","attributes":{}},"status":"PUBLISHED","createdAt":"2026-03-01T09:00:00Z","updatedAt":"2026-03-01T09:00:00Z"},"error":null,"timestamp":"2026-04-13T18:00:00+09:00"}""",
)
data class V2CourseEnvelopeDoc(
    @field:Schema(example = "true")
    val success: Boolean = true,
    val data: CourseResponse? = null,
    @field:Schema(nullable = true, example = "null")
    val error: V2ApiError? = null,
    @field:Schema(example = "2026-04-13T18:00:00+09:00")
    val timestamp: String = "2026-04-13T18:00:00+09:00",
)

@Schema(
    description = "A&I v2 수강 단건 성공 응답",
    example = """{"success":true,"data":{"courseId":"course-1","courseSlug":"fl-basic","userId":"user-uuid-1","publicCode":"#OR402","username":"string","status":"ENABLED","joinedAt":"2026-03-05T09:20:18Z","bannedAt":null,"banReason":null,"updatedAt":"2026-03-05T09:20:18Z"},"error":null,"timestamp":"2026-04-13T18:00:00+09:00"}""",
)
data class V2CourseEnrollmentEnvelopeDoc(
    @field:Schema(example = "true")
    val success: Boolean = true,
    val data: CourseEnrollmentResponse? = null,
    @field:Schema(nullable = true, example = "null")
    val error: V2ApiError? = null,
    @field:Schema(example = "2026-04-13T18:00:00+09:00")
    val timestamp: String = "2026-04-13T18:00:00+09:00",
)

@Schema(
    description = "A&I v2 수강 목록 성공 응답",
    example = """{"success":true,"data":[{"courseId":"course-1","courseSlug":"fl-basic","userId":"user-uuid-1","publicCode":"#OR402","username":"string","status":"ENABLED","joinedAt":"2026-03-05T09:20:18Z","bannedAt":null,"banReason":null,"updatedAt":"2026-03-05T09:20:18Z"}],"error":null,"timestamp":"2026-04-13T18:00:00+09:00"}""",
)
data class V2CourseEnrollmentListEnvelopeDoc(
    @field:Schema(example = "true")
    val success: Boolean = true,
    val data: List<CourseEnrollmentResponse> = emptyList(),
    @field:Schema(nullable = true, example = "null")
    val error: V2ApiError? = null,
    @field:Schema(example = "2026-04-13T18:00:00+09:00")
    val timestamp: String = "2026-04-13T18:00:00+09:00",
)

@Schema(
    description = "A&I v2 주차 목록 성공 응답",
    example = """{"success":true,"data":[{"id":"week-1","weekNo":1,"title":"1주차 - Kotlin 기본","startDate":"2026-03-02","endDate":"2026-03-08","createdAt":"2026-03-01T09:00:00Z","updatedAt":"2026-03-01T09:00:00Z"}],"error":null,"timestamp":"2026-04-13T18:00:00+09:00"}""",
)
data class V2CourseWeekListEnvelopeDoc(
    @field:Schema(example = "true")
    val success: Boolean = true,
    val data: List<CourseWeekResponse> = emptyList(),
    @field:Schema(nullable = true, example = "null")
    val error: V2ApiError? = null,
    @field:Schema(example = "2026-04-13T18:00:00+09:00")
    val timestamp: String = "2026-04-13T18:00:00+09:00",
)

@Schema(
    description = "A&I v2 코스 목차 성공 응답",
    example = """{"success":true,"data":{"course":{"id":"course-1","slug":"cs-basic-fl","fieldTag":"FL","title":"기초 CS 과정","description":"Computer Science Fundamentals","phase":"BASIC"},"totalAssignments":3,"assignments":[{"assignmentId":"8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111","weekNo":1,"orderInWeek":1,"title":"터미널 계산기","difficulty":"MID","startAt":"2026-03-03T00:00:00Z","endAt":"2026-03-11T00:00:00Z","checked":true}]},"error":null,"timestamp":"2026-04-13T18:00:00+09:00"}""",
)
data class V2CourseOutlineEnvelopeDoc(
    @field:Schema(example = "true")
    val success: Boolean = true,
    val data: CourseOutlineResponse? = null,
    @field:Schema(nullable = true, example = "null")
    val error: V2ApiError? = null,
    @field:Schema(example = "2026-04-13T18:00:00+09:00")
    val timestamp: String = "2026-04-13T18:00:00+09:00",
)

@Schema(
    description = "A&I v2 과제 목록 성공 응답",
    example = """{"success":true,"data":[{"assignmentId":"8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111","weekNo":1,"orderInWeek":1,"startAt":"2026-03-03T00:00:00Z","endAt":"2026-03-11T00:00:00Z","status":"PUBLISHED","metadata":{"title":"터미널 계산기","difficulty":"MID","description":"# 문제 설명","requirements":[{"sortOrder":1,"requirementText":"함수 분리 필수"}],"learningGoals":[{"sortOrder":1,"learningGoalText":"함수 분리"}],"testCases":[{"seq":1,"inputValues":["ADD 1","CLOSE"],"outputText":"+1","visibility":"PUBLIC"}],"codeTemplates":[{"language":"KOTLIN","functionTemplate":"/* ... */\nfun solution(): String { ... }"}]}}],"error":null,"timestamp":"2026-04-13T18:00:00+09:00"}""",
)
data class V2AssignmentSummaryListEnvelopeDoc(
    @field:Schema(example = "true")
    val success: Boolean = true,
    val data: List<AssignmentSummaryResponse> = emptyList(),
    @field:Schema(nullable = true, example = "null")
    val error: V2ApiError? = null,
    @field:Schema(example = "2026-04-13T18:00:00+09:00")
    val timestamp: String = "2026-04-13T18:00:00+09:00",
)

@Schema(
    description = "A&I v2 과제 상세 성공 응답",
    example = """{"success":true,"data":{"assignmentId":"8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111","courseSlug":"fl-basic","weekNo":1,"orderInWeek":1,"startAt":"2026-03-03T00:00:00Z","endAt":"2026-03-11T00:00:00Z","status":"PUBLISHED","publishedAt":"2026-03-03T00:00:00Z","metadata":{"title":"터미널 계산기","difficulty":"MID","description":"# 문제 설명","requirements":[{"sortOrder":1,"requirementText":"함수 분리 필수"}],"learningGoals":[{"sortOrder":1,"learningGoalText":"함수 분리"}],"testCases":[{"seq":1,"inputValues":["ADD 1","CLOSE"],"outputText":"+1","visibility":"PUBLIC"}],"codeTemplates":[{"language":"KOTLIN","functionTemplate":"/* ... */\nfun solution(): String { ... }"}]}},"error":null,"timestamp":"2026-04-13T18:00:00+09:00"}""",
)
data class V2AssignmentDetailEnvelopeDoc(
    @field:Schema(example = "true")
    val success: Boolean = true,
    val data: AssignmentDetailResponse? = null,
    @field:Schema(nullable = true, example = "null")
    val error: V2ApiError? = null,
    @field:Schema(example = "2026-04-13T18:00:00+09:00")
    val timestamp: String = "2026-04-13T18:00:00+09:00",
)

@Schema(
    description = "A&I v2 관리자용 과제 제출 현황 성공 응답. 코스 수강생 전체를 기준으로 projection 존재 여부를 합쳐 제출/미제출 현황을 반환합니다.",
    example = """{"success":true,"data":{"assignmentId":"7fbe8f62-9d89-4c74-b1e4-3ad3b9d7f001","courseSlug":"back-basic","totalEnrolled":3,"submittedCount":2,"notSubmittedCount":1,"items":[{"userId":"user-1","publicCode":"A00123","username":"alice","enrollmentStatus":"ENABLED","submitted":true,"score":90,"passedCases":9,"totalCases":10,"completedAt":"2026-04-09T02:15:30.123Z"},{"userId":"user-2","publicCode":"A00124","username":"bob","enrollmentStatus":"ENABLED","submitted":false,"score":null,"passedCases":null,"totalCases":null,"completedAt":null}]},"error":null,"timestamp":"2026-04-13T18:00:00+09:00"}""",
)
data class V2AdminAssignmentSubmissionStatusesEnvelopeDoc(
    @field:Schema(example = "true")
    val success: Boolean = true,
    val data: AdminAssignmentSubmissionStatusesResponse? = null,
    @field:Schema(nullable = true, example = "null")
    val error: V2ApiError? = null,
    @field:Schema(example = "2026-04-13T18:00:00+09:00")
    val timestamp: String = "2026-04-13T18:00:00+09:00",
)

@Schema(
    description = "A&I v2 본문 없는 성공 응답",
    example = """{"success":true,"data":null,"error":null,"timestamp":"2026-04-13T18:00:00+09:00"}""",
)
data class V2EmptyEnvelopeDoc(
    @field:Schema(example = "true")
    val success: Boolean = true,
    @field:Schema(nullable = true, example = "null")
    val data: Any? = null,
    @field:Schema(nullable = true, example = "null")
    val error: V2ApiError? = null,
    @field:Schema(example = "2026-04-13T18:00:00+09:00")
    val timestamp: String = "2026-04-13T18:00:00+09:00",
)
