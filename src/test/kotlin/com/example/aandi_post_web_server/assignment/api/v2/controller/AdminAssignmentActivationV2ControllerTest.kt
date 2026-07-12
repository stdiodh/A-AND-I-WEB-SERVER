@file:Suppress("DEPRECATION")

package com.example.aandi_post_web_server.assignment.api.v2.controller

import com.example.aandi_post_web_server.assignment.application.activation.AssignmentActivationService
import com.example.aandi_post_web_server.assignment.application.activation.TestAssignmentActivationConfig
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentActivation
import com.example.aandi_post_web_server.common.config.WebConfig
import com.example.aandi_post_web_server.common.error.ErrorResponseFactory
import com.example.aandi_post_web_server.common.error.GlobalApiExceptionHandler
import com.example.aandi_post_web_server.common.error.GlobalWebExceptionHandler
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

@WebFluxTest(controllers = [AdminAssignmentActivationV2Controller::class])
@Import(
    WebConfig::class,
    SecurityConfig::class,
    ErrorResponseFactory::class,
    GlobalApiExceptionHandler::class,
    GlobalWebExceptionHandler::class,
    TestAssignmentActivationConfig::class,
)
class AdminAssignmentActivationV2ControllerTest : StringSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockBean
    private lateinit var activationService: AssignmentActivationService

    private val adminId = "1fd3abf7-5ea4-403f-bcf8-8b3f9d8df502"
    private val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
    private val sampleActivation = AssignmentActivation(
        id = AssignmentActivation.GLOBAL_ID,
        active = false,
        updatedAt = Instant.parse("2026-06-12T01:00:00Z"),
        updatedBy = "1fd3abf7-5ea4-403f-bcf8-8b3f9d8df502",
    )

    init {
        beforeTest { Mockito.reset(activationService) }

        "토큰이 없으면 401" {
            webTestClient.get()
                .uri("/v2/admin/assignments/activation")
                .exchange()
                .expectStatus().isUnauthorized
        }

        "USER 권한이면 403" {
            userClient().get()
                .uri("/v2/admin/assignments/activation")
                .exchange()
                .expectStatus().isForbidden
        }

        "ADMIN 권한 GET 은 현재 활성화 상태를 반환한다" {
            Mockito.`when`(activationService.getActivation()).thenReturn(Mono.just(sampleActivation))

            adminClient().get()
                .uri("/v2/admin/assignments/activation")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.active").isEqualTo(false)
                .jsonPath("$.data.updatedAt").isEqualTo("2026-06-12T01:00:00Z")
                .jsonPath("$.data.updatedBy").isEqualTo("1fd3abf7-5ea4-403f-bcf8-8b3f9d8df502")
                .jsonPath("$.data.id").doesNotExist()
        }

        "PUT 은 active 값으로 토글한다" {
            Mockito.`when`(activationService.setActivation(active = false, updatedBy = adminId))
                .thenReturn(Mono.just(sampleActivation))

            adminClient().put()
                .uri("/v2/admin/assignments/activation")
                .header("Content-Type", "application/json")
                .bodyValue("""{"active":false}""")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.active").isEqualTo(false)
        }

        "PUT 에서 active 가 null 이면 400" {
            adminClient().put()
                .uri("/v2/admin/assignments/activation")
                .header("Content-Type", "application/json")
                .bodyValue("""{"active":null}""")
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
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
}
