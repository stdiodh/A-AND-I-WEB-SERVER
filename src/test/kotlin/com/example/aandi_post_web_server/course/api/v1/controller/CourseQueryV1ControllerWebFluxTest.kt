@file:Suppress("DEPRECATION")

package com.example.aandi_post_web_server.course.api.v1.controller

import com.example.aandi_post_web_server.assignment.api.dto.AssignmentMetadataResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentSummaryResponse
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import com.example.aandi_post_web_server.common.config.WebConfig
import com.example.aandi_post_web_server.common.error.ErrorResponseFactory
import com.example.aandi_post_web_server.common.error.GlobalApiExceptionHandler
import com.example.aandi_post_web_server.common.error.GlobalWebExceptionHandler
import com.example.aandi_post_web_server.common.error.RequestIdSupport
import com.example.aandi_post_web_server.common.error.RequestIdWebFilter
import com.example.aandi_post_web_server.common.security.SecurityConfig
import com.example.aandi_post_web_server.course.api.dto.CourseMetadataResponse
import com.example.aandi_post_web_server.course.api.dto.CourseResponse
import com.example.aandi_post_web_server.course.api.dto.CourseWeekResponse
import com.example.aandi_post_web_server.course.application.service.CourseV1Service
import com.example.aandi_post_web_server.course.domain.model.CoursePhase
import com.example.aandi_post_web_server.course.domain.model.CourseStatus
import com.example.aandi_post_web_server.course.domain.model.CourseTrack
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.spring.SpringExtension
import org.mockito.ArgumentMatchers
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
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.LocalDate

