package com.example.aandi_post_web_server.assignment.infrastructure.adapter

import com.example.aandi_post_web_server.assignment.application.port.AssignmentQueryStore
import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRepository
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
class RepositoryAssignmentQueryStore(
    private val assignmentRepository: AssignmentRepository,
) : AssignmentQueryStore {
    override fun findById(assignmentId: String): Mono<Assignment> =
        assignmentRepository.findById(assignmentId)

    override fun findByIdAndCourseId(assignmentId: String, courseId: String): Mono<Assignment> =
        assignmentRepository.findByIdAndCourseId(assignmentId, courseId)

    override fun findAllByCourseId(courseId: String): Flux<Assignment> =
        assignmentRepository.findAllByCourseId(courseId)

    override fun findAllByCourseIdAndWeekNo(courseId: String, weekNo: Int): Flux<Assignment> =
        assignmentRepository.findAllByCourseIdAndWeekNo(courseId, weekNo)
}
