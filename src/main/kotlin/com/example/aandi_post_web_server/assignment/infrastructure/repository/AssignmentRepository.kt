package com.example.aandi_post_web_server.assignment.infrastructure.repository

import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface AssignmentRepository : ReactiveMongoRepository<Assignment, String> {
    fun findByIdAndCourseId(id: String, courseId: String): Mono<Assignment>
    fun findByCourseIdAndWeekNoAndOrderInWeek(courseId: String, weekNo: Int, orderInWeek: Int): Mono<Assignment>
    fun findByCourseIdAndOriginAssignmentId(courseId: String, originAssignmentId: String): Mono<Assignment>
    fun findByCourseIdAndCopyFingerprint(courseId: String, copyFingerprint: String): Mono<Assignment>
    fun findAllByIdIn(ids: Collection<String>): Flux<Assignment>
    fun findAllByCourseId(courseId: String): Flux<Assignment>
    fun findAllByCourseIdAndStatus(courseId: String, status: AssignmentStatus): Flux<Assignment>
    fun findAllByCourseIdAndWeekNo(courseId: String, weekNo: Int): Flux<Assignment>
    fun findAllByCourseIdAndWeekNoAndStatus(courseId: String, weekNo: Int, status: AssignmentStatus): Flux<Assignment>
}
