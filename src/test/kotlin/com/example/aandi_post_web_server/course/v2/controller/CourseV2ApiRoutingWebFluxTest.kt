@file:Suppress("DEPRECATION")

package com.example.aandi_post_web_server.course.api.v2.controller

import com.example.aandi_post_web_server.assignment.api.dto.AssignmentCodeTemplateResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentDetailMetadataResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentLearningGoalResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentMetadataPayload
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentRequirementResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentTestCaseResponse
import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentRequest
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTemplateLanguage
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseVisibility
import com.example.aandi_post_web_server.common.config.WebConfig
import com.example.aandi_post_web_server.common.error.ErrorResponseFactory
import com.example.aandi_post_web_server.common.error.GlobalApiExceptionHandler
import com.example.aandi_post_web_server.common.error.GlobalWebExceptionHandler
import com.example.aandi_post_web_server.common.security.SecurityConfig
import com.example.aandi_post_web_server.course.api.v1.controller.CourseQueryV1Controller
import com.example.aandi_post_web_server.course.api.dto.CourseMetadataPayload
import com.example.aandi_post_web_server.course.api.dto.CourseMetadataResponse
import com.example.aandi_post_web_server.course.api.dto.CourseResponse
import com.example.aandi_post_web_server.course.domain.model.CoursePhase
import com.example.aandi_post_web_server.course.domain.model.CourseStatus
import com.example.aandi_post_web_server.course.domain.model.CourseTrack
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
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.LocalDate
import com.example.aandi_post_web_server.support.TestJwtFactory

