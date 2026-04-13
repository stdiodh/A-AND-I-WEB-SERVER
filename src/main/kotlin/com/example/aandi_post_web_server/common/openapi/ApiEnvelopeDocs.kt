package com.example.aandi_post_web_server.common.openapi

import com.example.aandi_post_web_server.assignment.dtos.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentSummaryResponse
import com.example.aandi_post_web_server.assignment.v2.dto.AssignmentSubmissionStatusResponse
import com.example.aandi_post_web_server.course.dtos.CourseEnrollmentResponse
import com.example.aandi_post_web_server.course.dtos.CourseOutlineResponse
import com.example.aandi_post_web_server.course.dtos.CourseResponse
import com.example.aandi_post_web_server.course.dtos.CourseWeekResponse
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
    example = """{"success":true,"data":[{"assignmentId":"8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111","weekNo":1,"orderInWeek":1,"startAt":"2026-03-03T00:00:00Z","endAt":"2026-03-11T00:00:00Z","status":"PUBLISHED","metadata":{"title":"터미널 계산기","difficulty":"MID","description":"# 문제 설명","requirements":[{"sortOrder":1,"requirementText":"함수 분리 필수"}],"learningGoals":[{"sortOrder":1,"learningGoalText":"함수 분리"}],"testCases":[{"seq":1,"inputValues":["ADD 1","CLOSE"],"outputText":"+1","visibility":"PUBLIC"}],"codeTemplates":[{"language":"KOTLIN","functionTemplate":"/* ... */\nfun solution(): String { ... }"}]}}],"error":null,"timestamp":"2026-03-09T12:00:00+09:00"}""",
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
    example = """{"success":true,"data":{"assignmentId":"8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111","courseSlug":"fl-basic","weekNo":1,"orderInWeek":1,"startAt":"2026-03-03T00:00:00Z","endAt":"2026-03-11T00:00:00Z","status":"PUBLISHED","publishedAt":"2026-03-03T00:00:00Z","metadata":{"title":"터미널 계산기","difficulty":"MID","description":"# 문제 설명","requirements":[{"sortOrder":1,"requirementText":"함수 분리 필수"}],"learningGoals":[{"sortOrder":1,"learningGoalText":"함수 분리"}],"testCases":[{"seq":1,"inputValues":["ADD 1","CLOSE"],"outputText":"+1","visibility":"PUBLIC"}],"codeTemplates":[{"language":"KOTLIN","functionTemplate":"/* ... */\nfun solution(): String { ... }"}]}},"error":null,"timestamp":"2026-03-09T12:00:00+09:00"}""",
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
    description = "현재 사용자의 과제 제출 여부 성공 응답. projection 이 없으면 submitted=false 와 null 상세 필드를 반환합니다.",
    example = """{"success":true,"data":{"assignmentId":"7fbe8f62-9d89-4c74-b1e4-3ad3b9d7f001","submitted":true,"firstCompletedAt":"2026-04-13T08:20:11Z","lastCompletedAt":"2026-04-13T08:20:11Z","latestScore":80,"passedCases":8,"totalCases":10},"error":null,"timestamp":"2026-04-13T17:20:11+09:00"}""",
)
data class AssignmentSubmissionStatusEnvelopeDoc(
    @field:Schema(example = "true")
    val success: Boolean = true,
    val data: AssignmentSubmissionStatusResponse? = null,
    @field:Schema(nullable = true, example = "null")
    val error: ApiErrorPayload? = null,
    @field:Schema(example = "2026-04-13T17:20:11+09:00")
    val timestamp: String = "2026-04-13T17:20:11+09:00",
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
