package com.example.aandi_post_web_server.assignment.entity

import com.example.aandi_post_web_server.assignment.enum.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

data class AssignmentMetadata(
    val title: String,
    val difficulty: AssignmentDifficulty,
    val description: String,
    val timeLimitMinutes: Int,
    val learningGoals: List<String> = emptyList(),
    val attributes: Map<String, Any?> = emptyMap(),
)

@Document(collection = "assignments")
@CompoundIndex(name = "ux_assignment_course_week_order", def = "{'courseId': 1, 'weekNo': 1, 'orderInWeek': 1}", unique = true)
data class Assignment(
    @Id
    val id: String? = null,
    val courseId: String,
    val courseSlug: String = "",
    val createdBy: String,
    val weekNo: Int,
    val orderInWeek: Int,
    val startAt: Instant,
    val endAt: Instant,
    val metadata: AssignmentMetadata,
    val status: AssignmentStatus = AssignmentStatus.DRAFT,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val publishedAt: Instant? = null,
)
