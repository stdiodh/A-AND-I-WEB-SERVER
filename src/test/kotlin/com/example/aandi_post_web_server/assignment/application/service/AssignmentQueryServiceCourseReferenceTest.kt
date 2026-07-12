package com.example.aandi_post_web_server.assignment.application.service

import com.example.aandi_post_web_server.assignment.application.port.AssignmentCourseQueryPort
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.entity.AssignmentMetadata
import com.example.aandi_post_web_server.assignment.infrastructure.adapter.RepositoryAssignmentContentQueryStore
import com.example.aandi_post_web_server.assignment.infrastructure.adapter.RepositoryAssignmentQueryStore
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRequirementRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentTestCaseRepository
import com.example.aandi_post_web_server.course.domain.model.AssignmentId
import com.example.aandi_post_web_server.course.domain.model.CourseId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AssignmentQueryServiceCourseReferenceTest : StringSpec({
    "outline reference projects visible assignments in week and order sequence" {
        val fixture = CourseReferenceFixture()
        val weekTwo = courseReferenceAssignment(
            id = ASSIGNMENT_ID_3,
            weekNo = 2,
            orderInWeek = 1,
            title = "2주차 첫 과제",
            difficulty = AssignmentDifficulty.HIGH,
        )
        val scheduled = courseReferenceAssignment(
            id = ASSIGNMENT_ID_4,
            weekNo = 1,
            orderInWeek = 1,
            title = "예약 과제",
            startAt = NOW.plusSeconds(1),
        )
        val weekOneSecond = courseReferenceAssignment(
            id = ASSIGNMENT_ID_2,
            weekNo = 1,
            orderInWeek = 2,
            title = "1주차 두 번째 과제",
            difficulty = AssignmentDifficulty.MID,
        )
        val draft = courseReferenceAssignment(
            id = ASSIGNMENT_ID_5,
            weekNo = 1,
            orderInWeek = 1,
            title = "초안 과제",
            status = AssignmentStatus.DRAFT,
        )
        val weekOneFirst = courseReferenceAssignment(
            id = ASSIGNMENT_ID_1,
            weekNo = 1,
            orderInWeek = 1,
            title = "1주차 첫 과제",
            difficulty = AssignmentDifficulty.LOW,
        )
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseId(COURSE_ID))
            .thenReturn(Flux.just(weekTwo, scheduled, weekOneSecond, draft, weekOneFirst))

        StepVerifier.create(fixture.service.getVisibleOutlineAssignments(CourseId.from(COURSE_ID)))
            .expectNext(
                weekOneFirst.toOutlineReference(),
                weekOneSecond.toOutlineReference(),
                weekTwo.toOutlineReference(),
            )
            .verifyComplete()

        Mockito.verify(fixture.assignmentRepository).findAllByCourseId(COURSE_ID)
        fixture.verifyNoChildOrCourseQueries()
    }

    "course reference returns exact not found response when assignment is missing" {
        val fixture = CourseReferenceFixture()
        Mockito.`when`(fixture.assignmentRepository.findById(ASSIGNMENT_ID_1))
            .thenReturn(Mono.empty())

        StepVerifier.create(
            fixture.service.getVisibleAssignmentCourseId(AssignmentId.from(ASSIGNMENT_ID_1))
        )
            .expectErrorSatisfies { error ->
                val exception = error as ResponseStatusException
                exception.statusCode shouldBe HttpStatus.NOT_FOUND
                exception.reason shouldBe "과제를 찾을 수 없습니다: $ASSIGNMENT_ID_1"
            }
            .verify()

        Mockito.verify(fixture.assignmentRepository).findById(ASSIGNMENT_ID_1)
        fixture.verifyNoChildOrCourseQueries()
    }

    "course reference returns exact not found response when assignment is hidden" {
        val fixture = CourseReferenceFixture()
        val hidden = courseReferenceAssignment(
            id = ASSIGNMENT_ID_1,
            startAt = NOW.plusSeconds(1),
        )
        Mockito.`when`(fixture.assignmentRepository.findById(ASSIGNMENT_ID_1))
            .thenReturn(Mono.just(hidden))

        StepVerifier.create(
            fixture.service.getVisibleAssignmentCourseId(AssignmentId.from(ASSIGNMENT_ID_1))
        )
            .expectErrorSatisfies { error ->
                val exception = error as ResponseStatusException
                exception.statusCode shouldBe HttpStatus.NOT_FOUND
                exception.reason shouldBe "과제를 찾을 수 없습니다: $ASSIGNMENT_ID_1"
            }
            .verify()

        Mockito.verify(fixture.assignmentRepository).findById(ASSIGNMENT_ID_1)
        fixture.verifyNoChildOrCourseQueries()
    }

    "course reference returns the visible assignment raw course id" {
        val fixture = CourseReferenceFixture()
        val visible = courseReferenceAssignment(
            id = ASSIGNMENT_ID_1,
            courseId = RAW_COURSE_ID,
        )
        Mockito.`when`(fixture.assignmentRepository.findById(ASSIGNMENT_ID_1))
            .thenReturn(Mono.just(visible))

        StepVerifier.create(
            fixture.service.getVisibleAssignmentCourseId(AssignmentId.from(ASSIGNMENT_ID_1))
        )
            .expectNext(RAW_COURSE_ID)
            .verifyComplete()

        Mockito.verify(fixture.assignmentRepository).findById(ASSIGNMENT_ID_1)
        fixture.verifyNoChildOrCourseQueries()
    }
})

