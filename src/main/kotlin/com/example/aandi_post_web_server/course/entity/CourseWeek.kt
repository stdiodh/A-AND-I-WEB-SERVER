package com.example.aandi_post_web_server.course.entity

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.TypeAlias
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.time.LocalDate

@Document(collection = "course_weeks")
@TypeAlias("courseWeek")
@CompoundIndex(name = "ux_course_week", def = "{'courseId': 1, 'weekNo': 1}", unique = true)
data class CourseWeek(
    @Id
    val id: String? = null,
    val courseId: String,
    val weekNo: Int,
    val title: String,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
