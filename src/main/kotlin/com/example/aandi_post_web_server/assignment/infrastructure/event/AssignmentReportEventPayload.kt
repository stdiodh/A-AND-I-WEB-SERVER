package com.example.aandi_post_web_server.assignment.infrastructure.event

import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import com.example.aandi_post_web_server.assignment.entity.Assignment
import java.time.Instant

data class AssignmentReportEventPayload(
    val eventType: AssignmentReportEventType,
    val assignmentId: String,
    val courseSlug: String,
    val title: String,
    val status: AssignmentStatus,
    val startAt: Instant,
    val endAt: Instant,
    val publishedAt: Instant?,
    val problemId: String,
) {
    companion object {
        fun from(
            eventType: AssignmentReportEventType,
            assignment: Assignment,
            status: AssignmentStatus,
            publishedAt: Instant?,
        ): AssignmentReportEventPayload {
            val assignmentId = requireNotNull(assignment.id)
            return AssignmentReportEventPayload(
                eventType = eventType,
                assignmentId = assignmentId,
                courseSlug = assignment.courseSlug,
                title = assignment.metadata.title,
                status = status,
                startAt = assignment.startAt,
                endAt = assignment.endAt,
                publishedAt = publishedAt,
                problemId = assignmentId,
            )
        }
    }
}

enum class AssignmentReportEventType {
    ASSIGNMENT_CREATED,
    ASSIGNMENT_PUBLISHED,
    ASSIGNMENT_UPDATED,
    ASSIGNMENT_DELETED,
    ASSIGNMENT_UNPUBLISHED,
}
