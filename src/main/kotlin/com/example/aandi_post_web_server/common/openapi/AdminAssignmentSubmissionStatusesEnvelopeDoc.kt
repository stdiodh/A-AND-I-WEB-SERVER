package com.example.aandi_post_web_server.common.openapi

import com.example.aandi_post_web_server.assignment.v2.dto.AdminAssignmentSubmissionStatusesResponse
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "관리자용 과제 제출 현황 성공 응답. 코스 수강생 전체를 기준으로 projection 존재 여부를 합쳐 제출/미제출 현황을 반환합니다.",
    example = """{"success":true,"data":{"assignmentId":"7fbe8f62-9d89-4c74-b1e4-3ad3b9d7f001","courseSlug":"back-basic","totalEnrolled":3,"submittedCount":2,"notSubmittedCount":1,"items":[{"userId":"user-1","publicCode":"A00123","username":"alice","enrollmentStatus":"ENABLED","submitted":true,"score":90,"passedCases":9,"totalCases":10,"completedAt":"2026-04-09T02:15:30.123Z"},{"userId":"user-2","publicCode":"A00124","username":"bob","enrollmentStatus":"ENABLED","submitted":false,"score":null,"passedCases":null,"totalCases":null,"completedAt":null}]},"error":null,"timestamp":"2026-04-13T18:00:00Z"}""",
)
data class AdminAssignmentSubmissionStatusesEnvelopeDoc(
    @field:Schema(example = "true")
    val success: Boolean = true,
    val data: AdminAssignmentSubmissionStatusesResponse? = null,
    @field:Schema(nullable = true, example = "null")
    val error: ApiErrorPayload? = null,
    @field:Schema(example = "2026-04-13T18:00:00Z")
    val timestamp: String = "2026-04-13T18:00:00Z",
)
