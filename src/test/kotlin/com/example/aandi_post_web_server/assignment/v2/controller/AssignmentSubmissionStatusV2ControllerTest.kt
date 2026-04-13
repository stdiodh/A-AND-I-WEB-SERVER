@file:Suppress("DEPRECATION")

package com.example.aandi_post_web_server.assignment.v2.controller

import com.example.aandi_post_web_server.assignment.v2.dto.AssignmentSubmissionStatusResponse
import com.example.aandi_post_web_server.assignment.v2.service.AssignmentSubmissionStatusV2Service
import com.example.aandi_post_web_server.common.config.WebConfig
import com.example.aandi_post_web_server.common.error.ErrorResponseFactory
import com.example.aandi_post_web_server.common.security.SecurityConfig
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.spring.SpringExtension
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.time.Instant

@WebFluxTest(controllers = [AssignmentSubmissionStatusV2Controller::class])
@Import(WebConfig::class, SecurityConfig::class, ErrorResponseFactory::class)
class AssignmentSubmissionStatusV2ControllerTest : StringSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockBean
    private lateinit var assignmentSubmissionStatusV2Service: AssignmentSubmissionStatusV2Service

    private val assignmentId = "7fbe8f62-9d89-4c74-b1e4-3ad3b9d7f001"
    private val userId = "user-1"

    init {
        beforeTest {
            Mockito.reset(assignmentSubmissionStatusV2Service)
        }

        "v2 제출 여부 조회 API는 토큰이 없으면 401을 반환한다" {
            webTestClient.get()
                .uri("/v2/assignments/$assignmentId/submission-status/me")
                .exchange()
                .expectStatus().isUnauthorized
        }

        "v2 제출 여부 조회 API는 공통 envelope 를 반환한다" {
            Mockito.`when`(assignmentSubmissionStatusV2Service.getMySubmissionStatus(assignmentId, userId))
                .thenReturn(
                    Mono.just(
                        AssignmentSubmissionStatusResponse(
                            assignmentId = assignmentId,
                            submitted = true,
                            firstCompletedAt = Instant.parse("2026-04-13T08:20:11Z"),
                            lastCompletedAt = Instant.parse("2026-04-13T08:20:11Z"),
                            latestScore = 80,
                            passedCases = 8,
                            totalCases = 10,
                        )
                    )
                )

            webTestClient.mutateWith(
                mockJwt().jwt { jwt ->
                    jwt.subject(userId)
                }.authorities(SimpleGrantedAuthority("ROLE_USER")),
            ).get()
                .uri("/v2/assignments/$assignmentId/submission-status/me")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.assignmentId").isEqualTo(assignmentId)
                .jsonPath("$.data.submitted").isEqualTo(true)
                .jsonPath("$.data.latestScore").isEqualTo(80)
                .jsonPath("$.error").isEmpty
                .jsonPath("$.timestamp").exists()
        }
    }
}
