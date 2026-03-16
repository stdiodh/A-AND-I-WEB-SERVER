package com.example.aandi_post_web_server.assignment.entity

import com.example.aandi_post_web_server.assignment.enum.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.enum.AssignmentProblemStep
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import com.example.aandi_post_web_server.assignment.enum.AssignmentTemplateLanguage
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

data class AssignmentProblemClassification(
    val algorithmStep: AssignmentProblemStep,
    val difficultyStep: Int,
)

data class AssignmentProblemDetail(
    val inputDescription: String? = null,
    val outputDescription: String? = null,
    val classification: AssignmentProblemClassification? = null,
)

data class AssignmentSubmissionGuide(
    val title: String = "문제 풀이 템플릿",
    val description: String = "제출 코드 상단에는 문제-해석-풀이 주석을 작성해야 합니다.",
    val commentSections: List<String> = listOf("문제", "해석", "풀이"),
)

data class AssignmentCodeTemplate(
    val language: AssignmentTemplateLanguage,
    val commentTemplate: String,
    val functionTemplate: String,
    val runnableTemplate: String,
)

data class AssignmentHiddenTestCase(
    val seq: Int,
    val inputText: String,
    val outputText: String,
)

data class AssignmentMetadata(
    val title: String,
    val difficulty: AssignmentDifficulty,
    val description: String,
    val timeLimitMinutes: Int,
    val learningGoals: List<String> = emptyList(),
    val problemDetail: AssignmentProblemDetail? = null,
    val submissionGuide: AssignmentSubmissionGuide? = null,
    val codeTemplates: List<AssignmentCodeTemplate> = emptyList(),
    val hiddenTestCases: List<AssignmentHiddenTestCase> = emptyList(),
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
