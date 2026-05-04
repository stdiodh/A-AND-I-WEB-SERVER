package com.example.aandi_post_web_server.course.api.dto

import com.example.aandi_post_web_server.course.domain.model.EnrollmentStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import java.time.Instant

@Schema(
    description = "수강생 등록 요청(report 서버에 동기화된 사용자를 기준으로 등록)",
    example =
        """
        {
          "publicCode": "#OR402"
        }
        """,
)
data class EnrollCourseRequest(
    @field:NotBlank
    @field:Schema(description = "등록할 사용자의 publicCode(#이 없으면 자동으로 붙여 정규화합니다. report 서버 users 컬렉션에 존재해야 합니다.)", example = "#OR402")
    val publicCode: String,
)

@Schema(
    description = "수강 상태 변경 요청",
    example =
        """
        {
          "status": "BANNED",
          "banReason": "운영 정책 위반"
        }
        """,
)
data class UpdateEnrollmentRequest(
    @field:Schema(description = "변경할 수강 상태(ENABLED, BANNED)", example = "BANNED")
    val status: EnrollmentStatus,
    @field:Schema(description = "BANNED 사유", example = "운영 정책 위반")
    val banReason: String? = null,
)

@Schema(description = "수강 정보 응답")
data class CourseEnrollmentResponse(
    @field:Schema(description = "코스 ID", example = "course-1")
    val courseId: String,
    @field:Schema(description = "코스를 구분하는 슬러그", example = "fl-basic")
    val courseSlug: String,
    @field:Schema(description = "사용자 UUID", example = "user-1")
    val userId: String,
    @field:Schema(description = "유저 publicCode", example = "#OR402")
    val publicCode: String,
    @field:Schema(description = "사용자 이름", example = "string")
    val username: String,
    @field:Schema(description = "수강 상태", example = "ENABLED")
    val status: EnrollmentStatus,
    @field:Schema(description = "등록 시각(KST(Asia/Seoul))", example = "2026-03-01T09:00:00+09:00")
    val joinedAt: Instant,
    @field:Schema(description = "차단 시각(KST(Asia/Seoul))")
    val bannedAt: Instant?,
    @field:Schema(description = "차단 사유")
    val banReason: String?,
    @field:Schema(description = "최종 변경 시각(KST(Asia/Seoul))", example = "2026-03-02T09:00:00+09:00")
    val updatedAt: Instant,
)
