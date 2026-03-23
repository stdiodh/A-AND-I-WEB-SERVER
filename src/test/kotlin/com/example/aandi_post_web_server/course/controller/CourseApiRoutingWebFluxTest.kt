@file:Suppress("DEPRECATION")

package com.example.aandi_post_web_server.course.controller

import com.example.aandi_post_web_server.assignment.dtos.AssignmentDetailMetadataResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentSummaryResponse
import com.example.aandi_post_web_server.assignment.dtos.CreateAssignmentRequest
import com.example.aandi_post_web_server.assignment.dtos.AssignmentProblemClassificationResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentProblemDetailResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentMetadataPayload
import com.example.aandi_post_web_server.assignment.dtos.AssignmentMetadataResponse
import com.example.aandi_post_web_server.assignment.dtos.UpdateAssignmentRequest
import com.example.aandi_post_web_server.assignment.enum.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.enum.AssignmentProblemStep
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import com.example.aandi_post_web_server.common.config.WebConfig
import com.example.aandi_post_web_server.common.error.ErrorResponseFactory
import com.example.aandi_post_web_server.common.security.SecurityConfig
import com.example.aandi_post_web_server.course.dtos.CreateCourseRequest
import com.example.aandi_post_web_server.course.dtos.CourseOutlineAssignmentItemResponse
import com.example.aandi_post_web_server.course.dtos.CourseOutlineHeaderResponse
import com.example.aandi_post_web_server.course.dtos.CourseOutlineResponse
import com.example.aandi_post_web_server.course.dtos.CourseMetadataResponse
import com.example.aandi_post_web_server.course.dtos.CourseMetadataPayload
import com.example.aandi_post_web_server.course.dtos.CourseResponse
import com.example.aandi_post_web_server.course.enum.CoursePhase
import com.example.aandi_post_web_server.course.enum.CourseStatus
import com.example.aandi_post_web_server.course.enum.CourseTrack
import com.example.aandi_post_web_server.course.service.CourseV1Service
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
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
import java.time.Instant
import java.time.LocalDate

