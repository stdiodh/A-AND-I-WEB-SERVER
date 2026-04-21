package com.example.aandi_post_web_server.course.infrastructure.repository

import com.example.aandi_post_web_server.course.entity.CourseWeek
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface CourseWeekRepository : ReactiveMongoRepository<CourseWeek, String> {
    fun findByCourseIdAndWeekNo(courseId: String, weekNo: Int): Mono<CourseWeek>
    fun findAllByCourseId(courseId: String): Flux<CourseWeek>
    fun deleteAllByCourseId(courseId: String): Mono<Long>
}
