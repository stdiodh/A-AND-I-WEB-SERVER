package com.example.aandi_post_web_server.course.application.service

import com.example.aandi_post_web_server.assignment.domain.model.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTemplateLanguage
import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.entity.AssignmentCodeTemplate
import com.example.aandi_post_web_server.assignment.entity.AssignmentRequirement
import com.example.aandi_post_web_server.assignment.entity.AssignmentTestCase
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRequirementRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentTestCaseRepository
import com.example.aandi_post_web_server.course.domain.model.CourseTrack
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseMetadata
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseEnrollmentRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseWeekRepository
import org.mockito.Mockito
import reactor.core.publisher.Flux
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal object CourseQueryServiceTestData {
    val fixedNow: Instant = Instant.parse("2026-03-15T00:00:00Z")

    fun queryAssignmentId(index: Int): String =
        "10000000-0000-4000-8000-${index.toString().padStart(12, '0')}"

    fun queryAssignment(
        id: String,
        courseId: String,
    ): Assignment {
        val now = Instant.parse("2026-02-20T00:00:00Z")
        return Assignment(
            id = id,
            courseId = courseId,
            createdBy = "admin",
            weekNo = 1,
            orderInWeek = 1,
            startAt = now,
            endAt = now.plusSeconds(3600),
            metadata = com.example.aandi_post_web_server.assignment.entity.AssignmentMetadata(
                title = "배포 조회 테스트 과제",
                difficulty = AssignmentDifficulty.MID,
                description = "content",
                timeLimitMinutes = 60,
                codeTemplates = listOf(
                    AssignmentCodeTemplate(
                        language = AssignmentTemplateLanguage.KOTLIN,
                        functionTemplate = "/* ... */\nfun solution(): String { ... }",
                    ),
                    AssignmentCodeTemplate(
                        language = AssignmentTemplateLanguage.DART,
                        functionTemplate = "/* ... */\nString solution() { ... }",
                    ),
                ),
            ),
            status = AssignmentStatus.PUBLISHED,
            createdAt = now,
            updatedAt = now,
            publishedAt = now,
        )
    }

    fun queryCourse(
        id: String,
        slug: String,
        title: String,
        fieldTag: CourseTrack = CourseTrack.FL,
        createdAt: Instant = Instant.parse("2026-02-20T00:00:00Z"),
        updatedAt: Instant = createdAt,
    ): Course = Course(
        id = id,
        slug = slug,
        fieldTag = fieldTag,
        startDate = LocalDate.of(2026, 3, 1),
        endDate = LocalDate.of(2026, 3, 30),
        metadata = CourseMetadata(
            title = title,
            description = null,
            phase = null,
            attributes = emptyMap(),
        ),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

internal class CourseQueryServiceTestFixture(
    clock: Clock = Clock.fixed(CourseQueryServiceTestData.fixedNow, ZoneOffset.UTC),
) {
    val courseRepository: CourseRepository = Mockito.mock(CourseRepository::class.java)
    val courseEnrollmentRepository: CourseEnrollmentRepository = Mockito.mock(CourseEnrollmentRepository::class.java)
    val courseWeekRepository: CourseWeekRepository = Mockito.mock(CourseWeekRepository::class.java)
    val assignmentRepository: AssignmentRepository = Mockito.mock(AssignmentRepository::class.java)
    val assignmentRequirementRepository: AssignmentRequirementRepository = Mockito.mock(AssignmentRequirementRepository::class.java)
    val assignmentTestCaseRepository: AssignmentTestCaseRepository = Mockito.mock(AssignmentTestCaseRepository::class.java)

    init {
        stubBatchChildren()
    }

    val service = CourseQueryService(
        courseRepository = courseRepository,
        courseEnrollmentRepository = courseEnrollmentRepository,
        courseWeekRepository = courseWeekRepository,
        assignmentRepository = assignmentRepository,
        assignmentRequirementRepository = assignmentRequirementRepository,
        assignmentTestCaseRepository = assignmentTestCaseRepository,
        clock = clock,
    )

    fun stubBatchChildren(
        requirements: List<AssignmentRequirement> = emptyList(),
        examples: List<AssignmentTestCase> = emptyList(),
    ) {
        Mockito.`when`(assignmentRequirementRepository.findAllByAssignmentIdIn(Mockito.anyCollection()))
            .thenReturn(Flux.fromIterable(requirements))
        Mockito.`when`(assignmentTestCaseRepository.findAllByAssignmentIdIn(Mockito.anyCollection()))
            .thenReturn(Flux.fromIterable(examples))
        Mockito.clearInvocations(assignmentRequirementRepository, assignmentTestCaseRepository)
    }
}
