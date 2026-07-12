package com.example.aandi_post_web_server.course.application.port

import com.example.aandi_post_web_server.course.entity.CourseWeek
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface CourseWeekStore {
    fun findAllByCourseId(courseId: String): Flux<CourseWeek>

    fun deleteAllByCourseId(courseId: String): Mono<Long>
}
