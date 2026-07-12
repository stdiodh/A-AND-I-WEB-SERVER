package com.example.aandi_post_web_server.course.infrastructure.adapter

import com.example.aandi_post_web_server.course.application.port.CourseStore
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
class RepositoryCourseStore(
    private val repository: CourseRepository,
) : CourseStore {
    override fun findAllNewestFirst(): Flux<Course> =
        repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))

    override fun findAllByIds(courseIds: Collection<String>): Flux<Course> =
        repository.findAllById(courseIds)

    override fun findById(courseId: String): Mono<Course> =
        repository.findById(courseId)

    override fun findBySlug(slug: String): Mono<Course> =
        repository.findBySlug(slug)

    override fun existsBySlug(slug: String): Mono<Boolean> =
        repository.existsBySlug(slug)

    override fun save(course: Course): Mono<Course> =
        repository.save(course)

    override fun deleteById(courseId: String): Mono<Void> =
        repository.deleteById(courseId)
}