@WebFluxTest(
    controllers = [
        CourseQueryV1Controller::class,
        CourseQueryV2Controller::class,
        AssignmentCourseV2Controller::class,
        CourseAdminV2Controller::class,
    ],
)
@Import(
    WebConfig::class,
    SecurityConfig::class,
    ErrorResponseFactory::class,
    GlobalApiExceptionHandler::class,
    GlobalWebExceptionHandler::class,
)
class CourseV2ApiRoutingWebFluxTest : StringSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockBean
    private lateinit var courseV1Service: CourseV1Service

    private val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
    private val adminId = "1fd3abf7-5ea4-403f-bcf8-8b3f9d8df502"
    private val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"

    init {
        beforeTest {
            Mockito.reset(courseV1Service)
        }

        "v2 코스 조회 API는 토큰이 없으면 401을 반환한다" {
            webTestClient.get()
                .uri("/v2/courses")
                .exchange()
                .expectStatus().isUnauthorized
        }

        "v2 코스 조회 API는 USER 토큰으로 호출하면 성공한다" {
            Mockito.`when`(courseV1Service.getCourses(userId)).thenReturn(Flux.just(sampleCourseResponse()))

            v2UserClient().get()
                .uri("/v2/courses")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data[0].slug").isEqualTo("back-basic")
                .jsonPath("$.error").isEmpty
        }

        "v1과 v2 코스 상세 조회는 동일한 비즈니스 응답 필드를 유지한다" {
            Mockito.`when`(courseV1Service.getCourse("back-basic", userId))
                .thenReturn(Mono.just(sampleCourseResponse()))

            v1UserClient().get()
                .uri("/v1/courses/back-basic")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.slug").isEqualTo("back-basic")
                .jsonPath("$.data.status").isEqualTo("PUBLISHED")

            v2UserClient().get()
                .uri("/v2/courses/back-basic")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.slug").isEqualTo("back-basic")
                .jsonPath("$.data.status").isEqualTo("PUBLISHED")
        }

        "v2 과제 ID로 코스 조회 API는 USER 토큰으로 호출하면 성공한다" {
            Mockito.`when`(courseV1Service.getAssignmentCourse(assignmentId, userId))
                .thenReturn(Mono.just(sampleCourseResponse()))

            v2UserClient().get()
                .uri("/v2/assignments/$assignmentId/course")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.data.slug").isEqualTo("back-basic")
        }

        "v2 코스 상세 조회에서 서비스 404는 공통 envelope로 전달된다" {
            Mockito.`when`(courseV1Service.getCourse("back-basic", userId))
                .thenReturn(Mono.error(ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "코스를 찾을 수 없습니다.")))

            v2UserClient().get()
                .uri("/v2/courses/back-basic")
                .exchange()
                .expectStatus().isNotFound
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error.code").isEqualTo(96501)
                .jsonPath("$.timestamp").exists()
        }

        "v2 admin 전체 코스 조회 API는 ADMIN 토큰으로 호출하면 성공한다" {
            Mockito.`when`(courseV1Service.getAdminCourses()).thenReturn(Flux.just(sampleCourseResponse()))

            v2AdminClient().get()
                .uri("/v2/admin/courses")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data[0].slug").isEqualTo("back-basic")
        }

        "v2 admin 전체 코스 조회 API는 ADMIN이 아니면 403을 반환한다" {
            v2UserClient().get()
                .uri("/v2/admin/courses")
                .exchange()
                .expectStatus().isForbidden
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error.code").isEqualTo(21201)
        }

        "v2 admin API는 토큰이 없으면 401을 반환한다" {
            webTestClient.post()
                .uri("/v2/admin/courses")
                .bodyValue(
                    mapOf(
                        "slug" to "back-basic",
                        "fieldTag" to "FL",
                        "startDate" to "2026-03-01",
                        "endDate" to "2026-03-28",
                        "metadata" to mapOf(
                            "title" to "BACK 기초",
                            "description" to "desc",
                            "phase" to "BASIC",
                        ),
                    ),
                )
                .exchange()
                .expectStatus().isUnauthorized
        }

        "v2 admin 과제 생성 API는 ADMIN 토큰으로 호출하면 성공한다" {
            val request = CreateAssignmentRequest(
                weekNo = 1,
                orderInWeek = 1,
                startAt = Instant.parse("2026-03-03T00:00:00Z"),
                endAt = Instant.parse("2026-03-11T00:00:00Z"),
                metadata = AssignmentMetadataPayload(
                    title = "터미널 계산기",
                    difficulty = AssignmentDifficulty.MID,
                    description = "# 문제 설명",
                    requirements = emptyList(),
                    learningGoals = emptyList(),
                    testCases = emptyList(),
                    codeTemplates = emptyList(),
                ),
            )
            Mockito.`when`(courseV1Service.createAssignment("back-basic", request, adminId))
                .thenReturn(Mono.just(sampleAssignmentDetailResponse()))

            v2AdminClient().post()
                .uri("/v2/admin/courses/back-basic/assignments")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.assignmentId").isEqualTo(assignmentId)
                .jsonPath("$.data.courseSlug").isEqualTo("back-basic")
        }

        "v2 코스 조회 API는 공통 헤더가 누락되면 40301을 반환한다" {
            webTestClient.mutate()
                .defaultHeader("Authorization", "Bearer ${TestJwtFactory.createAccessToken(userId, "USER")}")
                .defaultHeader("timestamp", "2026-04-13T18:00:00+09:00")
                .build()
                .get()
                .uri("/v2/courses")
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error.code").isEqualTo(40301)
        }
    }

    private fun v1UserClient(): WebTestClient =
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
            .build()

    private fun v2UserClient(): WebTestClient =
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

    private fun v2AdminClient(): WebTestClient =
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

    private fun sampleCourseResponse(): CourseResponse =
        CourseResponse(
            id = "course-1",
            slug = "back-basic",
            fieldTag = CourseTrack.FL,
            startDate = LocalDate.parse("2026-03-01"),
            endDate = LocalDate.parse("2026-03-28"),
            metadata = CourseMetadataResponse(
                title = "BACK 기초",
                description = "서버 기초 과정",
                phase = CoursePhase.BASIC,
                attributes = emptyMap(),
            ),
            status = CourseStatus.PUBLISHED,
            createdAt = Instant.parse("2026-03-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-03-01T00:00:00Z"),
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
                    ),
                ),
                codeTemplates = listOf(
                    AssignmentCodeTemplateResponse(
                        language = AssignmentTemplateLanguage.KOTLIN,
                        functionTemplate = "fun solution(): String = \"+1\"",
                    ),
                ),
            ),
        )
}
