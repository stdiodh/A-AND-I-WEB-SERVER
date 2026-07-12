package com.example.aandi_post_web_server.assignment.infrastructure.adapter

import com.example.aandi_post_web_server.assignment.application.port.AssignmentCopyStore
import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRepository
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class RepositoryAssignmentCopyStore(
    private val assignmentRepository: AssignmentRepository,
) : AssignmentCopyStore {
    override fun findById(assignmentId: String): Mono<Assignment> =
        assignmentRepository.findById(assignmentId)

    override fun save(assignment: Assignment): Mono<Assignment> =
        assignmentRepository.save(assignment)

    override fun findByCourseIdAndOriginAssignmentId(
        courseId: String,
        originAssignmentId: String,
    ): Mono<Assignment> =
        assignmentRepository.findByCourseIdAndOriginAssignmentId(courseId, originAssignmentId)

    override fun findByIdAndCourseId(assignmentId: String, courseId: String): Mono<Assignment> =
        assignmentRepository.findByIdAndCourseId(assignmentId, courseId)

    override fun findByCourseIdAndCopyFingerprint(courseId: String, copyFingerprint: String): Mono<Assignment> =
        assignmentRepository.findByCourseIdAndCopyFingerprint(courseId, copyFingerprint)

    override fun findByCourseIdAndWeekNoAndOrderInWeek(
        courseId: String,
        weekNo: Int,
        orderInWeek: Int,
    ): Mono<Assignment> =
        assignmentRepository.findByCourseIdAndWeekNoAndOrderInWeek(courseId, weekNo, orderInWeek)
}
