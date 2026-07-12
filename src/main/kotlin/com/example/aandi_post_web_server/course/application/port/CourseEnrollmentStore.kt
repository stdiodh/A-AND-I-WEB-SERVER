package com.example.aandi_post_web_server.course.application.port

import com.example.aandi_post_web_server.course.entity.CourseEnrollment
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface CourseEnrollmentStore {
    fun findByCourseIdAndUserId(courseId: String, userId: String): Mono<CourseEnrollment>

    fun findAllByCourseId(courseId: String): Flux<CourseEnrollment>

    fun findAllEnabledByUserId(userId: String): Flux<CourseEnrollment>

    fun save(enrollment: CourseEnrollment): Mono<CourseEnrollment>

    fun delete(enrollment: CourseEnrollment): Mono<Void>

    fun deleteAllByCourseId(courseId: String): Mono<Long>
}
