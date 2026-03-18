package com.example.aandi_post_web_server.course.service

import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.entity.AssignmentCodeTemplate
import com.example.aandi_post_web_server.assignment.entity.AssignmentExample
import com.example.aandi_post_web_server.assignment.entity.AssignmentProblemClassification
import com.example.aandi_post_web_server.assignment.entity.AssignmentProblemDetail
import com.example.aandi_post_web_server.assignment.entity.AssignmentRequirement
import com.example.aandi_post_web_server.assignment.entity.AssignmentSubmissionGuide
import com.example.aandi_post_web_server.assignment.enum.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.enum.AssignmentProblemStep
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import com.example.aandi_post_web_server.assignment.enum.AssignmentTemplateLanguage
import com.example.aandi_post_web_server.assignment.repository.AssignmentExampleRepository
import com.example.aandi_post_web_server.assignment.repository.AssignmentRepository
import com.example.aandi_post_web_server.assignment.repository.AssignmentRequirementRepository
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseEnrollment
import com.example.aandi_post_web_server.course.entity.CourseMetadata
import com.example.aandi_post_web_server.course.enum.CourseTrack
import com.example.aandi_post_web_server.course.enum.EnrollmentStatus
import com.example.aandi_post_web_server.course.repository.CourseEnrollmentRepository
import com.example.aandi_post_web_server.course.repository.CourseRepository
import com.example.aandi_post_web_server.course.repository.CourseWeekRepository
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
import java.time.Instant

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
        Mockito.`when`(fixture.assignmentExampleRepository.findAllByAssignmentIdOrderBySeq("8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"))
            .thenReturn(Flux.empty())
        Mockito.`when`(fixture.assignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder("7c53f1b3-0df8-4a9d-a56d-a5f50b96b7a1"))
            .thenReturn(Flux.empty())
        Mockito.`when`(fixture.assignmentExampleRepository.findAllByAssignmentIdOrderBySeq("7c53f1b3-0df8-4a9d-a56d-a5f50b96b7a1"))
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
        Mockito.`when`(fixture.assignmentExampleRepository.findAllByAssignmentIdOrderBySeq(assignmentId))
            .thenReturn(Flux.empty())

        StepVerifier.create(
            fixture.service.getAdminAssignmentDetail("back-basic", assignmentId)
        )
            .assertNext { detail ->
                detail.id shouldBe assignmentId
                detail.status shouldBe AssignmentStatus.DRAFT
                detail.metadata.problemDetail?.classification?.algorithmStep shouldBe AssignmentProblemStep.STEP0
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
        Mockito.`when`(fixture.assignmentExampleRepository.findAllByAssignmentIdOrderBySeq("8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"))
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
        Mockito.`when`(fixture.assignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder("8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"))
            .thenReturn(
                Flux.just(
                    AssignmentRequirement(
                        assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111",
                        sortOrder = 1,
                        requirementText = "함수 분리 필수",
                    )
                )
            )
        Mockito.`when`(fixture.assignmentExampleRepository.findAllByAssignmentIdOrderBySeq("8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"))
            .thenReturn(
                Flux.just(
                    AssignmentExample(
                        assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111",
                        seq = 1,
                        inputText = "ADD 1",
                        outputText = "1",
                    )
                )
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
})

private class QueryFixture {
    val courseRepository: CourseRepository = Mockito.mock(CourseRepository::class.java)
    val courseEnrollmentRepository: CourseEnrollmentRepository = Mockito.mock(CourseEnrollmentRepository::class.java)
    val courseWeekRepository: CourseWeekRepository = Mockito.mock(CourseWeekRepository::class.java)
    val assignmentRepository: AssignmentRepository = Mockito.mock(AssignmentRepository::class.java)
    val assignmentRequirementRepository: AssignmentRequirementRepository = Mockito.mock(AssignmentRequirementRepository::class.java)
    val assignmentExampleRepository: AssignmentExampleRepository = Mockito.mock(AssignmentExampleRepository::class.java)

    val service = CourseQueryService(
        courseRepository = courseRepository,
        courseEnrollmentRepository = courseEnrollmentRepository,
        courseWeekRepository = courseWeekRepository,
        assignmentRepository = assignmentRepository,
        assignmentRequirementRepository = assignmentRequirementRepository,
        assignmentTestCaseRepository = assignmentExampleRepository,
    )
}

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
            problemDetail = AssignmentProblemDetail(
                inputDescription = "입력이 없다.",
                outputDescription = "Hello World!를 출력한다.",
                classification = AssignmentProblemClassification(
                    algorithmStep = AssignmentProblemStep.STEP0,
                    difficultyStep = 1,
                ),
            ),
            submissionGuide = AssignmentSubmissionGuide(),
            codeTemplates = listOf(
                AssignmentCodeTemplate(
                    language = AssignmentTemplateLanguage.KOTLIN,
                    commentTemplate = "/* ... */",
                    functionTemplate = "fun solution(): String { ... }",
                    runnableTemplate = "fun solution(): String { ... }",
                ),
                AssignmentCodeTemplate(
                    language = AssignmentTemplateLanguage.DART,
                    commentTemplate = "/* ... */",
                    functionTemplate = "String solution() { ... }",
                    runnableTemplate = "String solution() { ... }",
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
