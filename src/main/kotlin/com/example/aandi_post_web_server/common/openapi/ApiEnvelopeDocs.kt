package com.example.aandi_post_web_server.common.openapi

import com.example.aandi_post_web_server.assignment.dtos.AssignmentDeliveryResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentSummaryResponse
import com.example.aandi_post_web_server.assignment.dtos.PublishAssignmentResponse
import com.example.aandi_post_web_server.assignment.dtos.TriggerDeliveriesResponse
import com.example.aandi_post_web_server.course.dtos.CourseEnrollmentResponse
import com.example.aandi_post_web_server.course.dtos.CourseOutlineResponse
import com.example.aandi_post_web_server.course.dtos.CourseResponse
import com.example.aandi_post_web_server.course.dtos.CourseWeekResponse
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "공통 실패 응답 Envelope",
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
    example = """{"success":true,"data":{"id":"enroll-1","userId":"mekazon","status":"ENROLLED","joinedAt":"2026-03-05T09:20:18Z","droppedAt":null,"bannedAt":null,"banReason":null,"updatedAt":"2026-03-05T09:20:18Z"},"error":null,"timestamp":"2026-03-09T12:00:00+09:00"}""",
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
    example = """{"success":true,"data":[{"id":"enroll-1","userId":"mekazon","status":"ENROLLED","joinedAt":"2026-03-05T09:20:18Z","droppedAt":null,"bannedAt":null,"banReason":null,"updatedAt":"2026-03-05T09:20:18Z"}],"error":null,"timestamp":"2026-03-09T12:00:00+09:00"}""",
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
    description = "코스 목차 요약 성공 응답",
    example = """{"success":true,"data":{"course":{"id":"course-1","slug":"cs-basic-fl","fieldTag":"FL","title":"기초 CS 과정","description":"Computer Science Fundamentals","phase":"BASIC"},"totalAssignments":3,"assignments":[{"assignmentId":"assignment-1","weekNo":1,"orderInWeek":1,"title":"터미널 계산기","difficulty":"MID","startAt":"2026-03-03T00:00:00Z","endAt":"2026-03-11T00:00:00Z","checked":true}]},"error":null,"timestamp":"2026-03-09T12:00:00+09:00"}""",
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
    example = """{"success":true,"data":[{"id":"assignment-1","weekNo":1,"orderInWeek":1,"startAt":"2026-03-03T00:00:00Z","endAt":"2026-03-11T00:00:00Z","status":"PUBLISHED","metadata":{"title":"터미널 계산기","difficulty":"MID","description":"# 문제 설명","timeLimitMinutes":60,"learningGoals":["함수 분리"],"attributes":{"language":"kotlin"}}}],"error":null,"timestamp":"2026-03-09T12:00:00+09:00"}""",
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
    example = """{"success":true,"data":{"id":"assignment-1","courseSlug":"fl-basic","weekNo":1,"orderInWeek":1,"startAt":"2026-03-03T00:00:00Z","endAt":"2026-03-11T00:00:00Z","status":"DRAFT","publishedAt":null,"metadata":{"title":"터미널 계산기","difficulty":"MID","description":"# 문제 설명","timeLimitMinutes":60,"learningGoals":["함수 분리"],"attributes":{"language":"kotlin"}},"requirements":[{"sortOrder":1,"requirementText":"함수 분리 필수"}],"examples":[{"seq":1,"inputText":"ADD 1\\nCLOSE","outputText":"+1","description":"기본 동작"}]},"error":null,"timestamp":"2026-03-09T12:00:00+09:00"}""",
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
    description = "과제 게시 성공 응답",
    example = """{"success":true,"data":{"assignmentId":"assignment-1","courseSlug":"fl-basic","status":"PUBLISHED","publishedAt":"2026-03-10T00:00:00Z"},"error":null,"timestamp":"2026-03-09T12:00:00+09:00"}""",
)
data class PublishAssignmentEnvelopeDoc(
    @field:Schema(example = "true")
    val success: Boolean = true,
    val data: PublishAssignmentResponse? = null,
    @field:Schema(nullable = true, example = "null")
    val error: ApiErrorPayload? = null,
    @field:Schema(example = "2026-03-09T12:00:00+09:00")
    val timestamp: String = "2026-03-09T12:00:00+09:00",
)

@Schema(
    description = "배포 트리거 성공 응답",
    example = """{"success":true,"data":{"assignmentId":"assignment-1","courseSlug":"fl-basic","targetCount":42,"deliveredCount":40,"failedCount":2},"error":null,"timestamp":"2026-03-09T12:00:00+09:00"}""",
)
data class TriggerDeliveriesEnvelopeDoc(
    @field:Schema(example = "true")
    val success: Boolean = true,
    val data: TriggerDeliveriesResponse? = null,
    @field:Schema(nullable = true, example = "null")
    val error: ApiErrorPayload? = null,
    @field:Schema(example = "2026-03-09T12:00:00+09:00")
    val timestamp: String = "2026-03-09T12:00:00+09:00",
)

@Schema(
    description = "배포 결과 목록 성공 응답",
    example = """{"success":true,"data":[{"userId":"mekazon","status":"DELIVERED","deliveredAt":"2026-03-10T00:01:00Z","failureReason":null}],"error":null,"timestamp":"2026-03-09T12:00:00+09:00"}""",
)
data class AssignmentDeliveryListEnvelopeDoc(
    @field:Schema(example = "true")
    val success: Boolean = true,
    val data: List<AssignmentDeliveryResponse> = emptyList(),
    @field:Schema(nullable = true, example = "null")
    val error: ApiErrorPayload? = null,
    @field:Schema(example = "2026-03-09T12:00:00+09:00")
    val timestamp: String = "2026-03-09T12:00:00+09:00",
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
