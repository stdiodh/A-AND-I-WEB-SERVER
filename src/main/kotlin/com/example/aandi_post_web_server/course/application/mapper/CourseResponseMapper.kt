package com.example.aandi_post_web_server.course.application.mapper

import com.example.aandi_post_web_server.course.api.dto.CourseMetadataResponse
import com.example.aandi_post_web_server.course.api.dto.CourseResponse
import com.example.aandi_post_web_server.course.entity.Course

internal fun Course.toResponse(): CourseResponse =
    CourseResponse(
        id = requireNotNull(id),
        slug = slug,
        fieldTag = fieldTag,
        startDate = startDate,
        endDate = endDate,
        metadata = CourseMetadataResponse(
            title = metadata.title,
            description = metadata.description,
            phase = metadata.phase,
            attributes = metadata.attributes,
        ),
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
