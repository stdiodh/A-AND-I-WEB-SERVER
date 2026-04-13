package com.example.aandi_post_web_server.assignment.submission.entity

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "assignment_submission_statuses")
@CompoundIndex(name = "ux_assignment_submission_status_assignment_public_code", def = "{'assignmentId': 1, 'publicCode': 1}", unique = true)
data class AssignmentSubmissionStatusProjection(
    @Id
    val id: String? = null,
    @Indexed
    val assignmentId: String,
    @Indexed
    val publicCode: String,
    val submitted: Boolean = true,
    val firstCompletedAt: Instant,
    val lastCompletedAt: Instant,
    val latestScore: Int,
    val latestPassedCases: Int,
    val latestTotalCases: Int,
    val lastEventTimestamp: Instant,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    @Version
    val version: Long? = null,
)
