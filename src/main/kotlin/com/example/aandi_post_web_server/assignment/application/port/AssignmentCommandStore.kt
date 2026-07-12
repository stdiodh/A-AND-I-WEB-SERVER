package com.example.aandi_post_web_server.assignment.application.port

import com.example.aandi_post_web_server.assignment.entity.Assignment
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface AssignmentCommandStore {
    fun save(assignment: Assignment): Mono<Assignment>
    fun findByIdAndCourseId(assignmentId: String, courseId: String): Mono<Assignment>
    fun findAllByCourseId(courseId: String): Flux<Assignment>
    fun findByCourseIdAndWeekNoAndOrderInWeek(
        courseId: String,
        weekNo: Int,
        orderInWeek: Int,
    ): Mono<Assignment>
}
