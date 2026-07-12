package com.example.aandi_post_web_server.course.application.mapper

import com.example.aandi_post_web_server.course.api.dto.CourseEnrollmentResponse
import com.example.aandi_post_web_server.course.domain.model.EnrollmentStatus
import com.example.aandi_post_web_server.course.entity.CourseEnrollment
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class CourseEnrollmentResponseMapperTest : StringSpec({
    "enrollment의 모든 응답 필드를 그대로 옮긴다" {
        val enrollment = CourseEnrollment(
            id = "enrollment-1",
            courseId = "course-1",
            userId = "user-1",
            publicCode = "#BE401",
            username = "backend-user",
            status = EnrollmentStatus.BANNED,
            joinedAt = Instant.parse("2026-03-01T00:00:00Z"),
            bannedAt = Instant.parse("2026-03-02T00:00:00Z"),
            banReason = "운영 정책 위반",
            updatedAt = Instant.parse("2026-03-02T00:00:01Z"),
        )

        enrollment.toResponse("backend-basic") shouldBe CourseEnrollmentResponse(
            courseId = "course-1",
            courseSlug = "backend-basic",
            userId = "user-1",
            publicCode = "#BE401",
            username = "backend-user",
            status = EnrollmentStatus.BANNED,
            joinedAt = Instant.parse("2026-03-01T00:00:00Z"),
            bannedAt = Instant.parse("2026-03-02T00:00:00Z"),
            banReason = "운영 정책 위반",
            updatedAt = Instant.parse("2026-03-02T00:00:01Z"),
        )
    }
})
