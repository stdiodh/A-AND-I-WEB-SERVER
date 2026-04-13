package com.example.aandi_post_web_server.common.config

import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping
import java.time.Duration

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ReportSwaggerDocumentationIntegrationTest : StringSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var requestMappingHandlerMapping: RequestMappingHandlerMapping

    private fun client(): WebTestClient =
        webTestClient.mutate()
            .responseTimeout(Duration.ofSeconds(20))
            .build()

    init {
        "report-v1 api docs endpoint는 200을 반환하고 v2 경로를 섞지 않는다" {
            client().get()
                .uri("/v3/api-docs/report-v1")
                .exchange()
                .expectStatus().isOk
                .expectBody(String::class.java)
                .consumeWith { result ->
                    val body = result.responseBody ?: ""
                    body.shouldContain("\"title\":\"A&I v1 API 문서\"")
                    body.shouldContain("\"/v1/courses\"")
                    body.shouldContain("\"/v1/admin/courses\"")
                    body.shouldNotContain("\"/v2/report")
                    body.shouldNotContain("\"/v2/assignments")
                }
        }

        "report-v2 api docs endpoint는 모든 v2 경로를 함께 포함하고 v1 경로를 섞지 않는다" {
            client().get()
                .uri("/v3/api-docs/report-v2")
                .exchange()
                .expectStatus().isOk
                .expectBody(String::class.java)
                .consumeWith { result ->
                    val body = result.responseBody ?: ""
                    body.shouldContain("\"title\":\"A&I v2 클라이언트 계약 문서\"")
                    body.shouldContain("\"/v2/report")
                    body.shouldContain("\"/v2/assignments/{assignmentId}/submission-status/me\"")
                    body.shouldContain("\"/v2/assignments/{assignmentId}/course\"")
                    body.shouldContain("\"/v2/courses\"")
                    body.shouldContain("\"/v2/admin/courses\"")
                    body.shouldContain("\"/v2/admin/courses/{courseSlug}/assignments/{assignmentId}/submission-statuses\"")
                    body.shouldNotContain("\"/v1/courses")
                    body.shouldNotContain("\"/v1/report")
                    body.shouldContain("\"description\":\"요청 성공 여부(Boolean)\"")
                    body.shouldContain("ISO-8601 또는 epoch milliseconds")
                    body.shouldContain("\"type\":\"boolean\"")
                    body.shouldContain("\"success\":true")
                    body.shouldContain("\"success\":false")
                    body.shouldNotContain("\"success\":\"SUCCESS\"")
                    body.shouldNotContain("\"success\":\"FAIL\"")
                    body.shouldContain("Authorization: Bearer {JWT}")
                    body.shouldContain("`/v2/courses/**`, `/v2/admin/courses/**`")
                    body.shouldContain("ReportApiEnvelope")
                    body.shouldContain("ApiEnvelope(success/data/error/timestamp)")
                    body.shouldContain("JUDGE_COMPLETED")
                    body.shouldContain("관리자용 과제 제출 현황 조회")
                    body.shouldContain("projection 이 없으면 미제출")
                    body.shouldContain("submitted_false_projection_not_found")
                    body.shouldContain("INTERNAL_ERROR_PUBLIC_CODE_PROJECTION_MISSING")
                }
        }

        "report-v2 문서는 현재 등록된 모든 v2 컨트롤러 경로를 포함한다" {
            val swaggerBody = client().get()
                .uri("/v3/api-docs/report-v2")
                .exchange()
                .expectStatus().isOk
                .expectBody(String::class.java)
                .returnResult()
                .responseBody ?: ""

            val mappedV2Paths = requestMappingHandlerMapping.handlerMethods.entries
                .filter { (_, handlerMethod) ->
                    handlerMethod.beanType.packageName.startsWith("com.example.aandi_post_web_server")
                }
                .flatMap { (mappingInfo, _) ->
                    mappingInfo.patternsCondition.patterns.map { pattern -> pattern.patternString }
                }
                .filter { path -> path.startsWith("/v2/") }
                .toSortedSet()

            check(mappedV2Paths.isNotEmpty()) {
                "검증 대상 v2 컨트롤러 경로를 찾지 못했습니다."
            }

            mappedV2Paths.forEach { path ->
                swaggerBody.shouldContain("\"$path\"")
            }
        }

        "assignment-v2 개별 api docs endpoint는 report-v2 문서로 리다이렉트된다" {
            client().get()
                .uri("/v3/api-docs/assignment-v2")
                .exchange()
                .expectStatus().is3xxRedirection
                .expectHeader().valueEquals("Location", "/v3/api-docs/report-v2")
        }

        "swagger report v1 진입 주소는 전용 문서 URL로 리다이렉트된다" {
            client().get()
                .uri("/swagger/report/v1")
                .exchange()
                .expectStatus().is3xxRedirection
                .expectHeader().valueEquals("Location", "/swagger-ui/index.html?urls.primaryName=report-service-v1")
        }

        "swagger report v2 진입 주소는 전용 문서 URL로 리다이렉트된다" {
            client().get()
                .uri("/swagger/report/v2")
                .exchange()
                .expectStatus().is3xxRedirection
                .expectHeader().valueEquals("Location", "/swagger-ui/index.html?urls.primaryName=report-service-v2")
        }

        "swagger config에는 gateway 스타일 이름이 포함된다" {
            client().get()
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
                    body.shouldNotContain("assignment-service-v2")
                    body.shouldNotContain("/v3/api-docs/assignment-v2")
                }
        }
    }
}