@WebFluxTest(controllers = [CourseV1Controller::class, CourseQueryV1Controller::class])
@Import(WebConfig::class, SecurityConfig::class, ErrorResponseFactory::class)
class CourseApiRoutingWebFluxTest : StringSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockBean
    private lateinit var courseV1Service: CourseV1Service

    private val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"

    init {
        beforeTest {
            Mockito.reset(courseV1Service)
        }

        "코스 조회 API는 토큰이 없으면 401을 반환한다" {
            webTestClient.get()
                .uri("/v1/courses")
                .exchange()
                .expectStatus().isUnauthorized
        }

        "코스 조회 API는 USER 토큰으로 호출하면 성공한다" {
            val response = sampleCourseResponse()
            val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
            Mockito.`when`(courseV1Service.getCourses(userId)).thenReturn(Flux.just(response))

            webTestClient.mutateWith(
                mockJwt().jwt { jwt ->
                    jwt.subject(userId)
                }.authorities(SimpleGrantedAuthority("ROLE_USER")),
            ).get()
                .uri("/v1/courses")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.data[0].slug").isEqualTo("back-basic")
        }

        "과제 상세 조회 API는 토큰이 없으면 401을 반환한다" {
            webTestClient.get()
                .uri("/v1/courses/back-basic/assignments/$assignmentId")
                .exchange()
                .expectStatus().isUnauthorized
        }

        "과제 상세 조회 API는 USER 토큰으로 호출하면 성공한다" {
            val response = sampleAssignmentDetailResponse()
            val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
            Mockito.`when`(courseV1Service.getAssignmentDetail("back-basic", assignmentId, userId))
                .thenReturn(Mono.just(response))

            webTestClient.mutateWith(
                mockJwt().jwt { jwt ->
                    jwt.subject(userId)
                }.authorities(SimpleGrantedAuthority("ROLE_USER")),
            ).get()
                .uri("/v1/courses/back-basic/assignments/$assignmentId")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.data.assignmentId").isEqualTo(assignmentId)
                .jsonPath("$.data.metadata.problemDetail.classification.algorithmStep").isEqualTo("STEP0")
                .jsonPath("$.data.metadata.codeTemplates").doesNotExist()
                .jsonPath("$.data.metadata.submissionGuide").doesNotExist()
        }

        "과제 ID로 코스 조회 API는 USER 토큰으로 호출하면 성공한다" {
            val response = sampleCourseResponse()
            val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
            Mockito.`when`(courseV1Service.getAssignmentCourse(assignmentId, userId))
                .thenReturn(Mono.just(response))

            webTestClient.mutateWith(
                mockJwt().jwt { jwt ->
                    jwt.subject(userId)
                }.authorities(SimpleGrantedAuthority("ROLE_USER")),
            ).get()
                .uri("/v1/courses/assignments/$assignmentId/course")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.data.slug").isEqualTo("back-basic")
        }

        "코스 목차 요약 API는 USER 토큰으로 호출하면 성공한다" {
            val response = sampleCourseOutlineResponse()
            val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
            Mockito.`when`(courseV1Service.getCourseOutline("back-basic", userId))
                .thenReturn(Mono.just(response))

            webTestClient.mutateWith(
                mockJwt().jwt { jwt ->
                    jwt.subject(userId)
                }.authorities(SimpleGrantedAuthority("ROLE_USER")),
            ).get()
                .uri("/v1/courses/back-basic/outline")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.data.course.slug").isEqualTo("back-basic")
                .jsonPath("$.data.assignments[0].checked").isEqualTo(true)
        }

        "admin 전체 코스 조회 API는 ADMIN 토큰으로 호출하면 성공한다" {
            val response = sampleCourseResponse()
            Mockito.`when`(courseV1Service.getAdminCourses()).thenReturn(Flux.just(response))

            webTestClient.mutateWith(
                mockJwt().jwt { jwt ->
                    jwt.subject("8ee88b63-526d-49dc-9e72-a96be0f81385")
                }.authorities(SimpleGrantedAuthority("ROLE_ADMIN")),
            ).get()
                .uri("/v1/admin/courses")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.data[0].slug").isEqualTo("back-basic")
                .consumeWith { result ->
                    String(result.responseBody ?: ByteArray(0)).contains("\"error\":null") shouldBe true
                }
        }

        "admin 전체 코스 조회 API는 ADMIN이 아니면 403을 반환한다" {
            webTestClient.mutateWith(
                mockJwt().authorities(SimpleGrantedAuthority("ROLE_USER")),
            ).get()
                .uri("/v1/admin/courses")
                .exchange()
                .expectStatus().isForbidden
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error.code").isEqualTo("FORBIDDEN")
        }

        "admin API는 ADMIN이 아니면 403을 반환한다" {
            webTestClient.mutateWith(
                mockJwt().authorities(SimpleGrantedAuthority("ROLE_USER")),
            ).post()
                .uri("/v1/admin/courses")
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
                    )
                )
                .exchange()
                .expectStatus().isForbidden
        }

        "admin API는 토큰이 없으면 401을 반환한다" {
            webTestClient.post()
                .uri("/v1/admin/courses")
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

        "admin API는 ADMIN 헤더로 호출하면 성공한다" {
            val response = sampleCourseResponse()
            val startDate = LocalDate.parse("2026-03-01")
            val endDate = LocalDate.parse("2026-03-28")
            val request = CreateCourseRequest(
                slug = "back-basic",
                fieldTag = CourseTrack.FL,
                startDate = startDate,
                endDate = endDate,
                metadata = CourseMetadataPayload(
                    title = "BACK 기초",
                    description = "desc",
                    phase = CoursePhase.BASIC,
                ),
            )
            Mockito.`when`(
                courseV1Service.createCourse(request)
            ).thenReturn(Mono.just(response))

            webTestClient.mutateWith(
                mockJwt().jwt { jwt ->
                    jwt.subject("8ee88b63-526d-49dc-9e72-a96be0f81385")
                }.authorities(SimpleGrantedAuthority("ROLE_ADMIN")),
            ).post()
                .uri("/v1/admin/courses")
                .bodyValue(
                    mapOf(
                        "slug" to "back-basic",
                        "fieldTag" to "FL",
                        "startDate" to startDate.toString(),
                        "endDate" to endDate.toString(),
                        "metadata" to mapOf(
                            "title" to "BACK 기초",
                            "description" to "desc",
                            "phase" to "BASIC",
                        ),
                    )
                )
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.data.slug").isEqualTo("back-basic")
        }

        "admin 수강생 삭제 API는 ADMIN 토큰으로 호출하면 성공한다" {
            Mockito.`when`(courseV1Service.deleteEnrollment("back-basic", "user-1")).thenReturn(Mono.empty())

            webTestClient.mutateWith(
                mockJwt().jwt { jwt ->
                    jwt.subject("8ee88b63-526d-49dc-9e72-a96be0f81385")
                }.authorities(SimpleGrantedAuthority("ROLE_ADMIN")),
            ).delete()
                .uri("/v1/admin/courses/back-basic/enrollments/user-1")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").isEmpty
        }

        "과제 생성은 JWT subject를 createdBy로 전달한다" {
            val response = sampleAssignmentDetailResponse()
            val request = CreateAssignmentRequest(
                weekNo = 1,
                orderInWeek = 1,
                startAt = Instant.parse("2026-03-01T00:00:00Z"),
                endAt = Instant.parse("2026-03-02T00:00:00Z"),
                metadata = AssignmentMetadataPayload(
                    title = "터미널 계산기",
                    difficulty = AssignmentDifficulty.MID,
                    description = "문제 본문",
                ),
            )
            Mockito.`when`(
                courseV1Service.createAssignment(
                    "back-basic",
                    request,
                    "8ee88b63-526d-49dc-9e72-a96be0f81385",
                ),
            ).thenReturn(Mono.just(response))

            webTestClient.mutateWith(
                mockJwt().jwt { jwt ->
                    jwt.subject("8ee88b63-526d-49dc-9e72-a96be0f81385")
                }.authorities(SimpleGrantedAuthority("ROLE_ADMIN")),
            ).post()
                .uri("/v1/admin/courses/back-basic/assignments")
                .bodyValue(
                    mapOf(
                        "weekNo" to 1,
                        "orderInWeek" to 1,
                        "startAt" to "2026-03-01T00:00:00Z",
                        "endAt" to "2026-03-02T00:00:00Z",
                        "metadata" to mapOf(
                            "title" to "터미널 계산기",
                            "difficulty" to "MID",
                            "description" to "문제 본문",
                            "requirements" to emptyList<Map<String, Any>>(),
                            "learningGoals" to emptyList<Map<String, Any>>(),
                            "examples" to emptyList<Map<String, Any>>(),
                        ),
                    ),
                )
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.data.assignmentId").isEqualTo(assignmentId)

            Mockito.verify(courseV1Service).createAssignment(
                "back-basic",
                request,
                "8ee88b63-526d-49dc-9e72-a96be0f81385",
            )
        }

        "admin 과제 목록 조회 API는 ADMIN 토큰으로 호출하면 성공한다" {
            Mockito.`when`(
                courseV1Service.getAdminAssignments(
                    courseSlug = "back-basic",
                    weekNo = 1,
                    status = AssignmentStatus.DRAFT,
                )
            ).thenReturn(
                Flux.just(
                    AssignmentSummaryResponse(
                        id = assignmentId,
                        weekNo = 1,
                        orderInWeek = 1,
                        startAt = Instant.parse("2026-03-01T00:00:00Z"),
                        endAt = Instant.parse("2026-03-02T00:00:00Z"),
                        status = AssignmentStatus.DRAFT,
                        metadata = AssignmentMetadataResponse(
                            title = "터미널 계산기",
                            difficulty = AssignmentDifficulty.MID,
                            description = "문제",
                            attributes = emptyMap(),
                        ),
                    )
                )
            )

            webTestClient.mutateWith(
                mockJwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN")),
            ).get()
                .uri("/v1/admin/courses/back-basic/assignments?weekNo=1&status=DRAFT")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.data[0].assignmentId").isEqualTo(assignmentId)
                .jsonPath("$.data[0].status").isEqualTo("DRAFT")
        }

        "admin 과제 상세 조회 API는 ADMIN 토큰으로 호출하면 성공한다" {
            val response = sampleAssignmentDetailResponse()
            Mockito.`when`(courseV1Service.getAdminAssignmentDetail("back-basic", assignmentId))
                .thenReturn(Mono.just(response))

            webTestClient.mutateWith(
                mockJwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN")),
            ).get()
                .uri("/v1/admin/courses/back-basic/assignments/$assignmentId")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.data.assignmentId").isEqualTo(assignmentId)
                .jsonPath("$.data.metadata.problemDetail.outputDescription").isEqualTo("Hello World!를 출력한다.")
                .jsonPath("$.data.metadata.codeTemplates").doesNotExist()
                .jsonPath("$.data.metadata.submissionGuide").doesNotExist()
        }

        "admin 과제 수정 API는 ADMIN이 아니면 403을 반환한다" {
            webTestClient.mutateWith(
                mockJwt().authorities(SimpleGrantedAuthority("ROLE_USER")),
            ).patch()
                .uri("/v1/admin/courses/back-basic/assignments/$assignmentId")
                .bodyValue(mapOf("orderInWeek" to 2))
                .exchange()
                .expectStatus().isForbidden
        }

        "admin 과제 삭제 API는 ADMIN 토큰으로 호출하면 성공한다" {
            Mockito.`when`(courseV1Service.deleteAssignment("back-basic", assignmentId))
                .thenReturn(Mono.empty())

            webTestClient.mutateWith(
                mockJwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN")),
            ).delete()
                .uri("/v1/admin/courses/back-basic/assignments/$assignmentId")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
        }

        "admin 과제 수정 API는 ADMIN 토큰으로 호출하면 성공한다" {
            val response = sampleAssignmentDetailResponse()
            val request = UpdateAssignmentRequest(
                orderInWeek = 2,
            )
            Mockito.`when`(
                courseV1Service.updateAssignment(
                    courseSlug = "back-basic",
                    assignmentId = assignmentId,
                    request = request,
                )
            ).thenReturn(Mono.just(response))

            webTestClient.mutateWith(
                mockJwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN")),
            ).patch()
                .uri("/v1/admin/courses/back-basic/assignments/$assignmentId")
                .bodyValue(mapOf("orderInWeek" to 2))
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.data.assignmentId").isEqualTo(assignmentId)
        }

    }
}

