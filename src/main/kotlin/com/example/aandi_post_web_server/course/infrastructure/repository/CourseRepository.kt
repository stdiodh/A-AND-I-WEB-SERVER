package com.example.aandi_post_web_server.course.infrastructure.repository

import com.example.aandi_post_web_server.course.entity.Course
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Mono

interface CourseRepository : ReactiveMongoRepository<Course, String> {
    fun findBySlug(slug: String): Mono<Course>
    fun existsBySlug(slug: String): Mono<Boolean>
}
