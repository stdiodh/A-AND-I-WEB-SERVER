package com.example.aandi_post_web_server.course.application.mapper

import com.example.aandi_post_web_server.course.api.dto.CourseEnrollmentResponse
import com.example.aandi_post_web_server.course.entity.CourseEnrollment

internal fun CourseEnrollment.toResponse(courseSlug: String): CourseEnrollmentResponse =
    CourseEnrollmentResponse(
        courseId = courseId,
        courseSlug = courseSlug,
        userId = userId,
        publicCode = publicCode,
        username = username,
        status = status,
        joinedAt = joinedAt,
        bannedAt = bannedAt,
        banReason = banReason,
        updatedAt = updatedAt,
    )
