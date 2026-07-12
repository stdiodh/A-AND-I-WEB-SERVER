package com.example.aandi_post_web_server.assignment.application.port

import com.example.aandi_post_web_server.assignment.entity.Assignment
import reactor.core.publisher.Mono

interface AssignmentCopyStore {
    fun findById(assignmentId: String): Mono<Assignment>
    fun save(assignment: Assignment): Mono<Assignment>
    fun findByCourseIdAndOriginAssignmentId(courseId: String, originAssignmentId: String): Mono<Assignment>
    fun findByIdAndCourseId(assignmentId: String, courseId: String): Mono<Assignment>
    fun findByCourseIdAndCopyFingerprint(courseId: String, copyFingerprint: String): Mono<Assignment>
    fun findByCourseIdAndWeekNoAndOrderInWeek(
        courseId: String,
        weekNo: Int,
        orderInWeek: Int,
    ): Mono<Assignment>
}
