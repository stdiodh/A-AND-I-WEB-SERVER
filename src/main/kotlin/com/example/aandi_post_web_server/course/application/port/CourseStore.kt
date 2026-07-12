package com.example.aandi_post_web_server.course.application.port

import com.example.aandi_post_web_server.course.entity.Course
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface CourseStore {
    fun findAllNewestFirst(): Flux<Course>

    fun findAllByIds(courseIds: Collection<String>): Flux<Course>

    fun findById(courseId: String): Mono<Course>

    fun findBySlug(slug: String): Mono<Course>

    fun existsBySlug(slug: String): Mono<Boolean>

    fun save(course: Course): Mono<Course>

    fun deleteById(courseId: String): Mono<Void>
}
