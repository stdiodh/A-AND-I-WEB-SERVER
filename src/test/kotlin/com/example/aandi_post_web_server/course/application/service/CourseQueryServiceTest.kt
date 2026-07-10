package com.example.aandi_post_web_server.course.application.service

import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.entity.AssignmentCodeTemplate
import com.example.aandi_post_web_server.assignment.entity.AssignmentTestCase
import com.example.aandi_post_web_server.assignment.entity.AssignmentRequirement
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTemplateLanguage
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseVisibility
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentTestCaseRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRequirementRepository
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseEnrollment
import com.example.aandi_post_web_server.course.entity.CourseMetadata
import com.example.aandi_post_web_server.course.entity.CourseWeek
import com.example.aandi_post_web_server.course.domain.model.CourseTrack
import com.example.aandi_post_web_server.course.domain.model.EnrollmentStatus
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseEnrollmentRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseWeekRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import org.mockito.Mockito
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class CourseQueryServiceTest : StringSpec({
    "관리자 코스 조회는 수강신청 여부와 관계없이 전체 코스를 반환한다" {
        val fixture = QueryFixture()
        val old = Instant.parse("2026-02-01T00:00:00Z")
        val latest = Instant.parse("2026-03-01T00:00:00Z")
        val flCourse = queryCourse(id = "course-1", slug = "fl-basic", title = "FL 기초", fieldTag = CourseTrack.FL, createdAt = old, updatedAt = old)
        val spCourse = queryCourse(id = "course-2", slug = "sp-basic", title = "SP 기초", fieldTag = CourseTrack.SP, createdAt = latest, updatedAt = latest)

        Mockito.`when`(fixture.courseRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")))
            .thenReturn(Flux.just(spCourse, flCourse))

        StepVerifier.create(
            fixture.service.getAdminCourses().map { it.slug }.collectList()
        )
            .assertNext { slugs ->
                slugs.shouldContainExactly("sp-basic", "fl-basic")
            }
            .verifyComplete()
    }

    "관리자 과제 목록 조회는 수강 상태와 무관하게 status 필터를 적용한다" {
        val fixture = QueryFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val draft = queryAssignment(id = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111", courseId = "course-1").copy(
            status = AssignmentStatus.DRAFT,
            startAt = Instant.parse("2026-04-01T00:00:00Z"),
            publishedAt = null,
        )
        val published = queryAssignment(id = "7c53f1b3-0df8-4a9d-a56d-a5f50b96b7a1", courseId = "course-1")

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.just(draft, published))
        Mockito.`when`(fixture.assignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder("8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"))
            .thenReturn(Flux.empty())
        Mockito.`when`(fixture.assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq("8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"))
            .thenReturn(Flux.empty())
        Mockito.`when`(fixture.assignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder("7c53f1b3-0df8-4a9d-a56d-a5f50b96b7a1"))
            .thenReturn(Flux.empty())
        Mockito.`when`(fixture.assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq("7c53f1b3-0df8-4a9d-a56d-a5f50b96b7a1"))
            .thenReturn(Flux.empty())

        StepVerifier.create(
            fixture.service.getAdminAssignments(
                courseSlug = "back-basic",
                weekNo = null,
                status = AssignmentStatus.DRAFT,
            ).map { it.id }.collectList()
        )
            .assertNext { ids ->
                ids.shouldContainExactly("8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111")
            }
            .verifyComplete()
    }

    "관리자 과제 상세 조회는 DRAFT 과제도 조회할 수 있다" {
        val fixture = QueryFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val draft = queryAssignment(id = assignmentId, courseId = "course-1").copy(
            status = AssignmentStatus.DRAFT,
            startAt = Instant.parse("2026-04-01T00:00:00Z"),
            publishedAt = null,
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId(assignmentId, "course-1"))
            .thenReturn(Mono.just(draft))
        Mockito.`when`(fixture.assignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder(assignmentId))
            .thenReturn(Flux.empty())
        Mockito.`when`(fixture.assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(assignmentId))
            .thenReturn(Flux.empty())

        StepVerifier.create(
            fixture.service.getAdminAssignmentDetail("back-basic", assignmentId)
        )
            .assertNext { detail ->
                detail.id shouldBe assignmentId
                detail.status shouldBe AssignmentStatus.DRAFT
                detail.metadata.codeTemplates.first().language shouldBe AssignmentTemplateLanguage.KOTLIN
            }
            .verifyComplete()
    }

    "관리자 과제 목록 응답은 top-level title, publishedAt, problemId 를 포함한다" {
        val fixture = QueryFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val published = queryAssignment(id = assignmentId, courseId = "course-1")

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.just(published))
        Mockito.`when`(fixture.assignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder(assignmentId))
            .thenReturn(Flux.empty())
        Mockito.`when`(fixture.assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(assignmentId))
            .thenReturn(Flux.empty())

        StepVerifier.create(
            fixture.service.getAdminAssignments(
                courseSlug = "back-basic",
                weekNo = null,
                status = AssignmentStatus.PUBLISHED,
            )
        )
            .assertNext { summary ->
                summary.id shouldBe assignmentId
                summary.title shouldBe "배포 조회 테스트 과제"
                summary.metadata.title shouldBe "배포 조회 테스트 과제"
                summary.publishedAt shouldBe published.publishedAt
                summary.problemId shouldBe assignmentId
            }
            .verifyComplete()
    }

    "관리자 과제 상세 응답은 top-level title, publishedAt, problemId 를 포함한다" {
        val fixture = QueryFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val published = queryAssignment(id = assignmentId, courseId = "course-1")

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId(assignmentId, "course-1"))
            .thenReturn(Mono.just(published))
        Mockito.`when`(fixture.assignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder(assignmentId))
            .thenReturn(Flux.empty())
        Mockito.`when`(fixture.assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(assignmentId))
            .thenReturn(Flux.empty())

        StepVerifier.create(
            fixture.service.getAdminAssignmentDetail("back-basic", assignmentId)
        )
            .assertNext { detail ->
                detail.id shouldBe assignmentId
                detail.title shouldBe "배포 조회 테스트 과제"
                detail.metadata.title shouldBe "배포 조회 테스트 과제"
                detail.publishedAt shouldBe published.publishedAt
                detail.problemId shouldBe assignmentId
            }
            .verifyComplete()
    }

    "코스 조회는 ENABLED 상태로 수강 중인 코스만 반환한다" {
        val fixture = QueryFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val flCourse = queryCourse(id = "course-1", slug = "fl-basic", title = "FL 기초", fieldTag = CourseTrack.FL)
        val spCourse = queryCourse(id = "course-2", slug = "sp-basic", title = "SP 기초", fieldTag = CourseTrack.SP)
        val enrollments = listOf(
            CourseEnrollment(id = "enroll-1", courseId = "course-1", userId = userId, status = EnrollmentStatus.ENABLED),
            CourseEnrollment(id = "enroll-2", courseId = "course-2", userId = userId, status = EnrollmentStatus.ENABLED),
        )

        Mockito.`when`(
            fixture.courseEnrollmentRepository.findAllByUserIdAndStatus(
                userId,
                EnrollmentStatus.ENABLED,
            )
        ).thenReturn(Flux.fromIterable(enrollments))
        Mockito.`when`(fixture.courseRepository.findAllById(listOf("course-1", "course-2")))
            .thenReturn(Flux.just(flCourse, spCourse))

        StepVerifier.create(
            fixture.service.getCourses(userId).map { it.slug }.collectList()
        )
            .assertNext { slugs ->
                slugs.shouldContainExactly("fl-basic", "sp-basic")
            }
            .verifyComplete()
    }

    "코스 목차 요약은 주차별 과제와 진행 상태를 함께 반환한다" {
        val fixture = QueryFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val now = Instant.now()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val enrollment = CourseEnrollment(
            id = "enroll-1",
            courseId = "course-1",
            userId = userId,
            status = EnrollmentStatus.ENABLED,
        )
        val completedA = Assignment(
            id = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111",
            courseId = "course-1",
            createdBy = "admin",
            weekNo = 1,
            orderInWeek = 1,
            startAt = now.minusSeconds(172800),
            endAt = now.minusSeconds(86400),
            metadata = queryAssignment("tmp", "course-1").metadata.copy(title = "Hello"),
            status = AssignmentStatus.PUBLISHED,
            createdAt = now.minusSeconds(172800),
            updatedAt = now.minusSeconds(172800),
            publishedAt = now.minusSeconds(172800),
        )
        val inProgress = Assignment(
            id = "7c53f1b3-0df8-4a9d-a56d-a5f50b96b7a1",
            courseId = "course-1",
            createdBy = "admin",
            weekNo = 2,
            orderInWeek = 1,
            startAt = now.minusSeconds(1800),
            endAt = now.plusSeconds(3600),
            metadata = queryAssignment("tmp2", "course-1").metadata.copy(title = "OOP Calculator"),
            status = AssignmentStatus.PUBLISHED,
            createdAt = now.minusSeconds(3600),
            updatedAt = now.minusSeconds(3600),
            publishedAt = now.minusSeconds(3600),
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", userId))
            .thenReturn(Mono.just(enrollment))
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.just(completedA, inProgress))

        StepVerifier.create(fixture.service.getCourseOutline("back-basic", userId))
            .assertNext { outline ->
                outline.totalAssignments shouldBe 2
                outline.assignments.size shouldBe 2
                outline.assignments[0].weekNo shouldBe 1
                outline.assignments[0].checked shouldBe true
                outline.assignments[1].weekNo shouldBe 2
                outline.assignments[1].checked shouldBe false
            }
            .verifyComplete()
    }

    "과제 조회는 status 미지정 시 PUBLISHED만 조회한다" {
        val fixture = QueryFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val enrollment = CourseEnrollment(
            id = "enroll-1",
            courseId = "course-1",
            userId = userId,
            status = EnrollmentStatus.ENABLED,
        )
        val published = queryAssignment(id = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111", courseId = "course-1")
        val futureDraft = queryAssignment(id = "7c53f1b3-0df8-4a9d-a56d-a5f50b96b7a1", courseId = "course-1").copy(
            status = AssignmentStatus.DRAFT,
            startAt = Instant.parse("2026-04-01T00:00:00Z"),
            publishedAt = null,
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", userId))
            .thenReturn(Mono.just(enrollment))
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.just(published, futureDraft))
        Mockito.`when`(fixture.assignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder("8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"))
            .thenReturn(Flux.empty())
        Mockito.`when`(fixture.assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq("8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"))
            .thenReturn(Flux.empty())

        StepVerifier.create(
            fixture.service.getAssignments(
                courseSlug = "back-basic",
                weekNo = null,
                status = null,
                userId = userId,
            ).map { it.id }.collectList()
        )
            .assertNext { ids ->
                ids.shouldContainExactly("8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111")
            }
            .verifyComplete()
    }

    "과제 조회에서 DRAFT 상태 요청은 BAD_REQUEST를 반환한다" {
        val fixture = QueryFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"

        val error = shouldThrow<ResponseStatusException> {
            fixture.service.getAssignments(
                courseSlug = "back-basic",
                weekNo = null,
                status = AssignmentStatus.DRAFT,
                userId = userId,
            )
        }

        error.statusCode shouldBe HttpStatus.BAD_REQUEST
    }

    "과제 목록 조회는 requirements와 examples를 함께 반환한다" {
        val fixture = QueryFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val enrollment = CourseEnrollment(
            id = "enroll-1",
            courseId = "course-1",
            userId = userId,
            status = EnrollmentStatus.ENABLED,
        )
        val published = queryAssignment(id = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111", courseId = "course-1")

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", userId))
            .thenReturn(Mono.just(enrollment))
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.just(published))
        fixture.stubBatchChildren(
            requirements = listOf(
                AssignmentRequirement(
                    assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111",
                    sortOrder = 1,
                    requirementText = "함수 분리 필수",
                )
            ),
            examples = listOf(
                AssignmentTestCase(
                    assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111",
                    seq = 1,
                    inputValues = listOf("ADD 1"),
                    outputText = "1",
                )
            ),
        )

        StepVerifier.create(
            fixture.service.getAssignments(
                courseSlug = "back-basic",
                weekNo = null,
                status = AssignmentStatus.PUBLISHED,
                userId = userId,
            )
        )
            .assertNext {
                it.metadata.requirements.map { requirement -> requirement.requirementText } shouldBe listOf("함수 분리 필수")
                it.metadata.examples.map { example -> example.outputText } shouldBe listOf("1")
            }
            .verifyComplete()
    }

    "코스 상세 조회는 ENABLED 수강생에게 코스 응답을 반환한다" {
        val fixture = QueryFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val enrollment = CourseEnrollment(id = "enroll-1", courseId = "course-1", userId = userId)

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", userId))
            .thenReturn(Mono.just(enrollment))

        StepVerifier.create(fixture.service.getCourse("back-basic", userId))
            .assertNext { response ->
                response.id shouldBe "course-1"
                response.slug shouldBe "back-basic"
                response.metadata.title shouldBe "BACK 기초"
            }
            .verifyComplete()
    }

    "코스 상세 조회는 비활성 수강 상태를 조회 불가로 처리한다" {
        val fixture = QueryFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val enrollment = CourseEnrollment(
            id = "enroll-1",
            courseId = "course-1",
            userId = userId,
            status = EnrollmentStatus.BANNED,
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", userId))
            .thenReturn(Mono.just(enrollment))

        StepVerifier.create(fixture.service.getCourse("back-basic", userId))
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.NOT_FOUND
                error.reason shouldBe "조회 가능한 코스를 찾을 수 없습니다."
            }
            .verify()
    }

    "수강 신청 목록은 courseSlug를 포함하고 updatedAt 내림차순으로 반환한다" {
        val fixture = QueryFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val older = CourseEnrollment(
            id = "enroll-1",
            courseId = "course-1",
            userId = "user-1",
            publicCode = "#FL301",
            username = "older",
            updatedAt = Instant.parse("2026-03-02T00:00:00Z"),
        )
        val newer = CourseEnrollment(
            id = "enroll-2",
            courseId = "course-1",
            userId = "user-2",
            publicCode = "#FL302",
            username = "newer",
            bannedAt = Instant.parse("2026-03-03T00:00:00Z"),
            banReason = "policy",
            updatedAt = Instant.parse("2026-03-04T00:00:00Z"),
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.just(older, newer))

        StepVerifier.create(fixture.service.getEnrollments("back-basic").collectList())
            .assertNext { enrollments ->
                enrollments.map { it.userId }.shouldContainExactly("user-2", "user-1")
                enrollments[0].courseSlug shouldBe "back-basic"
                enrollments[0].publicCode shouldBe "#FL302"
                enrollments[0].banReason shouldBe "policy"
            }
            .verifyComplete()
    }

    "주차 목록 조회는 수강 확인 후 weekNo 오름차순으로 반환한다" {
        val fixture = QueryFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val enrollment = CourseEnrollment(id = "enroll-1", courseId = "course-1", userId = userId)
        val week2 = CourseWeek(
            id = "week-2",
            courseId = "course-1",
            weekNo = 2,
            title = "2주차",
            startDate = LocalDate.of(2026, 3, 8),
            endDate = LocalDate.of(2026, 3, 14),
        )
        val week1 = CourseWeek(
            id = "week-1",
            courseId = "course-1",
            weekNo = 1,
            title = "1주차",
            startDate = LocalDate.of(2026, 3, 1),
            endDate = LocalDate.of(2026, 3, 7),
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", userId))
            .thenReturn(Mono.just(enrollment))
        Mockito.`when`(fixture.courseWeekRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.just(week2, week1))

        StepVerifier.create(fixture.service.getWeeks("back-basic", userId).collectList())
            .assertNext { weeks ->
                weeks.map { it.weekNo }.shouldContainExactly(1, 2)
                weeks[0].id shouldBe "week-1"
                weeks[0].title shouldBe "1주차"
                weeks[0].startDate shouldBe LocalDate.of(2026, 3, 1)
            }
            .verifyComplete()
    }

    "주차별 사용자 과제 조회는 PUBLIC testcase만 노출한다" {
        val fixture = QueryFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val enrollment = CourseEnrollment(id = "enroll-1", courseId = "course-1", userId = userId)
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val published = queryAssignment(id = assignmentId, courseId = "course-1")

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", userId))
            .thenReturn(Mono.just(enrollment))
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseIdAndWeekNo("course-1", 1))
            .thenReturn(Flux.just(published))
        fixture.stubBatchChildren(
            examples = listOf(
                AssignmentTestCase(
                    assignmentId = assignmentId,
                    seq = 1,
                    inputValues = listOf("public"),
                    outputText = "visible",
                    visibility = AssignmentTestCaseVisibility.PUBLIC,
                ),
                AssignmentTestCase(
                    assignmentId = assignmentId,
                    seq = 2,
                    inputValues = listOf("hidden"),
                    outputText = "secret",
                    visibility = AssignmentTestCaseVisibility.HIDDEN,
                ),
            ),
        )

        StepVerifier.create(
            fixture.service.getAssignmentsByWeek(
                courseSlug = "back-basic",
                weekNo = 1,
                status = AssignmentStatus.PUBLISHED,
                userId = userId,
            )
        )
            .assertNext { summary ->
                summary.metadata.examples.map { it.outputText } shouldBe listOf("visible")
            }
            .verifyComplete()
    }

    "과제 목록 조회는 빈 목록이면 child document batch 조회를 실행하지 않는다" {
        val fixture = QueryFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val enrollment = CourseEnrollment(id = "enroll-1", courseId = "course-1", userId = userId)

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", userId))
            .thenReturn(Mono.just(enrollment))
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.empty())

        StepVerifier.create(
            fixture.service.getAssignments(
                courseSlug = "back-basic",
                weekNo = null,
                status = AssignmentStatus.PUBLISHED,
                userId = userId,
            )
        )
            .verifyComplete()

        Mockito.verify(fixture.assignmentRequirementRepository, Mockito.never())
            .findAllByAssignmentIdIn(Mockito.anyCollection())
        Mockito.verify(fixture.assignmentTestCaseRepository, Mockito.never())
            .findAllByAssignmentIdIn(Mockito.anyCollection())
        Mockito.verify(fixture.assignmentRequirementRepository, Mockito.never())
            .findAllByAssignmentIdOrderBySortOrder(Mockito.anyString())
        Mockito.verify(fixture.assignmentTestCaseRepository, Mockito.never())
            .findAllByAssignmentIdOrderBySeq(Mockito.anyString())
    }

    "과제 목록 조회는 assignment 수와 무관하게 child document를 batch로 한 번씩 조회한다" {
        verifyAssignmentListUsesSingleBatchForCount(1)
        verifyAssignmentListUsesSingleBatchForCount(30)
    }

    "과제 목록 조회는 정렬과 child document 매핑 및 PUBLIC testcase 필터링을 보존한다" {
        val fixture = QueryFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val enrollment = CourseEnrollment(id = "enroll-1", courseId = "course-1", userId = userId)
        val firstId = queryAssignmentId(1)
        val secondId = queryAssignmentId(2)
        val emptyId = queryAssignmentId(3)
        val first = queryAssignment(id = firstId, courseId = "course-1").copy(weekNo = 1, orderInWeek = 2)
        val second = queryAssignment(id = secondId, courseId = "course-1").copy(weekNo = 1, orderInWeek = 1)
        val empty = queryAssignment(id = emptyId, courseId = "course-1").copy(weekNo = 2, orderInWeek = 1)
        val privateMarker = "PERF_PRIVATE_MUST_NOT_LEAK_001"

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", userId))
            .thenReturn(Mono.just(enrollment))
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.just(empty, first, second))
        fixture.stubBatchChildren(
            requirements = listOf(
                AssignmentRequirement(assignmentId = firstId, sortOrder = 2, requirementText = "A second"),
                AssignmentRequirement(assignmentId = secondId, sortOrder = 1, requirementText = "duplicate requirement"),
                AssignmentRequirement(assignmentId = firstId, sortOrder = 1, requirementText = "duplicate requirement"),
            ),
            examples = listOf(
                AssignmentTestCase(
                    assignmentId = firstId,
                    seq = 3,
                    inputValues = listOf("hidden-a"),
                    outputText = privateMarker,
                    visibility = AssignmentTestCaseVisibility.HIDDEN,
                ),
                AssignmentTestCase(
                    assignmentId = firstId,
                    seq = 2,
                    inputValues = listOf("a2"),
                    outputText = "A public second",
                    visibility = AssignmentTestCaseVisibility.PUBLIC,
                ),
                AssignmentTestCase(
                    assignmentId = secondId,
                    seq = 1,
                    inputValues = listOf("same"),
                    outputText = "duplicate output",
                    visibility = AssignmentTestCaseVisibility.PUBLIC,
                ),
                AssignmentTestCase(
                    assignmentId = firstId,
                    seq = 1,
                    inputValues = listOf("same"),
                    outputText = "duplicate output",
                    visibility = AssignmentTestCaseVisibility.PUBLIC,
                ),
                AssignmentTestCase(
                    assignmentId = secondId,
                    seq = 2,
                    inputValues = listOf("hidden-b"),
                    outputText = privateMarker,
                    visibility = AssignmentTestCaseVisibility.HIDDEN,
                ),
            ),
        )

        StepVerifier.create(
            fixture.service.getAssignments(
                courseSlug = "back-basic",
                weekNo = null,
                status = AssignmentStatus.PUBLISHED,
                userId = userId,
            ).collectList()
        )
            .assertNext { assignments ->
                assignments.map { it.id }.shouldContainExactly(secondId, firstId, emptyId)
                assignments[0].metadata.requirements.map { it.requirementText } shouldBe listOf("duplicate requirement")
                assignments[0].metadata.examples.map { it.outputText } shouldBe listOf("duplicate output")
                assignments[1].metadata.requirements.map { it.requirementText } shouldBe listOf("duplicate requirement", "A second")
                assignments[1].metadata.examples.map { it.outputText } shouldBe listOf("duplicate output", "A public second")
                assignments[2].metadata.requirements shouldBe emptyList()
                assignments[2].metadata.examples shouldBe emptyList()
                assignments.flatMap { it.metadata.examples }.any { it.outputText == privateMarker } shouldBe false
            }
            .verifyComplete()
    }

    "관리자 과제 목록 조회는 hidden과 excluded testcase 노출 동작을 유지한다" {
        val fixture = QueryFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentId = queryAssignmentId(1)
        val draft = queryAssignment(id = assignmentId, courseId = "course-1")
            .copy(status = AssignmentStatus.DRAFT, publishedAt = null)

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.just(draft))
        fixture.stubBatchChildren(
            examples = listOf(
                AssignmentTestCase(
                    assignmentId = assignmentId,
                    seq = 3,
                    inputValues = listOf("excluded"),
                    outputText = "internal",
                    visibility = AssignmentTestCaseVisibility.EXCLUDED,
                ),
                AssignmentTestCase(
                    assignmentId = assignmentId,
                    seq = 1,
                    inputValues = listOf("public"),
                    outputText = "visible",
                    visibility = AssignmentTestCaseVisibility.PUBLIC,
                ),
                AssignmentTestCase(
                    assignmentId = assignmentId,
                    seq = 2,
                    inputValues = listOf("hidden"),
                    outputText = "secret",
                    visibility = AssignmentTestCaseVisibility.HIDDEN,
                ),
            ),
        )

        StepVerifier.create(fixture.service.getAdminAssignments("back-basic", null, null))
            .assertNext { summary ->
                summary.status shouldBe AssignmentStatus.DRAFT
                summary.metadata.examples.map { it.outputText }.shouldContainExactly("visible", "secret", "internal")
            }
            .verifyComplete()
    }

    "관리자 상태 필터와 응답은 요청당 동일한 공개 기준 시각을 사용한다" {
        val startAt = Instant.parse("2026-07-10T00:00:00Z")
        val clock = AdvancingClock(
            startAt.minusNanos(1),
            startAt.plusNanos(1),
        )
        val fixture = QueryFixture(clock)
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val scheduled = queryAssignment(id = queryAssignmentId(1), courseId = "course-1")
            .copy(startAt = startAt, publishedAt = startAt)

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.just(scheduled))

        StepVerifier.create(fixture.service.getAdminAssignments("back-basic", null, AssignmentStatus.DRAFT))
            .assertNext { summary -> summary.status shouldBe AssignmentStatus.DRAFT }
            .verifyComplete()

        clock.readCount shouldBe 1
    }

    "과제 목록 조회는 수강 실패 시 assignment와 child document를 조회하지 않는다" {
        val fixture = QueryFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", userId))
            .thenReturn(Mono.empty())

        StepVerifier.create(fixture.service.getAssignments("back-basic", null, AssignmentStatus.PUBLISHED, userId))
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.NOT_FOUND
                error.reason shouldBe "조회 가능한 코스를 찾을 수 없습니다."
            }
            .verify()

        Mockito.verify(fixture.assignmentRepository, Mockito.never()).findAllByCourseId(Mockito.anyString())
        Mockito.verify(fixture.assignmentRequirementRepository, Mockito.never())
            .findAllByAssignmentIdIn(Mockito.anyCollection())
        Mockito.verify(fixture.assignmentTestCaseRepository, Mockito.never())
            .findAllByAssignmentIdIn(Mockito.anyCollection())
    }

    "과제 목록 조회는 future-scheduled와 DRAFT 과제를 사용자에게 노출하지 않는다" {
        val fixture = QueryFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val enrollment = CourseEnrollment(id = "enroll-1", courseId = "course-1", userId = userId)
        val visible = queryAssignment(id = queryAssignmentId(1), courseId = "course-1")
        val future = queryAssignment(id = queryAssignmentId(2), courseId = "course-1")
            .copy(startAt = Instant.parse("2027-01-01T00:00:00Z"), publishedAt = Instant.parse("2027-01-01T00:00:00Z"))
        val draft = queryAssignment(id = queryAssignmentId(3), courseId = "course-1")
            .copy(status = AssignmentStatus.DRAFT, publishedAt = null)

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", userId))
            .thenReturn(Mono.just(enrollment))
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.just(future, draft, visible))

        StepVerifier.create(
            fixture.service.getAssignments("back-basic", null, AssignmentStatus.PUBLISHED, userId)
                .map { it.id }
                .collectList()
        )
            .assertNext { ids -> ids.shouldContainExactly(queryAssignmentId(1)) }
            .verifyComplete()
    }

    "과제 목록 조회는 child document batch 조회 실패를 전파한다" {
        val fixture = QueryFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val enrollment = CourseEnrollment(id = "enroll-1", courseId = "course-1", userId = userId)
        val assignmentId = queryAssignmentId(1)
        val published = queryAssignment(id = assignmentId, courseId = "course-1")
        val failure = IllegalStateException("requirement batch failed")

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", userId))
            .thenReturn(Mono.just(enrollment))
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.just(published))
        Mockito.`when`(fixture.assignmentRequirementRepository.findAllByAssignmentIdIn(listOf(assignmentId)))
            .thenReturn(Flux.error(failure))

        StepVerifier.create(fixture.service.getAssignments("back-basic", null, AssignmentStatus.PUBLISHED, userId))
            .expectErrorSatisfies { error -> error shouldBe failure }
            .verify()
    }

    "사용자 과제 상세 조회는 PRIVATE testcase를 노출하지 않는다" {
        val fixture = QueryFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val enrollment = CourseEnrollment(id = "enroll-1", courseId = "course-1", userId = userId)
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val published = queryAssignment(id = assignmentId, courseId = "course-1")

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", userId))
            .thenReturn(Mono.just(enrollment))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId(assignmentId, "course-1"))
            .thenReturn(Mono.just(published))
        Mockito.`when`(fixture.assignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder(assignmentId))
            .thenReturn(Flux.empty())
        Mockito.`when`(fixture.assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(assignmentId))
            .thenReturn(
                Flux.just(
                    AssignmentTestCase(
                        assignmentId = assignmentId,
                        seq = 1,
                        inputValues = listOf("public"),
                        outputText = "visible",
                        visibility = AssignmentTestCaseVisibility.PUBLIC,
                    ),
                    AssignmentTestCase(
                        assignmentId = assignmentId,
                        seq = 2,
                        inputValues = listOf("hidden"),
                        outputText = "secret",
                        visibility = AssignmentTestCaseVisibility.HIDDEN,
                    ),
                    AssignmentTestCase(
                        assignmentId = assignmentId,
                        seq = 3,
                        inputValues = listOf("excluded"),
                        outputText = "internal",
                        visibility = AssignmentTestCaseVisibility.EXCLUDED,
                    ),
                )
            )

        StepVerifier.create(fixture.service.getAssignmentDetail("back-basic", assignmentId, userId))
            .assertNext { detail ->
                detail.metadata.testCases.map { it.outputText } shouldBe listOf("visible")
            }
            .verifyComplete()

        Mockito.verify(fixture.assignmentRequirementRepository)
            .findAllByAssignmentIdOrderBySortOrder(assignmentId)
        Mockito.verify(fixture.assignmentTestCaseRepository)
            .findAllByAssignmentIdOrderBySeq(assignmentId)
        Mockito.verify(fixture.assignmentRequirementRepository, Mockito.never())
            .findAllByAssignmentIdIn(Mockito.anyCollection())
        Mockito.verify(fixture.assignmentTestCaseRepository, Mockito.never())
            .findAllByAssignmentIdIn(Mockito.anyCollection())
    }

    "관리자 과제 상세 조회는 hidden과 excluded testcase를 모두 포함한다" {
        val fixture = QueryFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val draft = queryAssignment(id = assignmentId, courseId = "course-1").copy(status = AssignmentStatus.DRAFT, publishedAt = null)

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId(assignmentId, "course-1"))
            .thenReturn(Mono.just(draft))
        Mockito.`when`(fixture.assignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder(assignmentId))
            .thenReturn(Flux.empty())
        Mockito.`when`(fixture.assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(assignmentId))
            .thenReturn(
                Flux.just(
                    AssignmentTestCase(
                        assignmentId = assignmentId,
                        seq = 1,
                        inputValues = listOf("public"),
                        outputText = "visible",
                        visibility = AssignmentTestCaseVisibility.PUBLIC,
                    ),
                    AssignmentTestCase(
                        assignmentId = assignmentId,
                        seq = 2,
                        inputValues = listOf("hidden"),
                        outputText = "secret",
                        visibility = AssignmentTestCaseVisibility.HIDDEN,
                    ),
                    AssignmentTestCase(
                        assignmentId = assignmentId,
                        seq = 3,
                        inputValues = listOf("excluded"),
                        outputText = "internal",
                        visibility = AssignmentTestCaseVisibility.EXCLUDED,
                    ),
                )
            )

        StepVerifier.create(fixture.service.getAdminAssignmentDetail("back-basic", assignmentId))
            .assertNext { detail ->
                detail.status shouldBe AssignmentStatus.DRAFT
                detail.metadata.testCases.map { it.outputText }.shouldContainExactly("visible", "secret", "internal")
            }
            .verifyComplete()
    }

    "관리자 과제 목록 week 필터는 주차별 repository 조회를 사용한다" {
        val fixture = QueryFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val published = queryAssignment(id = assignmentId, courseId = "course-1")

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseIdAndWeekNo("course-1", 1))
            .thenReturn(Flux.just(published))
        Mockito.`when`(fixture.assignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder(assignmentId))
            .thenReturn(Flux.empty())
        Mockito.`when`(fixture.assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(assignmentId))
            .thenReturn(Flux.empty())

        StepVerifier.create(fixture.service.getAdminAssignments("back-basic", 1, null))
            .assertNext { summary ->
                summary.id shouldBe assignmentId
            }
            .verifyComplete()

        Mockito.verify(fixture.assignmentRepository).findAllByCourseIdAndWeekNo("course-1", 1)
    }

    "과제 ID로 코스 조회는 과제 공개 여부와 수강 상태를 확인한다" {
        val fixture = QueryFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val assignment = queryAssignment(id = assignmentId, courseId = "course-1")
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val enrollment = CourseEnrollment(id = "enroll-1", courseId = "course-1", userId = userId)

        Mockito.`when`(fixture.assignmentRepository.findById(assignmentId)).thenReturn(Mono.just(assignment))
        Mockito.`when`(fixture.courseRepository.findById("course-1")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", userId))
            .thenReturn(Mono.just(enrollment))

        StepVerifier.create(fixture.service.getAssignmentCourse(assignmentId, userId))
            .assertNext { response ->
                response.slug shouldBe "back-basic"
            }
            .verifyComplete()
    }

    "과제 ID로 코스 조회는 과제의 코스가 없으면 NOT_FOUND를 반환한다" {
        val fixture = QueryFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val assignment = queryAssignment(id = assignmentId, courseId = "missing-course")

        Mockito.`when`(fixture.assignmentRepository.findById(assignmentId)).thenReturn(Mono.just(assignment))
        Mockito.`when`(fixture.courseRepository.findById("missing-course")).thenReturn(Mono.empty())

        StepVerifier.create(fixture.service.getAssignmentCourse(assignmentId, userId))
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.NOT_FOUND
                error.reason shouldBe "코스를 찾을 수 없습니다: missing-course"
            }
            .verify()
    }

    "수강 코스 조회는 수강 내역이 없으면 빈 목록을 반환한다" {
        val fixture = QueryFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"

        Mockito.`when`(fixture.courseEnrollmentRepository.findAllByUserIdAndStatus(userId, EnrollmentStatus.ENABLED))
            .thenReturn(Flux.empty())

        StepVerifier.create(fixture.service.getCourses(userId).collectList())
            .assertNext { courses -> courses shouldBe emptyList() }
            .verifyComplete()
    }

    "잘못된 weekNo는 BAD_REQUEST를 반환한다" {
        val fixture = QueryFixture()

        val error = shouldThrow<ResponseStatusException> {
            fixture.service.getAssignments(
                courseSlug = "back-basic",
                weekNo = 0,
                status = AssignmentStatus.PUBLISHED,
                userId = "8ee88b63-526d-49dc-9e72-a96be0f81385",
            )
        }

        error.statusCode shouldBe HttpStatus.BAD_REQUEST
        error.reason shouldBe "weekNo는 1 이상이어야 합니다."
    }
})

