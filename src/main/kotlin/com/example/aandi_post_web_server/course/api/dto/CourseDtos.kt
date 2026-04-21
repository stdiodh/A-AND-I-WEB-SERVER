package com.example.aandi_post_web_server.course.api.dto

import com.example.aandi_post_web_server.course.domain.model.CoursePhase
import com.example.aandi_post_web_server.course.domain.model.CourseStatus
import com.example.aandi_post_web_server.course.domain.model.CourseTrack
import com.example.aandi_post_web_server.course.domain.model.EnrollmentStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.time.LocalDate

@Schema(description = "코스 메타데이터")
data class CourseMetadataPayload(
    @field:NotBlank
    @field:Schema(description = "코스 이름", example = "FL 기초")
    val title: String,
    @field:Schema(description = "코스 설명", example = "프론트엔드 트랙 기초 과정")
    val description: String? = null,
    @field:Schema(description = "과정 단계", example = "BASIC")
    val phase: CoursePhase? = null,
    @field:Schema(description = "확장 메타데이터")
    val attributes: Map<String, Any?> = emptyMap(),
)

@Schema(
    description = "코스 생성 요청",
    example =
        """
        {
          "slug": "3rd-cs-basic",
          "fieldTag": "NO",
          "startDate": "2026-03-02",
          "endDate": "2026-03-30",
          "metadata": {
            "title": "3rd_cs_basic",
            "description": "공통 CS 과정",
            "phase": "CS",
            "attributes": {}
          }
        }
        """,
)
data class CreateCourseRequest(
    @field:NotBlank
    @field:Schema(description = "코스를 구분하는 슬러그", example = "3rd-cs-basic")
    val slug: String,
    @field:NotNull
    @field:Schema(description = "트랙 태그(NO=공통, FL=프론트, SP=서버)", example = "NO")
    val fieldTag: CourseTrack,
    @field:NotNull
    @field:Schema(description = "과정 시작일", example = "2026-03-02")
    val startDate: LocalDate,
    @field:NotNull
    @field:Schema(description = "과정 종료일", example = "2026-03-30")
    val endDate: LocalDate,
    @field:NotNull
    @field:Schema(description = "코스 메타데이터")
    val metadata: CourseMetadataPayload,
)

@Schema(
    description = "코스 수정 요청",
    example =
        """
        {
          "fieldTag": "NO",
          "startDate": "2026-03-09",
          "endDate": "2026-04-06",
          "metadata": {
            "title": "3rd_cs_basic",
            "description": "공통 CS 과정",
            "phase": "CS",
            "attributes": {}
          },
          "status": "PUBLISHED"
        }
        """,
)
data class UpdateCourseRequest(
    @field:Schema(description = "트랙 태그(NO=공통, FL=프론트, SP=서버)", example = "NO")
    val fieldTag: CourseTrack? = null,
    @field:Schema(description = "과정 시작일", example = "2026-03-02")
    val startDate: LocalDate? = null,
    @field:Schema(description = "과정 종료일", example = "2026-03-30")
    val endDate: LocalDate? = null,
    @field:Schema(description = "코스 메타데이터")
    val metadata: CourseMetadataPayload? = null,
    @field:Schema(description = "코스 상태", example = "PUBLISHED")
    val status: CourseStatus? = null,
)

@Schema(description = "코스 메타데이터 응답")
data class CourseMetadataResponse(
    @field:Schema(description = "코스 이름", example = "FL 기초")
    val title: String,
    @field:Schema(description = "코스 설명", example = "프론트엔드 트랙 기초 과정")
    val description: String? = null,
    @field:Schema(description = "과정 단계", example = "BASIC")
    val phase: CoursePhase? = null,
    @field:Schema(description = "확장 메타데이터")
    val attributes: Map<String, Any?> = emptyMap(),
)

@Schema(description = "코스 응답")
data class CourseResponse(
    @field:Schema(description = "코스 ID", example = "course-1")
    val id: String,
    @field:Schema(description = "코스를 구분하는 슬러그", example = "fl-basic")
    val slug: String,
    @field:Schema(description = "트랙 태그(NO=공통, FL=프론트, SP=서버)", example = "NO")
    val fieldTag: CourseTrack,
    @field:Schema(description = "과정 시작일", example = "2026-03-02")
    val startDate: LocalDate,
    @field:Schema(description = "과정 종료일", example = "2026-03-30")
    val endDate: LocalDate,
    @field:Schema(description = "코스 메타데이터")
    val metadata: CourseMetadataResponse,
    @field:Schema(description = "코스 상태", example = "PUBLISHED")
    val status: CourseStatus,
    @field:Schema(description = "생성 시각(KST(Asia/Seoul))", example = "2026-03-01T09:00:00+09:00")
    val createdAt: Instant,
    @field:Schema(description = "수정 시각(KST(Asia/Seoul))", example = "2026-03-01T09:00:00+09:00")
    val updatedAt: Instant,
)

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

@Schema(description = "주차 응답")
data class CourseWeekResponse(
    @field:Schema(description = "주차 ID", example = "week-1")
    val id: String,
    @field:Schema(description = "주차 번호", example = "1")
    val weekNo: Int,
    @field:Schema(description = "주차 제목", example = "1주차 - Kotlin 기본")
    val title: String,
    @field:Schema(description = "시작일")
    val startDate: LocalDate?,
    @field:Schema(description = "종료일")
    val endDate: LocalDate?,
    @field:Schema(description = "생성 시각(KST(Asia/Seoul))", example = "2026-03-01T09:00:00+09:00")
    val createdAt: Instant,
    @field:Schema(description = "수정 시각(KST(Asia/Seoul))", example = "2026-03-01T09:00:00+09:00")
    val updatedAt: Instant,
)
