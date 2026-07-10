package com.example.aandi_post_web_server.assignment.application.port

import com.example.aandi_post_web_server.course.domain.model.CourseId
import com.example.aandi_post_web_server.course.domain.model.CourseSlug
import com.example.aandi_post_web_server.course.domain.model.WeekNo
import reactor.core.publisher.Mono
import java.time.Instant

data class AssignmentCourseReference(
    val id: String?,
    val slug: String,
)

interface AssignmentCoursePort {
    fun findBySlug(slug: CourseSlug): Mono<AssignmentCourseReference>

    fun findSlugById(courseId: String): Mono<String>

    fun ensureWeekExistsOrCreate(
        courseId: CourseId,
        weekNo: WeekNo,
        startAt: Instant,
        endAt: Instant,
    ): Mono<Void>
}
