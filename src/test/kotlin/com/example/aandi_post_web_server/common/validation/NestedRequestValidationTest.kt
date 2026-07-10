package com.example.aandi_post_web_server.common.validation

import com.example.aandi_post_web_server.assignment.api.dto.AssignmentMetadataPayload
import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentLearningGoalRequest
import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentRequest
import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentRequirementRequest
import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentTestCaseRequest
import com.example.aandi_post_web_server.assignment.api.dto.UpdateAssignmentRequest
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentDifficulty
import com.example.aandi_post_web_server.course.api.dto.CourseMetadataPayload
import com.example.aandi_post_web_server.course.api.dto.CreateCourseRequest
import com.example.aandi_post_web_server.course.api.dto.UpdateCourseRequest
import com.example.aandi_post_web_server.course.domain.model.CourseTrack
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import jakarta.validation.Validation
import java.time.Instant
import java.time.LocalDate

class NestedRequestValidationTest : StringSpec({
    val validator = Validation.buildDefaultValidatorFactory().validator

    "course metadata 제약은 생성과 수정 요청에서 중첩 검증된다" {
        val createPaths = validator.validate(
            CreateCourseRequest(
                slug = "back-basic",
                fieldTag = CourseTrack.SP,
                startDate = LocalDate.parse("2026-03-01"),
                endDate = LocalDate.parse("2026-03-31"),
                metadata = CourseMetadataPayload(title = " "),
            )
        ).map { it.propertyPath.toString() }
        val updatePaths = validator.validate(
            UpdateCourseRequest(metadata = CourseMetadataPayload(title = " "))
        ).map { it.propertyPath.toString() }

        createPaths shouldContain "metadata.title"
        updatePaths shouldContain "metadata.title"
    }

    "assignment metadata의 목록 원소 제약은 중첩 검증된다" {
        val metadata = AssignmentMetadataPayload(
            title = "title",
            difficulty = AssignmentDifficulty.LOW,
            description = "description",
            requirements = listOf(
                CreateAssignmentRequirementRequest(sortOrder = 0, requirementText = " ")
            ),
            learningGoals = listOf(
                CreateAssignmentLearningGoalRequest(sortOrder = 0, learningGoalText = " ")
            ),
            testCases = listOf(
                CreateAssignmentTestCaseRequest(
                    seq = 0,
                    inputValues = emptyList(),
                    outputText = " ",
                )
            ),
        )
        val createPaths = validator.validate(
            CreateAssignmentRequest(
                weekNo = 1,
                orderInWeek = 1,
                startAt = Instant.parse("2026-03-01T00:00:00Z"),
                endAt = Instant.parse("2026-03-02T00:00:00Z"),
                metadata = metadata,
            )
        ).map { it.propertyPath.toString() }
        val updatePaths = validator.validate(
            UpdateAssignmentRequest(metadata = metadata)
        ).map { it.propertyPath.toString() }

        createPaths shouldContain "metadata.requirements[0].sortOrder"
        createPaths shouldContain "metadata.requirements[0].requirementText"
        createPaths shouldContain "metadata.learningGoals[0].sortOrder"
        createPaths shouldContain "metadata.learningGoals[0].learningGoalText"
        createPaths shouldContain "metadata.testCases[0].seq"
        createPaths shouldContain "metadata.testCases[0].outputText"
        updatePaths shouldContain "metadata.requirements[0].sortOrder"
    }
})