private class CourseReferenceFixture {
    val assignmentCourseQueryPort: AssignmentCourseQueryPort = Mockito.mock(AssignmentCourseQueryPort::class.java)
    val assignmentRepository: AssignmentRepository = Mockito.mock(AssignmentRepository::class.java)
    private val assignmentRequirementRepository = Mockito.mock(AssignmentRequirementRepository::class.java)
    private val assignmentTestCaseRepository = Mockito.mock(AssignmentTestCaseRepository::class.java)

    val service = AssignmentQueryService(
        assignmentCourseQueryPort = assignmentCourseQueryPort,
        assignmentQueryStore = RepositoryAssignmentQueryStore(assignmentRepository),
        assignmentContentQueryStore = RepositoryAssignmentContentQueryStore(
            assignmentRequirementRepository,
            assignmentTestCaseRepository,
        ),
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    fun verifyNoChildOrCourseQueries() {
        Mockito.verifyNoInteractions(
            assignmentCourseQueryPort,
            assignmentRequirementRepository,
            assignmentTestCaseRepository,
        )
    }
}

private fun courseReferenceAssignment(
    id: String,
    courseId: String = COURSE_ID,
    weekNo: Int = 1,
    orderInWeek: Int = 1,
    title: String = "과제",
    difficulty: AssignmentDifficulty = AssignmentDifficulty.MID,
    status: AssignmentStatus = AssignmentStatus.PUBLISHED,
    startAt: Instant = NOW.minusSeconds(1),
    endAt: Instant = NOW.plusSeconds(3600),
): Assignment = Assignment(
    id = id,
    courseId = courseId,
    courseSlug = "back-basic",
    createdBy = "admin",
    weekNo = weekNo,
    orderInWeek = orderInWeek,
    startAt = startAt,
    endAt = endAt,
    metadata = AssignmentMetadata(
        title = title,
        difficulty = difficulty,
        description = "설명",
        timeLimitMinutes = 60,
    ),
    status = status,
    createdAt = NOW.minusSeconds(3600),
    updatedAt = NOW.minusSeconds(1800),
    publishedAt = if (status == AssignmentStatus.PUBLISHED) startAt else null,
)

private fun Assignment.toOutlineReference(): AssignmentOutlineReference = AssignmentOutlineReference(
    assignmentId = requireNotNull(id),
    weekNo = weekNo,
    orderInWeek = orderInWeek,
    title = metadata.title,
    difficulty = metadata.difficulty,
    startAt = startAt,
    endAt = endAt,
)

private val NOW: Instant = Instant.parse("2026-07-11T00:00:00Z")
private const val COURSE_ID = "course-1"
private const val RAW_COURSE_ID = "stored-course-id"
private const val ASSIGNMENT_ID_1 = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
private const val ASSIGNMENT_ID_2 = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f112"
private const val ASSIGNMENT_ID_3 = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f113"
private const val ASSIGNMENT_ID_4 = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f114"
private const val ASSIGNMENT_ID_5 = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f115"
