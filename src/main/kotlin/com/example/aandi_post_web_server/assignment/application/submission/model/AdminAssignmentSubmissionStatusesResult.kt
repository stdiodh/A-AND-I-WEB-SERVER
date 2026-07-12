package com.example.aandi_post_web_server.assignment.application.submission.model

import com.example.aandi_post_web_server.course.domain.model.EnrollmentStatus
import java.time.Instant

data class AdminAssignmentSubmissionStatusesResult(
    val assignmentId: String,
    val courseSlug: String,
    val totalEnrolled: Int,
    val submittedCount: Int,
    val notSubmittedCount: Int,
    val items: List<AdminAssignmentSubmissionStatusItemResult>,
)

data class AdminAssignmentSubmissionStatusItemResult(
    val userId: String,
    val publicCode: String,
    val username: String,
    val enrollmentStatus: EnrollmentStatus,
    val submitted: Boolean,
    val score: Int? = null,
    val passedCases: Int? = null,
    val totalCases: Int? = null,
    val completedAt: Instant? = null,
)
