package com.example.aandi_post_web_server.submission.entity

import com.example.aandi_post_web_server.submission.enum.AssignmentSubmissionLanguage
import com.example.aandi_post_web_server.submission.enum.AssignmentSubmissionStatus
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "assignment_submissions")
@CompoundIndex(name = "idx_submission_assignment_user_created", def = "{'assignmentId': 1, 'userId': 1, 'createdAt': -1}")
data class AssignmentSubmission(
    @Id
    val id: String? = null,
    val assignmentId: String,
    val courseId: String,
    val courseSlug: String,
    val userId: String,
    val language: AssignmentSubmissionLanguage,
    val status: AssignmentSubmissionStatus = AssignmentSubmissionStatus.PENDING,
    val realtimeFeedback: Boolean = true,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val completedAt: Instant? = null,
)
