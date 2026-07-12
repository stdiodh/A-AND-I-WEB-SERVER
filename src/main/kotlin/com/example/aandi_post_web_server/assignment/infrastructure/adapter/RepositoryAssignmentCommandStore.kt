package com.example.aandi_post_web_server.assignment.infrastructure.adapter

import com.example.aandi_post_web_server.assignment.application.port.AssignmentCommandStore
import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRepository
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
class RepositoryAssignmentCommandStore(
    private val assignmentRepository: AssignmentRepository,
) : AssignmentCommandStore {
    override fun save(assignment: Assignment): Mono<Assignment> =
        assignmentRepository.save(assignment)

    override fun findByIdAndCourseId(assignmentId: String, courseId: String): Mono<Assignment> =
        assignmentRepository.findByIdAndCourseId(assignmentId, courseId)

    override fun findAllByCourseId(courseId: String): Flux<Assignment> =
        assignmentRepository.findAllByCourseId(courseId)

    override fun findByCourseIdAndWeekNoAndOrderInWeek(
        courseId: String,
        weekNo: Int,
        orderInWeek: Int,
    ): Mono<Assignment> =
        assignmentRepository.findByCourseIdAndWeekNoAndOrderInWeek(courseId, weekNo, orderInWeek)
}
