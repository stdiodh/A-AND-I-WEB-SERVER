package com.example.aandi_post_web_server.assignment.application.port

import com.example.aandi_post_web_server.course.domain.model.CourseId
import com.example.aandi_post_web_server.course.domain.model.CourseSlug
import com.example.aandi_post_web_server.course.domain.model.UserId
import reactor.core.publisher.Mono

interface AssignmentCourseQueryPort {
    fun findBySlug(slug: CourseSlug): Mono<AssignmentCourseReference>

    fun isEnrollmentEnabled(
        courseId: CourseId,
        userId: UserId,
    ): Mono<Boolean>
}