private fun verifyAssignmentListUsesSingleBatchForCount(count: Int) {
    val fixture = QueryFixture()
    val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
    val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
    val enrollment = CourseEnrollment(id = "enroll-1", courseId = "course-1", userId = userId)
    val assignments = (1..count).map { index ->
        queryAssignment(id = queryAssignmentId(index), courseId = "course-1")
            .copy(weekNo = ((index - 1) / 10) + 1, orderInWeek = ((index - 1) % 10) + 1)
    }
    val assignmentIds = assignments.map { assignment -> requireNotNull(assignment.id) }

    Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
    Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", userId))
        .thenReturn(Mono.just(enrollment))
    Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1"))
        .thenReturn(Flux.fromIterable(assignments.asReversed()))

    StepVerifier.create(
        fixture.service.getAssignments("back-basic", null, AssignmentStatus.PUBLISHED, userId)
            .map { it.id }
            .collectList()
    )
        .assertNext { ids -> ids.shouldContainExactly(assignmentIds) }
        .verifyComplete()

    Mockito.verify(fixture.assignmentRequirementRepository, Mockito.times(1))
        .findAllByAssignmentIdIn(assignmentIds)
    Mockito.verify(fixture.assignmentTestCaseRepository, Mockito.times(1))
        .findAllByAssignmentIdIn(assignmentIds)
    Mockito.verify(fixture.assignmentRequirementRepository, Mockito.never())
        .findAllByAssignmentIdOrderBySortOrder(Mockito.anyString())
    Mockito.verify(fixture.assignmentTestCaseRepository, Mockito.never())
        .findAllByAssignmentIdOrderBySeq(Mockito.anyString())
}

