package com.example.aandi_post_web_server.common.config

import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ReportSwaggerDocumentationIntegrationTest : StringSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var webTestClient: WebTestClient

    init {
        "report-v1 api docs endpoint는 200을 반환하고 v2 경로를 섞지 않는다" {
            webTestClient.get()
                .uri("/v3/api-docs/report-v1")
                .exchange()
                .expectStatus().isOk
                .expectBody(String::class.java)
                .consumeWith { result ->
                    val body = result.responseBody ?: ""
                    body.shouldContain("\"title\":\"A&I Report API v1\"")
                    body.shouldContain("\"/v1/courses\"")
                    body.shouldNotContain("\"/v2/report")
                }
        }

        "report-v2 api docs endpoint는 200을 반환하고 v1 경로를 섞지 않는다" {
            webTestClient.get()
                .uri("/v3/api-docs/report-v2")
                .exchange()
                .expectStatus().isOk
                .expectBody(String::class.java)
                .consumeWith { result ->
                    val body = result.responseBody ?: ""
                    body.shouldContain("\"title\":\"A&I Report API v2\"")
                    body.shouldContain("\"/v2/report")
                    body.shouldNotContain("\"/v1/courses")
                    body.shouldNotContain("\"/v1/report")
                    body.shouldContain("\"description\":\"요청 성공 여부(Boolean)\"")
                    body.shouldContain("ISO-8601 또는 epoch milliseconds")
                    body.shouldContain("\"type\":\"boolean\"")
                    body.shouldContain("\"success\":true")
                    body.shouldContain("\"success\":false")
                    body.shouldNotContain("\"success\":\"SUCCESS\"")
                    body.shouldNotContain("\"success\":\"FAIL\"")
                }
        }

        "swagger report v1 진입 주소는 전용 문서 URL로 리다이렉트된다" {
            webTestClient.get()
                .uri("/swagger/report/v1")
                .exchange()
                .expectStatus().is3xxRedirection
                .expectHeader().valueEquals("Location", "/swagger-ui/index.html?urls.primaryName=report-service-v1")
        }

        "swagger report v2 진입 주소는 전용 문서 URL로 리다이렉트된다" {
            webTestClient.get()
                .uri("/swagger/report/v2")
                .exchange()
                .expectStatus().is3xxRedirection
                .expectHeader().valueEquals("Location", "/swagger-ui/index.html?urls.primaryName=report-service-v2")
        }

        "swagger config에는 gateway 스타일 이름이 포함된다" {
            webTestClient.get()
                .uri("/v3/api-docs/swagger-config")
                .exchange()
                .expectStatus().isOk
                .expectBody(String::class.java)
                .consumeWith { result ->
                    val body = result.responseBody ?: ""
                    body.shouldContain("report-service-v1")
                    body.shouldContain("/v3/api-docs/report-v1")
                    body.shouldContain("report-service-v2")
                    body.shouldContain("/v3/api-docs/report-v2")
                }
        }
    }
}
