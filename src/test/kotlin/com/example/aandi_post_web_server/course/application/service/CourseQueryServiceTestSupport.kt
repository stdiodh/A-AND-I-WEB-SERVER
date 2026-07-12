package com.example.aandi_post_web_server.course.application.service

import com.example.aandi_post_web_server.assignment.application.service.AssignmentQueryService
import com.example.aandi_post_web_server.assignment.infrastructure.adapter.RepositoryAssignmentQueryStore
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRequirementRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentTestCaseRepository
import com.example.aandi_post_web_server.course.domain.model.CourseTrack
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseMetadata
import com.example.aandi_post_web_server.course.infrastructure.adapter.AssignmentCourseQueryAdapter
import com.example.aandi_post_web_server.course.infrastructure.adapter.RepositoryCourseEnrollmentStore
import com.example.aandi_post_web_server.course.infrastructure.adapter.RepositoryCourseStore
import com.example.aandi_post_web_server.course.infrastructure.adapter.RepositoryCourseWeekStore
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseEnrollmentRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseWeekRepository
import org.mockito.Mockito
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal object CourseQueryServiceTestData {
    val fixedNow: Instant = Instant.parse("2026-03-15T00:00:00Z")

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
    private val assignmentRequirementRepository: AssignmentRequirementRepository = Mockito.mock(AssignmentRequirementRepository::class.java)
    private val assignmentTestCaseRepository: AssignmentTestCaseRepository = Mockito.mock(AssignmentTestCaseRepository::class.java)
    private val assignmentQueryService = AssignmentQueryService(
        assignmentCourseQueryPort = AssignmentCourseQueryAdapter(courseRepository, courseEnrollmentRepository),
        assignmentQueryStore = RepositoryAssignmentQueryStore(assignmentRepository),
        assignmentRequirementRepository = assignmentRequirementRepository,
        assignmentTestCaseRepository = assignmentTestCaseRepository,
        clock = clock,
    )

    val service = CourseQueryService(
        courseStore = RepositoryCourseStore(courseRepository),
        courseEnrollmentStore = RepositoryCourseEnrollmentStore(courseEnrollmentRepository),
        courseWeekStore = RepositoryCourseWeekStore(courseWeekRepository),
        assignmentQueryService = assignmentQueryService,
        clock = clock,
    )
}
