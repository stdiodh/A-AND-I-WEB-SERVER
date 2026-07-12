package com.example.aandi_post_web_server.course.application.service

import com.example.aandi_post_web_server.assignment.application.service.AssignmentQueryService
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import com.example.aandi_post_web_server.course.infrastructure.adapter.RepositoryCourseEnrollmentStore
import com.example.aandi_post_web_server.course.infrastructure.adapter.RepositoryCourseStore
import com.example.aandi_post_web_server.course.infrastructure.adapter.RepositoryCourseWeekStore
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseEnrollmentRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseWeekRepository
import io.kotest.core.spec.style.StringSpec
import org.mockito.Mockito
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class CourseQueryServiceAssignmentDelegationTest : StringSpec({
    "assignment query facade preserves every public call without touching course persistence" {
        val courseRepository = Mockito.mock(CourseRepository::class.java)
        val courseEnrollmentRepository = Mockito.mock(CourseEnrollmentRepository::class.java)
        val courseWeekRepository = Mockito.mock(CourseWeekRepository::class.java)
        val assignmentQueryService = Mockito.mock(AssignmentQueryService::class.java)
        val service = CourseQueryService(
            courseStore = RepositoryCourseStore(courseRepository),
            courseEnrollmentStore = RepositoryCourseEnrollmentStore(courseEnrollmentRepository),
            courseWeekStore = RepositoryCourseWeekStore(courseWeekRepository),
            assignmentQueryService = assignmentQueryService,
        )

        Mockito.`when`(
            assignmentQueryService.getAssignmentsByWeek(
                COURSE_SLUG,
                WEEK_NO,
                AssignmentStatus.PUBLISHED,
                USER_ID,
            )
        ).thenReturn(Flux.empty())
        Mockito.`when`(
            assignmentQueryService.getAssignments(COURSE_SLUG, null, null, USER_ID)
        ).thenReturn(Flux.empty())
        Mockito.`when`(
            assignmentQueryService.getAdminAssignments(COURSE_SLUG, WEEK_NO, AssignmentStatus.DRAFT)
        ).thenReturn(Flux.empty())
        Mockito.`when`(
            assignmentQueryService.getAssignmentDetail(COURSE_SLUG, ASSIGNMENT_ID, USER_ID)
        ).thenReturn(Mono.empty())
        Mockito.`when`(
            assignmentQueryService.getAdminAssignmentDetail(COURSE_SLUG, ASSIGNMENT_ID)
        ).thenReturn(Mono.empty())

        StepVerifier.create(
            service.getAssignmentsByWeek(COURSE_SLUG, WEEK_NO, AssignmentStatus.PUBLISHED, USER_ID)
        ).verifyComplete()
        StepVerifier.create(service.getAssignments(COURSE_SLUG, null, null, USER_ID)).verifyComplete()
        StepVerifier.create(
            service.getAdminAssignments(COURSE_SLUG, WEEK_NO, AssignmentStatus.DRAFT)
        ).verifyComplete()
        StepVerifier.create(
            service.getAssignmentDetail(COURSE_SLUG, ASSIGNMENT_ID, USER_ID)
        ).verifyComplete()
        StepVerifier.create(service.getAdminAssignmentDetail(COURSE_SLUG, ASSIGNMENT_ID)).verifyComplete()

        Mockito.verify(assignmentQueryService)
            .getAssignmentsByWeek(COURSE_SLUG, WEEK_NO, AssignmentStatus.PUBLISHED, USER_ID)
        Mockito.verify(assignmentQueryService).getAssignments(COURSE_SLUG, null, null, USER_ID)
        Mockito.verify(assignmentQueryService)
            .getAdminAssignments(COURSE_SLUG, WEEK_NO, AssignmentStatus.DRAFT)
        Mockito.verify(assignmentQueryService).getAssignmentDetail(COURSE_SLUG, ASSIGNMENT_ID, USER_ID)
        Mockito.verify(assignmentQueryService).getAdminAssignmentDetail(COURSE_SLUG, ASSIGNMENT_ID)
        Mockito.verifyNoInteractions(
            courseRepository,
            courseEnrollmentRepository,
            courseWeekRepository,
        )
    }
})

private const val COURSE_SLUG = "back-basic"
private const val WEEK_NO = 1
private const val USER_ID = "8ee88b63-526d-49dc-9e72-a96be0f81385"
private const val ASSIGNMENT_ID = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
