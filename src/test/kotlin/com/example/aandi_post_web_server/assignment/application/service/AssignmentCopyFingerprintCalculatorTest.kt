package com.example.aandi_post_web_server.assignment.application.service

import com.example.aandi_post_web_server.assignment.domain.model.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTemplateLanguage
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseVisibility
import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.entity.AssignmentCodeTemplate
import com.example.aandi_post_web_server.assignment.entity.AssignmentMetadata
import com.example.aandi_post_web_server.assignment.entity.AssignmentRequirement
import com.example.aandi_post_web_server.assignment.entity.AssignmentTestCase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class AssignmentCopyFingerprintCalculatorTest : StringSpec({
    val calculator = AssignmentCopyFingerprintCalculator()

    "requirements 와 testCases 순서가 달라도 같은 내용이면 같은 fingerprint 를 만든다" {
        val assignment = fingerprintAssignment()
        val requirements = listOf(
            AssignmentRequirement(assignmentId = "assignment-1", sortOrder = 2, requirementText = "예외 처리"),
            AssignmentRequirement(assignmentId = "assignment-1", sortOrder = 1, requirementText = "함수 분리"),
        )
        val testCases = listOf(
            AssignmentTestCase(
                assignmentId = "assignment-1",
                seq = 2,
                inputValues = listOf("2 3"),
                outputText = "5",
                visibility = AssignmentTestCaseVisibility.HIDDEN,
            ),
            AssignmentTestCase(
                assignmentId = "assignment-1",
                seq = 1,
                inputValues = listOf("1 2"),
                outputText = "3",
                visibility = AssignmentTestCaseVisibility.PUBLIC,
            ),
        )

        calculator.calculate(assignment, requirements, testCases) shouldBe
            calculator.calculate(assignment, requirements.reversed(), testCases.reversed())
    }

    "fingerprint 대상 필드가 달라지면 다른 fingerprint 를 만든다" {
        val assignment = fingerprintAssignment()
        val requirements = listOf(AssignmentRequirement(assignmentId = "assignment-1", sortOrder = 1, requirementText = "함수 분리"))
        val testCases = listOf(
            AssignmentTestCase(
                assignmentId = "assignment-1",
                seq = 1,
                inputValues = listOf("1 2"),
                outputText = "3",
                visibility = AssignmentTestCaseVisibility.PUBLIC,
            )
        )
        val base = calculator.calculate(assignment, requirements, testCases)

        (calculator.calculate(assignment.copy(metadata = assignment.metadata.copy(title = "다른 제목")), requirements, testCases) == base) shouldBe false
        (calculator.calculate(assignment.copy(metadata = assignment.metadata.copy(description = "다른 설명")), requirements, testCases) == base) shouldBe false
        (calculator.calculate(assignment.copy(metadata = assignment.metadata.copy(difficulty = AssignmentDifficulty.HIGH)), requirements, testCases) == base) shouldBe false
        (calculator.calculate(assignment.copy(metadata = assignment.metadata.copy(timeLimitMinutes = 90)), requirements, testCases) == base) shouldBe false
        (calculator.calculate(assignment, requirements, testCases.map { it.copy(outputText = "4") }) == base) shouldBe false
        (calculator.calculate(
            assignment.copy(
                metadata = assignment.metadata.copy(
                    codeTemplates = listOf(
                        AssignmentCodeTemplate(AssignmentTemplateLanguage.KOTLIN, "fun solve() = 1")
                    )
                )
            ),
            requirements,
            testCases,
        ) == base) shouldBe false
    }

    "null 없이 empty list 는 일관된 fingerprint 를 만든다" {
        val assignment = fingerprintAssignment(
            metadata = AssignmentMetadata(
                title = "빈 목록",
                difficulty = AssignmentDifficulty.LOW,
                description = "설명",
                timeLimitMinutes = 30,
                learningGoals = emptyList(),
                codeTemplates = emptyList(),
            )
        )

        calculator.calculate(assignment, emptyList(), emptyList()) shouldBe
            calculator.calculate(assignment.copy(metadata = assignment.metadata.copy()), emptyList(), emptyList())
    }
})

private fun fingerprintAssignment(
    metadata: AssignmentMetadata = AssignmentMetadata(
        title = "두 수 더하기",
        difficulty = AssignmentDifficulty.MID,
        description = "두 수를 더하세요.",
        timeLimitMinutes = 60,
        learningGoals = listOf("입출력", "함수"),
        codeTemplates = listOf(
            AssignmentCodeTemplate(AssignmentTemplateLanguage.PYTHON, "def solve(): pass"),
            AssignmentCodeTemplate(AssignmentTemplateLanguage.KOTLIN, "fun solve() {}"),
        ),
    ),
): Assignment {
    val now = Instant.parse("2026-05-11T00:00:00Z")
    return Assignment(
        id = "assignment-1",
        courseId = "course-1",
        courseSlug = "source-course",
        createdBy = "admin",
        weekNo = 1,
        orderInWeek = 1,
        startAt = now,
        endAt = now.plusSeconds(3600),
        metadata = metadata,
        status = AssignmentStatus.PUBLISHED,
        createdAt = now,
        updatedAt = now,
        publishedAt = now,
    )
}
