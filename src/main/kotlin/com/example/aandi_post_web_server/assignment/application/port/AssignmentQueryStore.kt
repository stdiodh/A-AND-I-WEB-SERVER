package com.example.aandi_post_web_server.assignment.application.port

import com.example.aandi_post_web_server.assignment.entity.Assignment
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface AssignmentQueryStore {
    fun findById(assignmentId: String): Mono<Assignment>
    fun findByIdAndCourseId(assignmentId: String, courseId: String): Mono<Assignment>
    fun findAllByCourseId(courseId: String): Flux<Assignment>
    fun findAllByCourseIdAndWeekNo(courseId: String, weekNo: Int): Flux<Assignment>
}
