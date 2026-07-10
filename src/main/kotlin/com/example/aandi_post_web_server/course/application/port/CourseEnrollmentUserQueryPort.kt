package com.example.aandi_post_web_server.course.application.port

import reactor.core.publisher.Mono

data class CourseEnrollmentUserReference(
    val id: String,
    val publicCode: String,
    val username: String,
    val role: String,
)

interface CourseEnrollmentUserQueryPort {
    fun findByPublicCode(publicCode: String): Mono<CourseEnrollmentUserReference>
}
