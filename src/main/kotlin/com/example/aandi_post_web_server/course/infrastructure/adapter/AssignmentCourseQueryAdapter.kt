package com.example.aandi_post_web_server.course.infrastructure.adapter

import com.example.aandi_post_web_server.assignment.application.port.AssignmentCourseQueryPort
import com.example.aandi_post_web_server.assignment.application.port.AssignmentCourseReference
import com.example.aandi_post_web_server.course.domain.model.CourseId
import com.example.aandi_post_web_server.course.domain.model.CourseSlug
import com.example.aandi_post_web_server.course.domain.model.EnrollmentStatus
import com.example.aandi_post_web_server.course.domain.model.UserId
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseEnrollmentRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseRepository
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class AssignmentCourseQueryAdapter(
    private val courseRepository: CourseRepository,
    private val courseEnrollmentRepository: CourseEnrollmentRepository,
) : AssignmentCourseQueryPort {
    override fun findBySlug(slug: CourseSlug): Mono<AssignmentCourseReference> =
        courseRepository.findBySlug(slug.value)
            .map { course ->
                AssignmentCourseReference(
                    id = course.id,
                    slug = course.slug,
                )
            }

    override fun isEnrollmentEnabled(
        courseId: CourseId,
        userId: UserId,
    ): Mono<Boolean> =
        courseEnrollmentRepository.findByCourseIdAndUserId(courseId.value, userId.value)
            .map { enrollment -> enrollment.status == EnrollmentStatus.ENABLED }
            .defaultIfEmpty(false)
}
