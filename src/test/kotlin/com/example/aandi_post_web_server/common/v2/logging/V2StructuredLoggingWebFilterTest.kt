@file:Suppress("DEPRECATION")

package com.example.aandi_post_web_server.common.logging.v2

import ch.qos.logback.classic.Logger
import ch.qos.logback.core.read.ListAppender
import com.example.aandi_post_web_server.Application
import com.example.aandi_post_web_server.common.config.WebConfig
import com.example.aandi_post_web_server.common.error.ErrorResponseFactory
import com.example.aandi_post_web_server.common.error.GlobalApiExceptionHandler
import com.example.aandi_post_web_server.common.error.GlobalWebExceptionHandler
import com.example.aandi_post_web_server.common.error.RequestIdWebFilter
import com.example.aandi_post_web_server.common.security.SecurityConfig
import com.example.aandi_post_web_server.course.api.v1.controller.CourseV1Controller
import com.example.aandi_post_web_server.course.api.v1.controller.CourseQueryV1Controller
import com.example.aandi_post_web_server.course.api.dto.CreateCourseRequest
import com.example.aandi_post_web_server.course.api.dto.CourseMetadataResponse
import com.example.aandi_post_web_server.course.api.dto.CourseMetadataPayload
import com.example.aandi_post_web_server.course.api.dto.CourseResponse
import com.example.aandi_post_web_server.course.domain.model.CoursePhase
import com.example.aandi_post_web_server.course.domain.model.CourseStatus
import com.example.aandi_post_web_server.course.domain.model.CourseTrack
import com.example.aandi_post_web_server.course.application.service.CourseV1Service
import com.example.aandi_post_web_server.course.api.v2.controller.CourseAdminV2Controller
import com.example.aandi_post_web_server.course.api.v2.controller.CourseQueryV2Controller
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.LocalDate

