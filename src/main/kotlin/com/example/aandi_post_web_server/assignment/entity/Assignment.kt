package com.example.aandi_post_web_server.assignment.entity

import com.example.aandi_post_web_server.assignment.domain.model.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTemplateLanguage
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.TypeAlias
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

data class AssignmentCodeTemplate(
    val language: AssignmentTemplateLanguage,
    val functionTemplate: String,
)

data class AssignmentMetadata(
    val title: String,
    val difficulty: AssignmentDifficulty,
    val description: String,
    val timeLimitMinutes: Int,
    val learningGoals: List<String> = emptyList(),
    val codeTemplates: List<AssignmentCodeTemplate> = emptyList(),
)

@Document(collection = "assignments")
@TypeAlias("assignment")
@CompoundIndexes(
    CompoundIndex(name = "ux_assignment_course_week_order", def = "{'courseId': 1, 'weekNo': 1, 'orderInWeek': 1}", unique = true),
    CompoundIndex(
        name = "ux_assignment_course_origin",
        def = "{'courseId': 1, 'originAssignmentId': 1}",
        unique = true,
        partialFilter = "{'originAssignmentId': {'\$exists': true, '\$ne': null}}",
    ),
    CompoundIndex(
        name = "ux_assignment_course_copy_fingerprint",
        def = "{'courseId': 1, 'copyFingerprint': 1}",
        unique = true,
        partialFilter = "{'copyFingerprint': {'\$exists': true, '\$ne': null}}",
    ),
)
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
    val originAssignmentId: String? = null,
    val originCourseSlug: String? = null,
    val copyFingerprint: String? = null,
)
