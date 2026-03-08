package com.example.aandi_post_web_server.course.repository

import com.example.aandi_post_web_server.course.entity.CourseEnrollment
import com.example.aandi_post_web_server.course.enum.EnrollmentStatus
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface CourseEnrollmentRepository : ReactiveMongoRepository<CourseEnrollment, String> {
    fun findByCourseIdAndUserId(courseId: String, userId: String): Mono<CourseEnrollment>
    fun findAllByCourseId(courseId: String): Flux<CourseEnrollment>
    fun findAllByCourseIdAndStatus(courseId: String, status: EnrollmentStatus): Flux<CourseEnrollment>
    fun findAllByUserIdAndStatus(userId: String, status: EnrollmentStatus): Flux<CourseEnrollment>
    fun deleteAllByCourseId(courseId: String): Mono<Long>
}
