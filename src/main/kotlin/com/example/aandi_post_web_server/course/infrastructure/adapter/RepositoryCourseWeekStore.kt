package com.example.aandi_post_web_server.course.infrastructure.adapter

import com.example.aandi_post_web_server.course.application.port.CourseWeekStore
import com.example.aandi_post_web_server.course.entity.CourseWeek
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseWeekRepository
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
class RepositoryCourseWeekStore(
    private val repository: CourseWeekRepository,
) : CourseWeekStore {
    override fun findAllByCourseId(courseId: String): Flux<CourseWeek> =
        repository.findAllByCourseId(courseId)

    override fun deleteAllByCourseId(courseId: String): Mono<Long> =
        repository.deleteAllByCourseId(courseId)
}
