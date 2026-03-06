package com.example.aandi_post_web_server.course.controller

import com.example.aandi_post_web_server.assignment.dtos.AssignmentDeliveryResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.dtos.CreateAssignmentRequest
import com.example.aandi_post_web_server.assignment.dtos.AssignmentMetadataPayload
import com.example.aandi_post_web_server.assignment.enum.AssignmentDeliveryStatus
import com.example.aandi_post_web_server.assignment.enum.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import com.example.aandi_post_web_server.common.security.SecurityConfig
import com.example.aandi_post_web_server.course.dtos.CreateCourseRequest
import com.example.aandi_post_web_server.course.dtos.CourseMetadataPayload
import com.example.aandi_post_web_server.course.dtos.CourseResponse
import com.example.aandi_post_web_server.course.enum.CoursePhase
import com.example.aandi_post_web_server.course.enum.CourseStatus
import com.example.aandi_post_web_server.course.enum.CourseTrack
import com.example.aandi_post_web_server.course.enum.UserTrack
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
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.LocalDate

@WebFluxTest(controllers = [CourseV1Controller::class, CourseQueryV1Controller::class])
@Import(SecurityConfig::class)
class CourseApiRoutingWebFluxTest : StringSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockBean
    private lateinit var courseV1Service: CourseV1Service

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
            Mockito.`when`(courseV1Service.getCourses(null, null, null, userId)).thenReturn(Flux.just(response))

            webTestClient.mutateWith(
                mockJwt().jwt { jwt ->
                    jwt.subject(userId)
                }.authorities(SimpleGrantedAuthority("ROLE_USER")),
            ).get()
                .uri("/v1/courses")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$[0].slug").isEqualTo("back-basic")
        }

        "과제 상세 조회 API는 토큰이 없으면 401을 반환한다" {
            webTestClient.get()
                .uri("/v1/courses/back-basic/assignments/assignment-1")
                .exchange()
                .expectStatus().isUnauthorized
        }

        "과제 상세 조회 API는 USER 토큰으로 호출하면 성공한다" {
            val response = sampleAssignmentDetailResponse()
            val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
            Mockito.`when`(courseV1Service.getAssignmentDetail("back-basic", "assignment-1", userId))
                .thenReturn(Mono.just(response))

            webTestClient.mutateWith(
                mockJwt().jwt { jwt ->
                    jwt.subject(userId)
                }.authorities(SimpleGrantedAuthority("ROLE_USER")),
            ).get()
                .uri("/v1/courses/back-basic/assignments/assignment-1")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo("assignment-1")
        }

        "과제 ID로 코스 조회 API는 USER 토큰으로 호출하면 성공한다" {
            val response = sampleCourseResponse()
            val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
            Mockito.`when`(courseV1Service.getAssignmentCourse("assignment-1", userId))
                .thenReturn(Mono.just(response))

            webTestClient.mutateWith(
                mockJwt().jwt { jwt ->
                    jwt.subject(userId)
                }.authorities(SimpleGrantedAuthority("ROLE_USER")),
            ).get()
                .uri("/v1/courses/assignments/assignment-1/course")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.slug").isEqualTo("back-basic")
        }

        "코스 조회 API는 track 쿼리 파라미터로 필터링 호출한다" {
            val response = sampleCourseResponse()
            val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
            Mockito.`when`(
                courseV1Service.getCourses(
                    status = null,
                    phase = null,
                    track = UserTrack.FL,
                    userId = userId,
                )
            ).thenReturn(Flux.just(response))

            webTestClient.mutateWith(
                mockJwt().jwt { jwt ->
                    jwt.subject(userId)
                }.authorities(SimpleGrantedAuthority("ROLE_USER")),
            ).get()
                .uri("/v1/courses?track=FL")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$[0].targetTrack").isEqualTo("FL")
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
                .jsonPath("$[0].slug").isEqualTo("back-basic")
        }

        "admin 전체 코스 조회 API는 ADMIN이 아니면 403을 반환한다" {
            webTestClient.mutateWith(
                mockJwt().authorities(SimpleGrantedAuthority("ROLE_USER")),
            ).get()
                .uri("/v1/admin/courses")
                .exchange()
                .expectStatus().isForbidden
        }

        "admin API는 ADMIN이 아니면 403을 반환한다" {
            webTestClient.mutateWith(
                mockJwt().authorities(SimpleGrantedAuthority("ROLE_USER")),
            ).post()
                .uri("/v1/admin/courses")
                .bodyValue(
                    mapOf(
                        "title" to "BACK 기초",
                        "slug" to "back-basic",
                        "description" to "desc",
                        "phase" to "BASIC",
                        "targetTrack" to "FL",
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
                        "title" to "BACK 기초",
                        "slug" to "back-basic",
                        "description" to "desc",
                        "phase" to "BASIC",
                        "targetTrack" to "FL",
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
                .jsonPath("$.slug").isEqualTo("back-basic")
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
                    timeLimitMinutes = 60,
                ),
                requirement = emptyList(),
                exampleIO = emptyList(),
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
                            "timeLimitMinutes" to 60,
                        ),
                        "requirement" to emptyList<Map<String, Any>>(),
                        "exampleIO" to emptyList<Map<String, Any>>(),
                    ),
                )
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo("assignment-1")

            Mockito.verify(courseV1Service).createAssignment(
                "back-basic",
                request,
                "8ee88b63-526d-49dc-9e72-a96be0f81385",
            )
        }

        "admin 배포 조회 API는 ADMIN이 아니면 403을 반환한다" {
            webTestClient.mutateWith(
                mockJwt().authorities(SimpleGrantedAuthority("ROLE_USER")),
            ).get()
                .uri("/v1/admin/courses/back-basic/assignments/assignment-1/deliveries?status=DELIVERED")
                .exchange()
                .expectStatus().isForbidden
        }

        "admin 배포 조회 API는 ADMIN 토큰으로 호출하면 성공한다" {
            Mockito.`when`(
                courseV1Service.getDeliveries(
                    "back-basic",
                    "assignment-1",
                    AssignmentDeliveryStatus.DELIVERED,
                )
            ).thenReturn(
                Flux.just(
                    AssignmentDeliveryResponse(
                        userId = "user-1",
                        status = AssignmentDeliveryStatus.DELIVERED,
                        deliveredAt = Instant.parse("2026-03-01T00:00:00Z"),
                        failureReason = null,
                    )
                )
            )

            webTestClient.mutateWith(
                mockJwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN")),
            ).get()
                .uri("/v1/admin/courses/back-basic/assignments/assignment-1/deliveries?status=DELIVERED")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$[0].userId").isEqualTo("user-1")
        }
    }
}

private fun sampleCourseResponse(): CourseResponse {
    val now = Instant.parse("2026-02-20T00:00:00Z")
    return CourseResponse(
        id = "course-1",
        title = "BACK 기초",
        slug = "back-basic",
        description = "desc",
        phase = CoursePhase.BASIC,
        targetTrack = CourseTrack.FL,
        status = CourseStatus.ACTIVE,
        createdAt = now,
        updatedAt = now,
    )
}

private fun sampleAssignmentDetailResponse(): AssignmentDetailResponse {
    val now = Instant.parse("2026-03-01T00:00:00Z")
    return AssignmentDetailResponse(
        id = "assignment-1",
        courseSlug = "back-basic",
        weekNo = 1,
        seqInWeek = 1,
        title = "터미널 계산기",
        difficulty = AssignmentDifficulty.MID,
        contentMd = "# 문제 설명",
        timeLimitMinutes = 60,
        openAt = now,
        dueAt = now.plusSeconds(3600),
        status = AssignmentStatus.PUBLISHED,
        publishedAt = now,
        requirements = emptyList(),
        examples = emptyList(),
    )
}