private class QueryFixture(clock: Clock = Clock.systemUTC()) {
    val courseRepository: CourseRepository = Mockito.mock(CourseRepository::class.java)
    val courseEnrollmentRepository: CourseEnrollmentRepository = Mockito.mock(CourseEnrollmentRepository::class.java)
    val courseWeekRepository: CourseWeekRepository = Mockito.mock(CourseWeekRepository::class.java)
    val assignmentRepository: AssignmentRepository = Mockito.mock(AssignmentRepository::class.java)
    val assignmentRequirementRepository: AssignmentRequirementRepository = Mockito.mock(AssignmentRequirementRepository::class.java)
    val assignmentTestCaseRepository: AssignmentTestCaseRepository = Mockito.mock(AssignmentTestCaseRepository::class.java)

    init {
        stubBatchChildren()
    }

    val service = CourseQueryService(
        courseRepository = courseRepository,
        courseEnrollmentRepository = courseEnrollmentRepository,
        courseWeekRepository = courseWeekRepository,
        assignmentRepository = assignmentRepository,
        assignmentRequirementRepository = assignmentRequirementRepository,
        assignmentTestCaseRepository = assignmentTestCaseRepository,
        clock = clock,
    )

    fun stubBatchChildren(
        requirements: List<AssignmentRequirement> = emptyList(),
        examples: List<AssignmentTestCase> = emptyList(),
    ) {
        Mockito.`when`(assignmentRequirementRepository.findAllByAssignmentIdIn(Mockito.anyCollection()))
            .thenReturn(Flux.fromIterable(requirements))
        Mockito.`when`(assignmentTestCaseRepository.findAllByAssignmentIdIn(Mockito.anyCollection()))
            .thenReturn(Flux.fromIterable(examples))
        Mockito.clearInvocations(assignmentRequirementRepository, assignmentTestCaseRepository)
    }
}

