package com.example.aandi_post_web_server.course.api.dto

import com.example.aandi_post_web_server.course.domain.model.CoursePhase
import com.example.aandi_post_web_server.course.domain.model.CourseStatus
import com.example.aandi_post_web_server.course.domain.model.CourseTrack
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
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
