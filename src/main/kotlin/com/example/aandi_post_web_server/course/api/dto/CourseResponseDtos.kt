package com.example.aandi_post_web_server.course.api.dto

import com.example.aandi_post_web_server.course.domain.model.CoursePhase
import com.example.aandi_post_web_server.course.domain.model.CourseStatus
import com.example.aandi_post_web_server.course.domain.model.CourseTrack
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.time.LocalDate

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
