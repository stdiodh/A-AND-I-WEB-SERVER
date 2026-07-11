package com.example.aandi_post_web_server.assignment.application.service

import com.example.aandi_post_web_server.assignment.application.port.AssignmentCourseQueryPort
import com.example.aandi_post_web_server.assignment.application.port.AssignmentCourseReference
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
import com.example.aandi_post_web_server.course.domain.model.CourseId
import com.example.aandi_post_web_server.course.domain.model.CourseSlug
import com.example.aandi_post_web_server.course.domain.model.UserId
import org.mockito.Mockito
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

internal object AssignmentQueryServiceTestData {
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
}

internal class AssignmentQueryServiceTestFixture(
    clock: Clock = Clock.fixed(AssignmentQueryServiceTestData.fixedNow, ZoneOffset.UTC),
) {
    val assignmentCourseQueryPort: AssignmentCourseQueryPort = Mockito.mock(AssignmentCourseQueryPort::class.java)
    val assignmentRepository: AssignmentRepository = Mockito.mock(AssignmentRepository::class.java)
    val assignmentRequirementRepository: AssignmentRequirementRepository = Mockito.mock(AssignmentRequirementRepository::class.java)
    val assignmentTestCaseRepository: AssignmentTestCaseRepository = Mockito.mock(AssignmentTestCaseRepository::class.java)

    val service = AssignmentQueryService(
        assignmentCourseQueryPort = assignmentCourseQueryPort,
        assignmentRepository = assignmentRepository,
        assignmentRequirementRepository = assignmentRequirementRepository,
        assignmentTestCaseRepository = assignmentTestCaseRepository,
        clock = clock,
    )

    init {
        stubBatchChildren()
    }

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

    fun stubCourse(
        courseId: String = "course-1",
        slug: String = "back-basic",
    ) {
        Mockito.`when`(assignmentCourseQueryPort.findBySlug(CourseSlug.from(slug)))
            .thenReturn(Mono.just(AssignmentCourseReference(id = courseId, slug = slug)))
    }

    fun stubEnrollment(
        courseId: String,
        userId: String,
        enabled: Boolean = true,
    ) {
        Mockito.`when`(
            assignmentCourseQueryPort.isEnrollmentEnabled(
                CourseId.from(courseId),
                UserId.from(userId),
            )
        ).thenReturn(Mono.just(enabled))
    }
}