private fun sampleCourseResponse(): CourseResponse {
    val now = Instant.parse("2026-02-20T00:00:00Z")
    return CourseResponse(
        id = "course-1",
        slug = "back-basic",
        fieldTag = CourseTrack.FL,
        startDate = LocalDate.parse("2026-03-01"),
        endDate = LocalDate.parse("2026-03-28"),
        metadata = CourseMetadataResponse(
            title = "BACK 기초",
            description = "desc",
            phase = CoursePhase.BASIC,
            attributes = emptyMap(),
        ),
        status = CourseStatus.PUBLISHED,
        createdAt = now,
        updatedAt = now,
    )
}

private fun sampleAssignmentDetailResponse(): AssignmentDetailResponse {
    val now = Instant.parse("2026-03-01T00:00:00Z")
    return AssignmentDetailResponse(
        id = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111",
        courseSlug = "back-basic",
        weekNo = 1,
        orderInWeek = 1,
        startAt = now,
        endAt = now.plusSeconds(3600),
        status = AssignmentStatus.PUBLISHED,
        publishedAt = now,
        metadata = AssignmentDetailMetadataResponse(
            title = "터미널 계산기",
            difficulty = AssignmentDifficulty.MID,
            description = "# 문제 설명",
            problemDetail = AssignmentProblemDetailResponse(
                inputDescription = "입력이 없다.",
                outputDescription = "Hello World!를 출력한다.",
                classification = AssignmentProblemClassificationResponse(
                    algorithmStep = AssignmentProblemStep.STEP0,
                    difficultyStep = 1,
                ),
            ),
            attributes = emptyMap(),
        ),
    )
}

private fun sampleCourseOutlineResponse(): CourseOutlineResponse {
    return CourseOutlineResponse(
        course = CourseOutlineHeaderResponse(
            id = "course-1",
            slug = "back-basic",
            fieldTag = CourseTrack.FL,
            title = "BACK 기초",
            description = "desc",
            phase = CoursePhase.BASIC,
        ),
        totalAssignments = 1,
        assignments = listOf(
            CourseOutlineAssignmentItemResponse(
                assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111",
                weekNo = 1,
                orderInWeek = 1,
                title = "터미널 계산기",
                difficulty = AssignmentDifficulty.MID,
                startAt = Instant.parse("2026-03-01T00:00:00Z"),
                endAt = Instant.parse("2026-03-02T00:00:00Z"),
                checked = true,
            )
        ),
    )
}
