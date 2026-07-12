package com.example.aandi_post_web_server.course.application.mapper

import com.example.aandi_post_web_server.course.api.dto.CourseMetadataResponse
import com.example.aandi_post_web_server.course.api.dto.CourseResponse
import com.example.aandi_post_web_server.course.domain.model.CoursePhase
import com.example.aandi_post_web_server.course.domain.model.CourseStatus
import com.example.aandi_post_web_server.course.domain.model.CourseTrack
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseMetadata
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalDate

class CourseResponseMapperTest : StringSpec({
    "course와 metadata의 모든 응답 필드를 그대로 옮긴다" {
        course().toResponse() shouldBe CourseResponse(
            id = "course-1",
            slug = "backend-framework",
            fieldTag = CourseTrack.SP,
            startDate = LocalDate.of(2026, 3, 2),
            endDate = LocalDate.of(2026, 3, 30),
            metadata = CourseMetadataResponse(
                title = "Backend Framework",
                description = "Spring 심화 과정",
                phase = CoursePhase.FRAMEWORK,
                attributes = mapOf("language" to "Kotlin", "level" to 3, "optional" to null),
            ),
            status = CourseStatus.DRAFT,
            createdAt = Instant.parse("2026-03-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-03-01T00:00:01Z"),
        )
    }

    "저장되지 않아 id가 없는 course는 응답으로 변환하지 않는다" {
        shouldThrow<IllegalArgumentException> {
            course(id = null).toResponse()
        }
    }
})

private fun course(id: String? = "course-1"): Course =
    Course(
        id = id,
        slug = "backend-framework",
        fieldTag = CourseTrack.SP,
        startDate = LocalDate.of(2026, 3, 2),
        endDate = LocalDate.of(2026, 3, 30),
        metadata = CourseMetadata(
            title = "Backend Framework",
            description = "Spring 심화 과정",
            phase = CoursePhase.FRAMEWORK,
            attributes = mapOf("language" to "Kotlin", "level" to 3, "optional" to null),
        ),
        status = CourseStatus.DRAFT,
        createdAt = Instant.parse("2026-03-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-03-01T00:00:01Z"),
    )