private class AdvancingClock(
    private vararg val instants: Instant,
) : Clock() {
    var readCount: Int = 0
        private set

    override fun instant(): Instant = instants[minOf(readCount++, instants.lastIndex)]

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this
}

private fun queryAssignmentId(index: Int): String =
    "10000000-0000-4000-8000-${index.toString().padStart(12, '0')}"

private fun queryAssignment(
    id: String,
    courseId: String,
): Assignment {
    val now = Instant.parse("2026-02-20T00:00:00Z")
    return Assignment(
        id = id,
        courseId = courseId,
        createdBy = "admin",
        weekNo = 1,
        orderInWeek = 1,
        startAt = now,
        endAt = now.plusSeconds(3600),
        metadata = com.example.aandi_post_web_server.assignment.entity.AssignmentMetadata(
            title = "배포 조회 테스트 과제",
            difficulty = AssignmentDifficulty.MID,
            description = "content",
            timeLimitMinutes = 60,
            codeTemplates = listOf(
                AssignmentCodeTemplate(
                    language = AssignmentTemplateLanguage.KOTLIN,
                    functionTemplate = "/* ... */\nfun solution(): String { ... }",
                ),
                AssignmentCodeTemplate(
                    language = AssignmentTemplateLanguage.DART,
                    functionTemplate = "/* ... */\nString solution() { ... }",
                ),
            ),
        ),
        status = AssignmentStatus.PUBLISHED,
        createdAt = now,
        updatedAt = now,
        publishedAt = now,
    )
}

private fun queryCourse(
    id: String,
    slug: String,
    title: String,
    fieldTag: CourseTrack = CourseTrack.FL,
    createdAt: Instant = Instant.parse("2026-02-20T00:00:00Z"),
    updatedAt: Instant = createdAt,
): Course = Course(
    id = id,
    slug = slug,
    fieldTag = fieldTag,
    startDate = java.time.LocalDate.of(2026, 3, 1),
    endDate = java.time.LocalDate.of(2026, 3, 30),
    metadata = CourseMetadata(
        title = title,
        description = null,
        phase = null,
        attributes = emptyMap(),
    ),
    createdAt = createdAt,
    updatedAt = updatedAt,
)
