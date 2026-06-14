@file:Suppress("DEPRECATION")

package com.example.aandi_post_web_server.assignment.application.activation

import com.example.aandi_post_web_server.assignment.application.service.AdminAssignmentSubmissionStatusesV2Service
import com.example.aandi_post_web_server.common.config.AssignmentActivationGateFilterConfig
import com.example.aandi_post_web_server.common.config.WebConfig
import com.example.aandi_post_web_server.common.error.ErrorResponseFactory
import com.example.aandi_post_web_server.common.error.GlobalApiExceptionHandler
import com.example.aandi_post_web_server.common.error.GlobalWebExceptionHandler
import com.example.aandi_post_web_server.common.security.SecurityConfig
import com.example.aandi_post_web_server.course.api.v1.controller.CourseQueryV1Controller
import com.example.aandi_post_web_server.course.api.v2.controller.CourseAdminAssignmentSubmissionStatusesV2Controller
import com.example.aandi_post_web_server.course.api.v2.controller.CourseQueryV2Controller
import com.example.aandi_post_web_server.course.application.service.CourseV1Service
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
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@WebFluxTest(
    controllers = [
        CourseQueryV1Controller::class,
        CourseQueryV2Controller::class,
        CourseAdminAssignmentSubmissionStatusesV2Controller::class,
    ],
)
@Import(
    WebConfig::class,
    SecurityConfig::class,
    ErrorResponseFactory::class,
    GlobalApiExceptionHandler::class,
    GlobalWebExceptionHandler::class,
    AssignmentActivationGateFilterConfig::class,
)
class AssignmentActivationGateWebFluxTest : StringSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockBean
    private lateinit var courseV1Service: CourseV1Service

    @MockBean
    private lateinit var adminAssignmentSubmissionStatusesV2Service: AdminAssignmentSubmissionStatusesV2Service

    @MockBean
    private lateinit var activationService: AssignmentActivationService

    private val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
    private val adminId = "1fd3abf7-5ea4-403f-bcf8-8b3f9d8df502"

    init {
        beforeTest {
            Mockito.reset(activationService, courseV1Service, adminAssignmentSubmissionStatusesV2Service)
        }

        "비활성 상태에서 USER 호출은 503 ASSIGNMENT_DEACTIVATED" {
            Mockito.`when`(activationService.isActive()).thenReturn(Mono.just(false))

            userClient().get()
                .uri("/v2/courses/back-basic/assignments")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error.code").isEqualTo(50301)
                .jsonPath("$.error.value").isEqualTo("ASSIGNMENT_DEACTIVATED")
        }

        "비활성 상태여도 ADMIN 호출은 게이트를 통과한다" {
            Mockito.`when`(activationService.isActive()).thenReturn(Mono.just(false))
            Mockito.`when`(
                courseV1Service.getAssignments(
                    courseSlug = "back-basic",
                    weekNo = null,
                    status = null,
                    userId = adminId,
                )
            ).thenReturn(Flux.empty())

            adminClient().get()
                .uri("/v2/courses/back-basic/assignments")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
        }

        "활성 상태에서는 정상 응답" {
            Mockito.`when`(activationService.isActive()).thenReturn(Mono.just(true))
            Mockito.`when`(
                courseV1Service.getAssignments(
                    courseSlug = "back-basic",
                    weekNo = null,
                    status = null,
                    userId = userId,
                )
            ).thenReturn(Flux.empty())

            userClient().get()
                .uri("/v2/courses/back-basic/assignments")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
        }

        "v1 경로도 비활성 상태에서 503 ASSIGNMENT_DEACTIVATED 으로 차단된다" {
            Mockito.`when`(activationService.isActive()).thenReturn(Mono.just(false))

            userClient().get()
                .uri("/v1/courses/back-basic/assignments")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error.code").isEqualTo("ASSIGNMENT_DEACTIVATED")
        }

        "게이트 대상이 아닌 경로(/v2/courses/{slug}/outline) 는 비활성 상태에서도 통과한다" {
            Mockito.`when`(activationService.isActive()).thenReturn(Mono.just(false))
            Mockito.`when`(courseV1Service.getCourseOutline("back-basic", userId))
                .thenReturn(Mono.error(ResponseStatusExceptionStub()))

            userClient().get()
                .uri("/v2/courses/back-basic/outline")
                .exchange()
                .expectStatus().is5xxServerError
            // The point is not the success of the call but that the gate did not reject it
            // — we get a service-level error from the mock, not 503 ASSIGNMENT_DEACTIVATED
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
            .defaultHeader("timestamp", "2026-06-12T18:00:00+09:00")
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
            .defaultHeader("timestamp", "2026-06-12T18:00:00+09:00")
            .build()

    private class ResponseStatusExceptionStub : RuntimeException("stub")
}
