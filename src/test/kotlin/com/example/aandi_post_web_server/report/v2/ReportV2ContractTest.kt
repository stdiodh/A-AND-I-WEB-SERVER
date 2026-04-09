@file:Suppress("DEPRECATION")

package com.example.aandi_post_web_server.report.v2

import com.example.aandi_post_web_server.assignment.dtos.AssignmentCodeTemplateResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentDetailMetadataResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentLearningGoalResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentMetadataResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentRequirementResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentSummaryResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentTestCaseResponse
import com.example.aandi_post_web_server.assignment.enum.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import com.example.aandi_post_web_server.assignment.enum.AssignmentTemplateLanguage
import com.example.aandi_post_web_server.assignment.enum.AssignmentTestCaseVisibility
import com.example.aandi_post_web_server.common.config.WebConfig
import com.example.aandi_post_web_server.common.error.ErrorResponseFactory
import com.example.aandi_post_web_server.common.error.GlobalApiExceptionHandler
import com.example.aandi_post_web_server.common.error.GlobalWebExceptionHandler
import com.example.aandi_post_web_server.common.security.SecurityConfig
import com.example.aandi_post_web_server.course.service.CourseV1Service
import com.example.aandi_post_web_server.report.v2.controller.ReportQueryV2Controller
import com.example.aandi_post_web_server.report.v2.error.ReportExceptionHandler
import com.example.aandi_post_web_server.report.v2.service.ReportFacadeService
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.spring.SpringExtension
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.context.TestPropertySource
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@WebFluxTest(controllers = [ReportQueryV2Controller::class])
@TestPropertySource(properties = ["app.report.v2.salt-secret=report-v2-test-salt-secret"])
@Import(
    WebConfig::class,
    SecurityConfig::class,
    ErrorResponseFactory::class,
    GlobalApiExceptionHandler::class,
    GlobalWebExceptionHandler::class,
    ReportExceptionHandler::class,
    ReportFacadeService::class,
)
class ReportV2ContractTest : StringSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockBean
    private lateinit var courseV1Service: CourseV1Service

    private val timestampHeader = "2026-04-09T10:00:00+09:00"
    private val saltSecret = "report-v2-test-salt-secret"
    private val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
    private val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"

    init {
        beforeTest {
            Mockito.reset(courseV1Service)
        }

        "v2 report 과제 목록 조회는 규약형 envelope를 반환한다" {
            Mockito.`when`(
                courseV1Service.getAssignments("back-basic", null, null, userId)
            ).thenReturn(Flux.just(sampleAssignmentSummaryResponse()))

            authorizedRequest()
                .get()
                .uri("/v2/report/back-basic/assignments")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.success").isEqualTo("SUCCESS")
                .jsonPath("$.data[0].assignmentId").isEqualTo(assignmentId)
                .jsonPath("$.error").isEmpty
                .jsonPath("$.timestamp").exists()
        }

        "v2 report 과제 상세 조회는 규약형 envelope를 반환한다" {
            Mockito.`when`(
                courseV1Service.getAssignmentDetail("back-basic", assignmentId, userId)
            ).thenReturn(Mono.just(sampleAssignmentDetailResponse()))

            authorizedRequest()
                .get()
                .uri("/v2/report/back-basic/assignments/$assignmentId")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.success").isEqualTo("SUCCESS")
                .jsonPath("$.data.assignmentId").isEqualTo(assignmentId)
                .jsonPath("$.data.metadata.codeTemplates[0].language").isEqualTo("KOTLIN")
                .jsonPath("$.error").isEmpty
        }

        "salt 헤더가 없어도 요청은 성공한다" {
            Mockito.`when`(
                courseV1Service.getAssignments("back-basic", null, null, userId)
            ).thenReturn(Flux.just(sampleAssignmentSummaryResponse()))

            authorizedRequest(includeSalt = false)
                .get()
                .uri("/v2/report/back-basic/assignments")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.success").isEqualTo("SUCCESS")
                .jsonPath("$.error").isEmpty
        }

        "salt 헤더가 잘못되면 40301을 반환한다" {
            webTestClient.get()
                .uri("/v2/report/back-basic/assignments")
                .header("Authenticate", "Bearer ${createAccessToken()}")
                .header("deviceOS", "IOS")
                .header("timestamp", timestampHeader)
                .header("salt", "invalid-salt")
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$.success").isEqualTo("FAIL")
                .jsonPath("$.error.code").isEqualTo(40301)
                .jsonPath("$.error.value").isEqualTo("VALIDATE_ERROR")
                .jsonPath("$.error.alert").isEqualTo("입력값 형식이 올바르지 않습니다.")
        }

        "Authenticate 헤더 형식이 잘못되면 40301을 반환한다" {
            webTestClient.get()
                .uri("/v2/report/back-basic/assignments")
                .header("Authenticate", "Token abc")
                .header("deviceOS", "IOS")
                .header("timestamp", timestampHeader)
                .header("salt", createSalt(timestampHeader))
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$.success").isEqualTo("FAIL")
                .jsonPath("$.error.code").isEqualTo(40301)
                .jsonPath("$.error.value").isEqualTo("VALIDATE_ERROR")
                .jsonPath("$.error.alert").isEqualTo("입력값 형식이 올바르지 않습니다.")
        }

        "deviceOS 헤더가 없으면 40301을 반환한다" {
            webTestClient.get()
                .uri("/v2/report/back-basic/assignments")
                .header("Authenticate", "Bearer ${createAccessToken()}")
                .header("timestamp", timestampHeader)
                .header("salt", createSalt(timestampHeader))
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$.success").isEqualTo("FAIL")
                .jsonPath("$.error.code").isEqualTo(40301)
                .jsonPath("$.error.value").isEqualTo("VALIDATE_ERROR")
        }

        "인증 정보가 없으면 21101을 반환한다" {
            webTestClient.get()
                .uri("/v2/report/back-basic/assignments")
                .exchange()
                .expectStatus().isUnauthorized
                .expectBody()
                .jsonPath("$.success").isEqualTo("FAIL")
                .jsonPath("$.data").isEmpty
                .jsonPath("$.error.code").isEqualTo(21101)
                .jsonPath("$.error.value").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.timestamp").exists()
        }

        "없는 과제를 조회하면 96501을 반환한다" {
            Mockito.`when`(
                courseV1Service.getAssignmentDetail("back-basic", assignmentId, userId)
            ).thenReturn(
                Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "과제를 찾을 수 없습니다: $assignmentId"))
            )

            authorizedRequest()
                .get()
                .uri("/v2/report/back-basic/assignments/$assignmentId")
                .exchange()
                .expectStatus().isNotFound
                .expectBody()
                .jsonPath("$.success").isEqualTo("FAIL")
                .jsonPath("$.error.code").isEqualTo(96501)
                .jsonPath("$.error.value").isEqualTo("RESOURCE_NOT_FOUND")
        }

        "잘못된 상태 쿼리값은 40301을 반환한다" {
            authorizedRequest()
                .get()
                .uri("/v2/report/back-basic/assignments?status=NOT_A_STATUS")
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$.success").isEqualTo("FAIL")
                .jsonPath("$.error.code").isEqualTo(40301)
                .jsonPath("$.error.value").isEqualTo("VALIDATE_ERROR")
        }
    }

    private fun authorizedRequest(includeSalt: Boolean = true): WebTestClient {
        val builder = webTestClient.mutate()
            .defaultHeader("Authenticate", "Bearer ${createAccessToken()}")
            .defaultHeader("deviceOS", "IOS")
            .defaultHeader("timestamp", timestampHeader)
        if (includeSalt) {
            builder.defaultHeader("salt", createSalt(timestampHeader))
        }
        return builder.build()
    }

    private fun createAccessToken(): String {
        val now = Instant.now().epochSecond
        val headerJson = """{"alg":"HS256","typ":"JWT"}"""
        val payloadJson = """
            {
              "iss":"http://localhost:9000",
              "sub":"$userId",
              "aud":["aandi-gateway"],
              "role":"USER",
              "token_type":"ACCESS",
              "jti":"${UUID.randomUUID()}",
              "iat":$now,
              "exp":${now + 3600}
            }
        """.trimIndent().replace("\n", "").replace("  ", "")

        val encoder = Base64.getUrlEncoder().withoutPadding()
        val encodedHeader = encoder.encodeToString(headerJson.toByteArray())
        val encodedPayload = encoder.encodeToString(payloadJson.toByteArray())
        val signatureInput = "$encodedHeader.$encodedPayload"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec("local-dev-jwt-secret-must-be-at-least-32-bytes".toByteArray(), "HmacSHA256"))
        val signature = encoder.encodeToString(mac.doFinal(signatureInput.toByteArray()))
        return "$signatureInput.$signature"
    }

    private fun createSalt(timestamp: String): String =
        MessageDigest.getInstance("MD5")
            .digest((timestamp + saltSecret).toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun sampleAssignmentSummaryResponse(): AssignmentSummaryResponse =
        AssignmentSummaryResponse(
            id = assignmentId,
            weekNo = 1,
            orderInWeek = 1,
            startAt = Instant.parse("2026-03-03T00:00:00Z"),
            endAt = Instant.parse("2026-03-11T00:00:00Z"),
            status = AssignmentStatus.PUBLISHED,
            metadata = AssignmentMetadataResponse(
                title = "터미널 계산기",
                difficulty = AssignmentDifficulty.MID,
                description = "# 문제 설명",
                requirements = listOf(AssignmentRequirementResponse(1, "함수 분리 필수")),
                learningGoals = listOf(AssignmentLearningGoalResponse(1, "함수 분리")),
                testCases = listOf(
                    AssignmentTestCaseResponse(
                        seq = 1,
                        inputValues = listOf("ADD 1", "CLOSE"),
                        outputText = "+1",
                        visibility = AssignmentTestCaseVisibility.PUBLIC,
                    )
                ),
                codeTemplates = listOf(
                    AssignmentCodeTemplateResponse(
                        language = AssignmentTemplateLanguage.KOTLIN,
                        functionTemplate = "fun solution(): String = \"+1\"",
                    )
                ),
            ),
        )

    private fun sampleAssignmentDetailResponse(): AssignmentDetailResponse =
        AssignmentDetailResponse(
            id = assignmentId,
            courseSlug = "back-basic",
            weekNo = 1,
            orderInWeek = 1,
            startAt = Instant.parse("2026-03-03T00:00:00Z"),
            endAt = Instant.parse("2026-03-11T00:00:00Z"),
            status = AssignmentStatus.PUBLISHED,
            publishedAt = Instant.parse("2026-03-03T00:00:00Z"),
            metadata = AssignmentDetailMetadataResponse(
                title = "터미널 계산기",
                difficulty = AssignmentDifficulty.MID,
                description = "# 문제 설명",
                requirements = listOf(AssignmentRequirementResponse(1, "함수 분리 필수")),
                learningGoals = listOf(AssignmentLearningGoalResponse(1, "함수 분리")),
                testCases = listOf(
                    AssignmentTestCaseResponse(
                        seq = 1,
                        inputValues = listOf("ADD 1", "CLOSE"),
                        outputText = "+1",
                        visibility = AssignmentTestCaseVisibility.PUBLIC,
                    )
                ),
                codeTemplates = listOf(
                    AssignmentCodeTemplateResponse(
                        language = AssignmentTemplateLanguage.KOTLIN,
                        functionTemplate = "fun solution(): String = \"+1\"",
                    )
                ),
            ),
        )
}
