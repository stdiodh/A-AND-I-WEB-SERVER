package com.example.aandi_post_web_server.course.infrastructure.adapter

import com.example.aandi_post_web_server.course.application.port.CourseEnrollmentStore
import com.example.aandi_post_web_server.course.domain.model.EnrollmentStatus
import com.example.aandi_post_web_server.course.entity.CourseEnrollment
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseEnrollmentRepository
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
class RepositoryCourseEnrollmentStore(
    private val repository: CourseEnrollmentRepository,
) : CourseEnrollmentStore {
    override fun findByCourseIdAndUserId(courseId: String, userId: String): Mono<CourseEnrollment> =
        repository.findByCourseIdAndUserId(courseId, userId)

    override fun findAllByCourseId(courseId: String): Flux<CourseEnrollment> =
        repository.findAllByCourseId(courseId)

    override fun findAllEnabledByUserId(userId: String): Flux<CourseEnrollment> =
        repository.findAllByUserIdAndStatus(userId, EnrollmentStatus.ENABLED)

    override fun save(enrollment: CourseEnrollment): Mono<CourseEnrollment> =
        repository.save(enrollment)

    override fun delete(enrollment: CourseEnrollment): Mono<Void> =
        repository.delete(enrollment)

    override fun deleteAllByCourseId(courseId: String): Mono<Long> =
        repository.deleteAllByCourseId(courseId)
}
