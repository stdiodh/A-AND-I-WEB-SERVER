@file:Suppress("DEPRECATION")

package com.example.aandi_post_web_server.course.api.v2.controller

import com.example.aandi_post_web_server.assignment.api.v2.dto.AdminAssignmentSubmissionStatusItemResponse
import com.example.aandi_post_web_server.assignment.api.v2.dto.AdminAssignmentSubmissionStatusesResponse
import com.example.aandi_post_web_server.assignment.application.service.AdminAssignmentSubmissionStatusesV2Service
import com.example.aandi_post_web_server.common.config.WebConfig
import com.example.aandi_post_web_server.common.error.ErrorResponseFactory
import com.example.aandi_post_web_server.common.error.GlobalApiExceptionHandler
import com.example.aandi_post_web_server.common.error.GlobalWebExceptionHandler
import com.example.aandi_post_web_server.common.security.SecurityConfig
import com.example.aandi_post_web_server.course.domain.model.EnrollmentStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.spring.SpringExtension
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import java.time.Instant

@WebFluxTest(controllers = [CourseAdminAssignmentSubmissionStatusesV2Controller::class])
@Import(
    WebConfig::class,
    SecurityConfig::class,
    ErrorResponseFactory::class,
    GlobalApiExceptionHandler::class,
    GlobalWebExceptionHandler::class,
)
class CourseAdminAssignmentSubmissionStatusesV2ControllerTest : StringSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockBean
    private lateinit var adminAssignmentSubmissionStatusesV2Service: AdminAssignmentSubmissionStatusesV2Service

    private val courseSlug = "back-basic"
    private val assignmentId = "7fbe8f62-9d89-4c74-b1e4-3ad3b9d7f001"
    private val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
    private val adminId = "1fd3abf7-5ea4-403f-bcf8-8b3f9d8df502"

    init {
        beforeTest {
            Mockito.reset(adminAssignmentSubmissionStatusesV2Service)
        }

        "관리자 제출 현황 API는 토큰이 없으면 401을 반환한다" {
            webTestClient.get()
                .uri("/v2/admin/courses/$courseSlug/assignments/$assignmentId/submission-statuses")
                .exchange()
                .expectStatus().isUnauthorized
        }

        "관리자 제출 현황 API는 ADMIN 권한이 아니면 403을 반환한다" {
            userClient().get()
                .uri("/v2/admin/courses/$courseSlug/assignments/$assignmentId/submission-statuses")
                .exchange()
                .expectStatus().isForbidden
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error.code").isEqualTo(21201)
        }

        "관리자 제출 현황 API는 제출자와 미제출자를 함께 반환한다" {
            Mockito.`when`(adminAssignmentSubmissionStatusesV2Service.getSubmissionStatuses(courseSlug, assignmentId))
                .thenReturn(Mono.just(sampleResponse()))

            adminClient().get()
                .uri("/v2/admin/courses/$courseSlug/assignments/$assignmentId/submission-statuses")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.assignmentId").isEqualTo(assignmentId)
                .jsonPath("$.data.totalEnrolled").isEqualTo(2)
                .jsonPath("$.data.submittedCount").isEqualTo(1)
                .jsonPath("$.data.notSubmittedCount").isEqualTo(1)
                .jsonPath("$.data.items[0].submitted").isEqualTo(true)
                .jsonPath("$.data.items[0].score").isEqualTo(90)
                .jsonPath("$.data.items[0].completedAt").isEqualTo("2026-04-09T02:15:30.123Z")
                .jsonPath("$.data.items[1].submitted").isEqualTo(false)
                .jsonPath("$.data.items[1].score").isEmpty
                .jsonPath("$.data.items[1].completedAt").isEmpty
                .jsonPath("$.data.items[0].firstCompletedAt").doesNotExist()
                .jsonPath("$.data.items[0].lastCompletedAt").doesNotExist()
                .jsonPath("$.data.items[0].latestScore").doesNotExist()
        }

        "존재하지 않는 코스 또는 과제면 404를 반환한다" {
            Mockito.`when`(adminAssignmentSubmissionStatusesV2Service.getSubmissionStatuses(courseSlug, assignmentId))
                .thenReturn(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "과제를 찾을 수 없습니다.")))

            adminClient().get()
                .uri("/v2/admin/courses/$courseSlug/assignments/$assignmentId/submission-statuses")
                .exchange()
                .expectStatus().isNotFound
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error.code").isEqualTo(96501)
        }
    }

    private fun userClient(): WebTestClient =
        webTestClient
            .mutateWith(
                mockJwt()
                    .jwt { jwt ->
                        jwt.subject(userId)
                        jwt.claim("role", "USER")
                        jwt.claim("token_type", "ACCESS")
                    }
                    .authorities(SimpleGrantedAuthority("ROLE_USER"))
            )
            .mutate()
            .defaultHeader("deviceOS", "IOS")
            .defaultHeader("timestamp", "2026-04-13T18:00:00+09:00")
            .build()

    private fun adminClient(): WebTestClient =
        webTestClient
            .mutateWith(
                mockJwt()
                    .jwt { jwt ->
                        jwt.subject(adminId)
                        jwt.claim("role", "ADMIN")
                        jwt.claim("token_type", "ACCESS")
                    }
                    .authorities(SimpleGrantedAuthority("ROLE_ADMIN"))
            )
            .mutate()
            .defaultHeader("deviceOS", "IOS")
            .defaultHeader("timestamp", "2026-04-13T18:00:00+09:00")
            .build()

    private fun sampleResponse(): AdminAssignmentSubmissionStatusesResponse =
        AdminAssignmentSubmissionStatusesResponse(
            assignmentId = assignmentId,
            courseSlug = courseSlug,
            totalEnrolled = 2,
            submittedCount = 1,
            notSubmittedCount = 1,
            items = listOf(
                AdminAssignmentSubmissionStatusItemResponse(
                    userId = "user-1",
                    publicCode = "A00123",
                    username = "alice",
                    enrollmentStatus = EnrollmentStatus.ENABLED,
                    submitted = true,
                    score = 90,
                    passedCases = 9,
                    totalCases = 10,
                    completedAt = Instant.parse("2026-04-09T02:15:30.123Z"),
                ),
                AdminAssignmentSubmissionStatusItemResponse(
                    userId = "user-2",
                    publicCode = "A00124",
                    username = "bob",
                    enrollmentStatus = EnrollmentStatus.ENABLED,
                    submitted = false,
                ),
            ),
        )
}