@WebFluxTest(
    controllers = [
        CourseV1Controller::class,
        CourseQueryV1Controller::class,
        CourseQueryV2Controller::class,
        CourseAdminV2Controller::class,
    ],
)
@ContextConfiguration(classes = [Application::class])
@Import(
    WebConfig::class,
    SecurityConfig::class,
    ErrorResponseFactory::class,
    GlobalApiExceptionHandler::class,
    GlobalWebExceptionHandler::class,
    RequestIdWebFilter::class,
    V2StructuredLoggingConfig::class,
)
@TestPropertySource(
    properties = [
        "app.logging.v2.env=test",
        "app.logging.v2.service.name=report-service",
        "app.logging.v2.service.domain-code=4",
        "app.logging.v2.service.version=test-version",
        "app.logging.v2.service.instance-id=test-instance",
        "app.logging.v2.max-body-bytes=16384",
        "app.logging.v2.include-path-prefixes=/v2,/api/v2",
    ],
)
class V2StructuredLoggingWebFilterTest : StringSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var courseV1Service: CourseV1Service

    private val structuredLogger = LoggerFactory.getLogger(V2StructuredLoggingWebFilter.STRUCTURED_LOGGER_NAME) as Logger
    private val listAppender = ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>()
    private val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"

    init {
        beforeSpec {
            listAppender.start()
            structuredLogger.addAppender(listAppender)
        }

        afterSpec {
            structuredLogger.detachAppender(listAppender)
            listAppender.stop()
        }

        beforeTest {
            Mockito.reset(courseV1Service)
            listAppender.list.clear()
        }

        "v2 성공 요청은 계약 JSON INFO 로그를 남긴다" {
            val request = CreateCourseRequest(
                slug = "back-basic",
                fieldTag = CourseTrack.SP,
                startDate = LocalDate.parse("2026-03-02"),
                endDate = LocalDate.parse("2026-03-30"),
                metadata = CourseMetadataPayload(
                    title = "BACK 기초",
                    description = "서버 기초",
                    phase = CoursePhase.BASIC,
                    attributes = mapOf("loginId" to "han12345"),
                ),
            )
            Mockito.`when`(courseV1Service.createCourse(request))
                .thenReturn(Mono.just(sampleCourseResponse()))

            v2AdminClient().post()
                .uri("/v2/admin/courses")
                .header("deviceOS", "ios")
                .header("timestamp", "2026-04-15T10:15:30+09:00")
                .header("X-Trace-Id", "abc123trace")
                .header(HttpHeaders.USER_AGENT, "A&IApp/2.0.1 (iPhone; iOS 18.1)")
                .bodyValue(
                    request,
                )
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)

            listAppender.list shouldHaveSize 1
            val payload = parseLoggedJson()

            payload["level"].asText() shouldBe "INFO"
            payload["logType"].asText() shouldBe "API"
            payload["service"]["name"].asText() shouldBe "report-service"
            payload["service"]["domainCode"].asInt() shouldBe 4
            payload["http"]["path"].asText() shouldBe "/v2/admin/courses"
            payload["http"]["route"].asText() shouldBe "/v2/admin/courses"
            payload["headers"]["deviceOS"].asText() shouldBe "ios"
            payload["headers"]["Authenticate"].isNull shouldBe true
            payload["trace"]["traceId"].asText() shouldBe "abc123trace"
            payload["client"]["appVersion"].asText() shouldBe "2.0.1"
            payload["actor"]["userId"].asText() shouldBe userId
            payload["actor"]["role"].asText() shouldBe "ADMIN"
            payload["request"]["body"]["slug"].asText() shouldBe "back-basic"
            payload["request"]["body"]["metadata"]["attributes"]["loginId"].asText() shouldBe "han*****"
            payload["response"]["success"].asBoolean() shouldBe true
            payload["response"]["error"].shouldBeNullNode()
            payload["response"]["data"]["slug"].asText() shouldBe "back-basic"
        }

        "v2 실패 요청은 계약 JSON WARN 로그를 남긴다" {
            Mockito.`when`(courseV1Service.getCourse("missing-course", userId))
                .thenReturn(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "코스를 찾을 수 없습니다.")))

            v2UserClient().get()
                .uri("/v2/courses/missing-course")
                .header("deviceOS", "android")
                .header("timestamp", "2026-04-15T10:17:00+09:00")
                .header(HttpHeaders.USER_AGENT, "A&IApp/2.0.1 (Android 15)")
                .exchange()
                .expectStatus().isNotFound
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)

            listAppender.list shouldHaveSize 1
            val payload = parseLoggedJson()

            payload["level"].asText() shouldBe "WARN"
            payload["logType"].asText() shouldBe "API_ERROR"
            payload["http"]["path"].asText() shouldBe "/v2/courses/missing-course"
            payload["http"]["route"].asText() shouldBe "/v2/courses/{courseSlug}"
            payload["actor"]["role"].asText() shouldBe "USER"
            payload["request"]["body"].shouldBeNullNode()
            payload["response"]["success"].asBoolean() shouldBe false
            payload["response"]["data"].shouldBeNullNode()
            payload["response"]["error"]["code"].asInt() shouldBe 96501
            payload["response"]["error"]["message"].asText() shouldBe "코스를 찾을 수 없습니다."
        }

        "v2 서버 예외 요청은 ERROR API_ERROR 로그를 남긴다" {
            Mockito.`when`(courseV1Service.getCourse("explode", userId))
                .thenReturn(Mono.error(IllegalStateException("unexpected failure")))

            v2UserClient().get()
                .uri("/v2/courses/explode")
                .header("deviceOS", "android")
                .header("timestamp", "2026-04-15T10:17:00+09:00")
                .exchange()
                .expectStatus().is5xxServerError
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)

            listAppender.list shouldHaveSize 1
            val payload = parseLoggedJson()

            payload["level"].asText() shouldBe "ERROR"
            payload["logType"].asText() shouldBe "API_ERROR"
            payload["http"]["statusCode"].asInt() shouldBe 500
            payload["response"]["success"].asBoolean() shouldBe false
            payload["response"]["error"].isNull shouldBe false
        }

        "/api/v2 prefix 요청도 설정에 따라 구조화 로그 대상이다" {
            v2UserClient().get()
                .uri("/api/v2/courses")
                .header("deviceOS", "ios")
                .header("timestamp", "2026-04-15T10:17:00+09:00")
                .exchange()
                .expectStatus().isNotFound

            listAppender.list shouldHaveSize 1
            val payload = parseLoggedJson()

            payload["logType"].asText() shouldBe "API_ERROR"
            payload["http"]["path"].asText() shouldBe "/api/v2/courses"
        }

        "v1 요청은 구조화 v2 로그를 남기지 않는다" {
            Mockito.`when`(courseV1Service.getCourses(userId)).thenReturn(Flux.just(sampleCourseResponse()))

            v1UserClient().get()
                .uri("/v1/courses")
                .exchange()
                .expectStatus().isOk

            listAppender.list shouldHaveSize 0
        }

        "actuator와 swagger 경로는 구조화 v2 로그를 남기지 않는다" {
            webTestClient.get()
                .uri("/actuator/health")
                .exchange()

            webTestClient.get()
                .uri("/swagger-ui/index.html")
                .exchange()

            webTestClient.get()
                .uri("/v3/api-docs/report-v2")
                .exchange()

            listAppender.list shouldHaveSize 0
        }
    }

    private fun parseLoggedJson(): JsonNode =
        objectMapper.readTree(listAppender.list.single().formattedMessage)

    private fun v2UserClient(): WebTestClient =
        webTestClient.mutateWith(
            mockJwt().jwt { jwt ->
                jwt.subject(userId)
            }.authorities(SimpleGrantedAuthority("ROLE_USER")),
        )

    private fun v2AdminClient(): WebTestClient =
        webTestClient.mutateWith(
            mockJwt().jwt { jwt ->
                jwt.subject(userId)
            }.authorities(SimpleGrantedAuthority("ROLE_ADMIN")),
        )

    private fun v1UserClient(): WebTestClient =
        webTestClient.mutateWith(
            mockJwt().jwt { jwt ->
                jwt.subject(userId)
            }.authorities(SimpleGrantedAuthority("ROLE_USER")),
        )

    private fun JsonNode.shouldBeNullNode() {
        isNull shouldBe true
    }

    private fun sampleCourseResponse(): CourseResponse =
        CourseResponse(
            id = "course-1",
            slug = "back-basic",
            fieldTag = CourseTrack.SP,
            startDate = LocalDate.parse("2026-03-02"),
            endDate = LocalDate.parse("2026-03-30"),
            metadata = CourseMetadataResponse(
                title = "BACK 기초",
                description = "서버 기초",
                phase = CoursePhase.BASIC,
                attributes = mapOf("email" to "hood@example.com"),
            ),
            status = CourseStatus.PUBLISHED,
            createdAt = Instant.parse("2026-03-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-03-01T00:00:00Z"),
        )
}
