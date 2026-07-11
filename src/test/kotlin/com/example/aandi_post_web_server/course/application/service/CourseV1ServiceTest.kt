package com.example.aandi_post_web_server.course.application.service

import com.example.aandi_post_web_server.assignment.api.dto.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentSummaryResponse
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import io.kotest.core.spec.style.StringSpec
import org.mockito.Mockito
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class CourseV1ServiceTest : StringSpec({
    val courseCommandService = Mockito.mock(CourseCommandService::class.java)
    val courseQueryService = Mockito.mock(CourseQueryService::class.java)
    val service = CourseV1Service(courseCommandService, courseQueryService)

    beforeTest {
        Mockito.reset(courseCommandService, courseQueryService)
    }

    "과제 목록 조회는 query service에 그대로 위임한다" {
        val response = Mockito.mock(AssignmentSummaryResponse::class.java)
        Mockito.`when`(
            courseQueryService.getAssignments(
                courseSlug = "back-basic",
                weekNo = null,
                status = AssignmentStatus.PUBLISHED,
                userId = "user-1",
            )
        ).thenReturn(Flux.just(response))

        StepVerifier.create(service.getAssignments("back-basic", null, AssignmentStatus.PUBLISHED, "user-1"))
            .expectNext(response)
            .verifyComplete()

        Mockito.verify(courseQueryService)
            .getAssignments("back-basic", null, AssignmentStatus.PUBLISHED, "user-1")
    }

    "과제 상세 조회는 query service에 그대로 위임한다" {
        val response = Mockito.mock(AssignmentDetailResponse::class.java)
        Mockito.`when`(
            courseQueryService.getAssignmentDetail(
                courseSlug = "back-basic",
                assignmentId = "assignment-1",
                userId = "user-1",
            )
        ).thenReturn(Mono.just(response))

        StepVerifier.create(service.getAssignmentDetail("back-basic", "assignment-1", "user-1"))
            .expectNext(response)
            .verifyComplete()

        Mockito.verify(courseQueryService)
            .getAssignmentDetail("back-basic", "assignment-1", "user-1")
    }
})
