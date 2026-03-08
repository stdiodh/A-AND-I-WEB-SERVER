package com.example.aandi_post_web_server.course.dtos

import com.example.aandi_post_web_server.course.enum.CoursePhase
import com.example.aandi_post_web_server.course.enum.CourseStatus
import com.example.aandi_post_web_server.course.enum.CourseTrack
import com.example.aandi_post_web_server.course.enum.EnrollmentStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
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

@Schema(description = "코스 생성 요청")
data class CreateCourseRequest(
    @field:NotBlank
    @field:Schema(description = "코스 슬러그(고유값)", example = "fl-basic")
    val slug: String,
    @field:NotNull
    @field:Schema(description = "분야 태그", example = "FL")
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

@Schema(description = "코스 수정 요청")
data class UpdateCourseRequest(
    @field:Schema(description = "분야 태그", example = "SP")
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
    @field:Schema(description = "코스 슬러그", example = "fl-basic")
    val slug: String,
    @field:Schema(description = "분야 태그", example = "FL")
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

@Schema(description = "수강생 등록 요청")
data class EnrollCourseRequest(
    @field:NotBlank
    @field:Schema(description = "유저 식별값", example = "user-1")
    val userId: String,
)

@Schema(description = "수강 상태 변경 요청")
data class UpdateEnrollmentRequest(
    @field:Schema(description = "변경할 수강 상태", example = "BANNED")
    val status: EnrollmentStatus,
    @field:Schema(description = "BANNED 사유", example = "운영 정책 위반")
    val banReason: String? = null,
)

@Schema(description = "수강 정보 응답")
data class CourseEnrollmentResponse(
    @field:Schema(description = "수강 ID", example = "enroll-1")
    val id: String,
    @field:Schema(description = "유저 식별값", example = "user-1")
    val userId: String,
    @field:Schema(description = "수강 상태", example = "ENROLLED")
    val status: EnrollmentStatus,
    @field:Schema(description = "등록 시각(KST(Asia/Seoul))", example = "2026-03-01T09:00:00+09:00")
    val joinedAt: Instant,
    @field:Schema(description = "중도 포기 시각(KST(Asia/Seoul))")
    val droppedAt: Instant?,
    @field:Schema(description = "강제 제외 시각(KST(Asia/Seoul))")
    val bannedAt: Instant?,
    @field:Schema(description = "강제 제외 사유")
    val banReason: String?,
    @field:Schema(description = "최종 변경 시각(KST(Asia/Seoul))", example = "2026-03-02T09:00:00+09:00")
    val updatedAt: Instant,
)

@Schema(description = "주차 생성/수정 요청")
data class CreateCourseWeekRequest(
    @field:Min(1)
    @field:Schema(description = "주차 번호", example = "1")
    val weekNo: Int,
    @field:NotBlank
    @field:Schema(description = "주차 제목", example = "1주차 - Kotlin 기본")
    val title: String,
    @field:Schema(description = "시작일", example = "2026-03-02")
    val startDate: LocalDate? = null,
    @field:Schema(description = "종료일", example = "2026-03-08")
    val endDate: LocalDate? = null,
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