@WebFluxTest(controllers = [CourseQueryV1Controller::class])
@Import(
    WebConfig::class,
    SecurityConfig::class,
    RequestIdWebFilter::class,
    ErrorResponseFactory::class,
    GlobalApiExceptionHandler::class,
    GlobalWebExceptionHandler::class,
    com.example.aandi_post_web_server.assignment.application.activation.TestAssignmentActivationConfig::class,
)
class CourseQueryV1ControllerWebFluxTest : StringSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockBean
    private lateinit var courseV1Service: CourseV1Service

    private val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
    private val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"

    init {
        beforeTest {
            Mockito.reset(courseV1Service)
        }

        "코스 주차 목록 API는 JWT subject로 조회한 주차를 envelope로 반환한다" {
            Mockito.`when`(courseV1Service.getWeeks("back-basic", userId)).thenReturn(
                Flux.just(sampleWeek(weekNo = 1, title = "환경 설정"), sampleWeek(weekNo = 2, title = "컬렉션")),
            )

            userClient().get()
                .uri("/v1/courses/back-basic/weeks")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.error").isEmpty
                .jsonPath("$.data.length()").isEqualTo(2)
                .jsonPath("$.data[0].weekNo").isEqualTo(1)
                .jsonPath("$.data[1].title").isEqualTo("컬렉션")

            Mockito.verify(courseV1Service).getWeeks("back-basic", userId)
        }

        "코스 주차 목록이 비어 있으면 빈 배열을 반환한다" {
            Mockito.`when`(courseV1Service.getWeeks("back-basic", userId)).thenReturn(Flux.empty())

            userClient().get()
                .uri("/v1/courses/back-basic/weeks")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.length()").isEqualTo(0)
        }

        "주차별 과제 API는 path weekNo와 status 필터를 서비스에 전달한다" {
            Mockito.`when`(
                courseV1Service.getAssignmentsByWeek("back-basic", 2, AssignmentStatus.PUBLISHED, userId),
            ).thenReturn(Flux.just(sampleAssignment(weekNo = 2)))

            userClient().get()
                .uri("/v1/courses/back-basic/weeks/2/assignments?status=PUBLISHED")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data[0].assignmentId").isEqualTo(assignmentId)
                .jsonPath("$.data[0].weekNo").isEqualTo(2)
                .jsonPath("$.data[0].metadata.title").isEqualTo("터미널 계산기")

            Mockito.verify(courseV1Service)
                .getAssignmentsByWeek("back-basic", 2, AssignmentStatus.PUBLISHED, userId)
        }

        "주차별 과제 API는 status가 없으면 null 필터로 조회한다" {
            Mockito.`when`(courseV1Service.getAssignmentsByWeek("back-basic", 1, null, userId))
                .thenReturn(Flux.empty())

            userClient().get()
                .uri("/v1/courses/back-basic/weeks/1/assignments")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.data.length()").isEqualTo(0)

            Mockito.verify(courseV1Service).getAssignmentsByWeek("back-basic", 1, null, userId)
        }

        "코스 과제 목록 API는 query weekNo와 status를 서비스에 전달한다" {
            Mockito.`when`(
                courseV1Service.getAssignments("back-basic", 3, AssignmentStatus.DRAFT, userId),
            ).thenReturn(Flux.just(sampleAssignment(weekNo = 3, status = AssignmentStatus.DRAFT)))

            userClient().get()
                .uri("/v1/courses/back-basic/assignments?weekNo=3&status=DRAFT")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.data[0].weekNo").isEqualTo(3)
                .jsonPath("$.data[0].status").isEqualTo("DRAFT")

            Mockito.verify(courseV1Service).getAssignments("back-basic", 3, AssignmentStatus.DRAFT, userId)
        }

        "코스 과제 목록 API는 필터가 없으면 weekNo와 status를 null로 전달한다" {
            Mockito.`when`(courseV1Service.getAssignments("back-basic", null, null, userId))
                .thenReturn(Flux.just(sampleAssignment(weekNo = 1)))

            userClient().get()
                .uri("/v1/courses/back-basic/assignments")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.data.length()").isEqualTo(1)

            Mockito.verify(courseV1Service).getAssignments("back-basic", null, null, userId)
        }

        "코스 과제 목록 API는 weekNo가 숫자가 아니면 서비스 호출 없이 INPUT_ERROR를 반환한다" {
            userClient().get()
                .uri("/v1/courses/back-basic/assignments?weekNo=first")
                .header(RequestIdSupport.HEADER_NAME, "req-week-query-001")
                .exchange()
                .expectStatus().isBadRequest
                .expectHeader().valueEquals(RequestIdSupport.HEADER_NAME, "req-week-query-001")
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error.code").isEqualTo("INPUT_ERROR")

            Mockito.verify(courseV1Service, Mockito.never()).getAssignments(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.anyString(),
            )
        }

        "코스 상세 API는 서비스의 404를 공통 에러 envelope로 변환한다" {
            Mockito.`when`(courseV1Service.getCourse("missing", userId)).thenReturn(
                Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "코스를 찾을 수 없습니다: missing")),
            )

            userClient().get()
                .uri("/v1/courses/missing")
                .header(RequestIdSupport.HEADER_NAME, "req-course-404")
                .exchange()
                .expectStatus().isNotFound
                .expectHeader().valueEquals(RequestIdSupport.HEADER_NAME, "req-course-404")
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.data").isEmpty
                .jsonPath("$.error.code").isEqualTo("NOT_FOUND")
                .jsonPath("$.error.message").isEqualTo("코스를 찾을 수 없습니다: missing")
        }

        "코스 상세 API는 JWT subject 기준 코스를 반환한다" {
            Mockito.`when`(courseV1Service.getCourse("back-basic", userId)).thenReturn(Mono.just(sampleCourse()))

            userClient().get()
                .uri("/v1/courses/back-basic")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.data.id").isEqualTo("course-1")
                .jsonPath("$.data.fieldTag").isEqualTo("SP")
                .jsonPath("$.data.metadata.title").isEqualTo("BACK 기초")
        }

        "코스 주차 목록 API는 토큰이 없으면 서비스 호출 없이 401을 반환한다" {
            webTestClient.get()
                .uri("/v1/courses/back-basic/weeks")
                .exchange()
                .expectStatus().isUnauthorized
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("UNAUTHORIZED")

            Mockito.verifyNoInteractions(courseV1Service)
        }
    }

    private fun userClient(): WebTestClient =
        webTestClient.mutateWith(
            mockJwt().jwt { jwt -> jwt.subject(userId) }
                .authorities(SimpleGrantedAuthority("ROLE_USER")),
        )

    private fun sampleWeek(weekNo: Int, title: String): CourseWeekResponse =
        CourseWeekResponse(
            id = "week-$weekNo",
            weekNo = weekNo,
            title = title,
            startDate = LocalDate.parse("2026-03-02").plusWeeks(weekNo - 1L),
            endDate = LocalDate.parse("2026-03-08").plusWeeks(weekNo - 1L),
            createdAt = Instant.parse("2026-02-20T00:00:00Z"),
            updatedAt = Instant.parse("2026-02-20T00:00:00Z"),
        )

    private fun sampleAssignment(
        weekNo: Int,
        status: AssignmentStatus = AssignmentStatus.PUBLISHED,
    ): AssignmentSummaryResponse =
        AssignmentSummaryResponse(
            id = assignmentId,
            weekNo = weekNo,
            orderInWeek = 1,
            startAt = Instant.parse("2026-03-01T00:00:00Z"),
            endAt = Instant.parse("2026-03-02T00:00:00Z"),
            status = status,
            metadata = AssignmentMetadataResponse(
                title = "터미널 계산기",
                difficulty = AssignmentDifficulty.MID,
                description = "문제",
            ),
        )

    private fun sampleCourse(): CourseResponse =
        CourseResponse(
            id = "course-1",
            slug = "back-basic",
            fieldTag = CourseTrack.SP,
            startDate = LocalDate.parse("2026-03-02"),
            endDate = LocalDate.parse("2026-03-30"),
            metadata = CourseMetadataResponse(
                title = "BACK 기초",
                description = null,
                phase = CoursePhase.BASIC,
            ),
            status = CourseStatus.PUBLISHED,
            createdAt = Instant.parse("2026-02-20T00:00:00Z"),
            updatedAt = Instant.parse("2026-02-20T00:00:00Z"),
        )
}
