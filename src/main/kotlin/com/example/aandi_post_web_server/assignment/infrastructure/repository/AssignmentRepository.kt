package com.example.aandi_post_web_server.assignment.infrastructure.repository

import com.example.aandi_post_web_server.assignment.entity.Assignment
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface AssignmentRepository : ReactiveMongoRepository<Assignment, String> {
    fun findByIdAndCourseId(id: String, courseId: String): Mono<Assignment>
    fun findByCourseIdAndWeekNoAndOrderInWeek(courseId: String, weekNo: Int, orderInWeek: Int): Mono<Assignment>
    fun findByCourseIdAndOriginAssignmentId(courseId: String, originAssignmentId: String): Mono<Assignment>
    fun findByCourseIdAndCopyFingerprint(courseId: String, copyFingerprint: String): Mono<Assignment>
    fun findAllByCourseId(courseId: String): Flux<Assignment>
    fun findAllByCourseIdAndWeekNo(courseId: String, weekNo: Int): Flux<Assignment>
}
