package com.example.aandi_post_web_server.common.error

import com.example.aandi_post_web_server.common.security.SecurityConfig
import com.example.aandi_post_web_server.course.controller.CourseQueryV1Controller
import com.example.aandi_post_web_server.course.controller.CourseV1Controller
import com.example.aandi_post_web_server.course.service.CourseV1Service
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

@WebFluxTest(controllers = [CourseV1Controller::class, CourseQueryV1Controller::class])
@Import(
    SecurityConfig::class,
    RequestIdWebFilter::class,
    ErrorResponseFactory::class,
    GlobalApiExceptionHandler::class,
    GlobalWebExceptionHandler::class,
)
class ErrorHandlingWebFluxTest : StringSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockBean
    private lateinit var courseV1Service: CourseV1Service

    init {
        beforeTest {
            Mockito.reset(courseV1Service)
        }

        "validation 실패 시 공통 에러 envelope를 반환한다" {
            webTestClient.mutateWith(
                mockJwt().jwt { jwt ->
                    jwt.subject("2d61d1cb-2898-4319-ba62-d30bbd44eb21")
                }.authorities(SimpleGrantedAuthority("ROLE_ADMIN")),
            ).post()
                .uri("/v1/admin/courses")
                .header(RequestIdSupport.HEADER_NAME, "req-validation-001")
                .bodyValue(
                    mapOf(
                        "slug" to "",
                        "fieldTag" to "FL",
                        "startDate" to "2026-03-02",
                        "endDate" to "2026-03-30",
                        "metadata" to mapOf(
                            "title" to "FL 기초",
                            "description" to "desc",
                            "phase" to "BASIC",
                        ),
                    )
                )
                .exchange()
                .expectStatus().isBadRequest
                .expectHeader().valueEquals(RequestIdSupport.HEADER_NAME, "req-validation-001")
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.data").isEmpty
                .jsonPath("$.error.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.error.message").isEqualTo("요청 값이 올바르지 않습니다.")
                .jsonPath("$.timestamp").exists()
        }

        "enum mismatch 실패 시 공통 에러 envelope를 반환한다" {
            webTestClient.mutateWith(
                mockJwt().jwt { jwt ->
                    jwt.subject("2d61d1cb-2898-4319-ba62-d30bbd44eb21")
                }.authorities(SimpleGrantedAuthority("ROLE_ADMIN")),
            ).post()
                .uri("/v1/admin/courses")
                .header(RequestIdSupport.HEADER_NAME, "req-enum-001")
                .bodyValue(
                    mapOf(
                        "slug" to "fl-basic",
                        "fieldTag" to "FL",
                        "startDate" to "2026-03-02",
                        "endDate" to "2026-03-30",
                        "metadata" to mapOf(
                            "title" to "FL 기초",
                            "description" to "desc",
                            "phase" to "BAS1C",
                        ),
                    )
                )
                .exchange()
                .expectStatus().isBadRequest
                .expectHeader().valueEquals(RequestIdSupport.HEADER_NAME, "req-enum-001")
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error.code").isEqualTo("ENUM_MISMATCH")
                .jsonPath("$.error.message").isEqualTo("열거형 값이 올바르지 않습니다.")
                .jsonPath("$.timestamp").exists()
        }

        "Authorization 헤더가 없으면 401 공통 에러 envelope를 반환한다" {
            webTestClient.post()
                .uri("/v1/admin/courses")
                .header(RequestIdSupport.HEADER_NAME, "req-auth-001")
                .bodyValue(
                    mapOf(
                        "slug" to "fl-basic",
                        "fieldTag" to "FL",
                        "startDate" to "2026-03-02",
                        "endDate" to "2026-03-30",
                        "metadata" to mapOf(
                            "title" to "FL 기초",
                            "description" to "desc",
                            "phase" to "BASIC",
                        ),
                    )
                )
                .exchange()
                .expectStatus().isUnauthorized
                .expectHeader().valueEquals(RequestIdSupport.HEADER_NAME, "req-auth-001")
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.data").isEmpty
                .jsonPath("$.error.code").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.error.message").isEqualTo("인증이 필요하거나 토큰이 유효하지 않습니다.")
                .jsonPath("$.timestamp").exists()
        }
    }
}
