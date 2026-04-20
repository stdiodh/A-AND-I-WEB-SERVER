package com.example.aandi_post_web_server.course.entity

import com.example.aandi_post_web_server.course.domain.model.CourseStatus
import com.example.aandi_post_web_server.course.domain.model.CourseTrack
import com.example.aandi_post_web_server.course.domain.model.CoursePhase
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.time.LocalDate

data class CourseMetadata(
    val title: String,
    val description: String? = null,
    val phase: CoursePhase? = null,
    val attributes: Map<String, Any?> = emptyMap(),
)

@Document(collection = "courses")
data class Course(
    @Id
    val id: String? = null,
    @Indexed(unique = true)
    val slug: String,
    val fieldTag: CourseTrack,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val metadata: CourseMetadata,
    val status: CourseStatus = CourseStatus.PUBLISHED,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
