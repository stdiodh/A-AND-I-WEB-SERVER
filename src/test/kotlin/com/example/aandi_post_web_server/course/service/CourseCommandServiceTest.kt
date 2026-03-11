package com.example.aandi_post_web_server.course.service

import com.example.aandi_post_web_server.assignment.domain.AssignmentImportService
import com.example.aandi_post_web_server.assignment.domain.ImportedAssignmentContent
import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.entity.AssignmentDelivery
import com.example.aandi_post_web_server.assignment.dtos.AssignmentImportSourcePayload
import com.example.aandi_post_web_server.assignment.dtos.AssignmentMetadataPayload
import com.example.aandi_post_web_server.assignment.dtos.AssignmentProblemClassificationPayload
import com.example.aandi_post_web_server.assignment.dtos.AssignmentProblemDetailPayload
import com.example.aandi_post_web_server.assignment.dtos.CreateAssignmentRequest
import com.example.aandi_post_web_server.assignment.dtos.UpdateAssignmentRequest
import com.example.aandi_post_web_server.assignment.enum.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.enum.AssignmentProblemStep
import com.example.aandi_post_web_server.assignment.enum.AssignmentSourcePlatform
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import com.example.aandi_post_web_server.assignment.repository.AssignmentDeliveryRepository
import com.example.aandi_post_web_server.assignment.repository.AssignmentExampleRepository
import com.example.aandi_post_web_server.assignment.repository.AssignmentRepository
import com.example.aandi_post_web_server.assignment.repository.AssignmentRequirementRepository
import com.example.aandi_post_web_server.course.dtos.CreateCourseRequest
import com.example.aandi_post_web_server.course.dtos.CourseMetadataPayload
import com.example.aandi_post_web_server.course.dtos.UpdateEnrollmentRequest
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseEnrollment
import com.example.aandi_post_web_server.course.entity.CourseMetadata
import com.example.aandi_post_web_server.course.entity.CourseWeek
import com.example.aandi_post_web_server.course.enum.CoursePhase
import com.example.aandi_post_web_server.course.enum.CourseTrack
import com.example.aandi_post_web_server.course.enum.EnrollmentStatus
import com.example.aandi_post_web_server.course.repository.CourseEnrollmentRepository
import com.example.aandi_post_web_server.course.repository.CourseRepository
import com.example.aandi_post_web_server.course.repository.CourseWeekRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.time.LocalDate

class CourseCommandServiceTest : StringSpec({
    "중복 slug 코스 생성은 CONFLICT를 반환한다" {
        val fixture = CommandFixture()
        Mockito.`when`(fixture.courseRepository.existsBySlug("back-basic")).thenReturn(Mono.just(true))

        StepVerifier.create(
            fixture.service.createCourse(
                CreateCourseRequest(
                    slug = "back-basic",
                    fieldTag = CourseTrack.FL,
                    startDate = LocalDate.parse("2026-03-01"),
                    endDate = LocalDate.parse("2026-03-28"),
                    metadata = CourseMetadataPayload(
                        title = "BACK 기초",
                        description = "desc",
                        phase = CoursePhase.BASIC,
                    ),
                )
            )
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.CONFLICT
            }
            .verify()
    }

    "코스 삭제는 코스 연관 데이터를 하드 삭제한다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignments = listOf(
            commandAssignment(id = "assignment-1", courseId = "course-1", status = AssignmentStatus.PUBLISHED),
            commandAssignment(id = "assignment-2", courseId = "course-1", status = AssignmentStatus.DRAFT),
        )
        val assignmentIds = assignments.mapNotNull { it.id }

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1")).thenReturn(Flux.fromIterable(assignments))
        Mockito.`when`(fixture.assignmentRequirementRepository.deleteAllByAssignmentIdIn(assignmentIds)).thenReturn(Mono.just(2))
        Mockito.`when`(fixture.assignmentExampleRepository.deleteAllByAssignmentIdIn(assignmentIds)).thenReturn(Mono.just(2))
        Mockito.`when`(fixture.assignmentDeliveryRepository.deleteAllByAssignmentIdIn(assignmentIds)).thenReturn(Mono.just(4))
        Mockito.`when`(fixture.assignmentRepository.deleteAllById(assignmentIds)).thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseWeekRepository.deleteAllByCourseId("course-1")).thenReturn(Mono.just(3))
        Mockito.`when`(fixture.courseEnrollmentRepository.deleteAllByCourseId("course-1")).thenReturn(Mono.just(5))
        Mockito.`when`(fixture.courseRepository.deleteById("course-1")).thenReturn(Mono.empty())

        StepVerifier.create(fixture.service.deleteCourse("back-basic"))
            .verifyComplete()

        Mockito.verify(fixture.assignmentRequirementRepository).deleteAllByAssignmentIdIn(assignmentIds)
        Mockito.verify(fixture.assignmentExampleRepository).deleteAllByAssignmentIdIn(assignmentIds)
        Mockito.verify(fixture.assignmentDeliveryRepository).deleteAllByAssignmentIdIn(assignmentIds)
        Mockito.verify(fixture.assignmentRepository).deleteAllById(assignmentIds)
        Mockito.verify(fixture.courseWeekRepository).deleteAllByCourseId("course-1")
        Mockito.verify(fixture.courseEnrollmentRepository).deleteAllByCourseId("course-1")
        Mockito.verify(fixture.courseRepository).deleteById("course-1")
    }

    "과제 삭제는 과제 연관 데이터를 하드 삭제한다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignment = commandAssignment(id = "assignment-1", courseId = "course-1", status = AssignmentStatus.DRAFT)

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId("assignment-1", "course-1"))
            .thenReturn(Mono.just(assignment))
        Mockito.`when`(fixture.assignmentRequirementRepository.deleteAllByAssignmentIdIn(listOf("assignment-1"))).thenReturn(Mono.just(1))
        Mockito.`when`(fixture.assignmentExampleRepository.deleteAllByAssignmentIdIn(listOf("assignment-1"))).thenReturn(Mono.just(1))
        Mockito.`when`(fixture.assignmentDeliveryRepository.deleteAllByAssignmentIdIn(listOf("assignment-1"))).thenReturn(Mono.just(0))
        Mockito.`when`(fixture.assignmentRepository.deleteById("assignment-1")).thenReturn(Mono.empty())

        StepVerifier.create(fixture.service.deleteAssignment("back-basic", "assignment-1"))
            .verifyComplete()

        Mockito.verify(fixture.assignmentRequirementRepository).deleteAllByAssignmentIdIn(listOf("assignment-1"))
        Mockito.verify(fixture.assignmentExampleRepository).deleteAllByAssignmentIdIn(listOf("assignment-1"))
        Mockito.verify(fixture.assignmentDeliveryRepository).deleteAllByAssignmentIdIn(listOf("assignment-1"))
        Mockito.verify(fixture.assignmentRepository).deleteById("assignment-1")
    }

    "과제 수정은 전달된 필드를 반영해 저장한다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val target = commandAssignment(id = "assignment-1", courseId = "course-1", status = AssignmentStatus.DRAFT)

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId("assignment-1", "course-1"))
            .thenReturn(Mono.just(target))
        Mockito.`when`(
            fixture.courseWeekRepository.findByCourseIdAndWeekNo(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyInt(),
            )
        )
            .thenReturn(
                Mono.just(
                    CourseWeek(
                        id = "week-2",
                        courseId = "course-1",
                        weekNo = 2,
                        title = "2주차",
                    )
                )
            )
        Mockito.`when`(
            fixture.assignmentRepository.findByCourseIdAndWeekNoAndOrderInWeek(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyInt(),
                ArgumentMatchers.anyInt(),
            )
        )
            .thenReturn(Mono.empty())
        Mockito.`when`(fixture.assignmentRepository.save(ArgumentMatchers.any(Assignment::class.java)))
            .thenAnswer { invocation ->
                val assignment = invocation.arguments[0] as Assignment
                Mono.just(assignment)
            }
        Mockito.`when`(fixture.assignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder("assignment-1"))
            .thenReturn(Flux.empty())
        Mockito.`when`(fixture.assignmentExampleRepository.findAllByAssignmentIdOrderBySeq("assignment-1"))
            .thenReturn(Flux.empty())

        StepVerifier.create(
            fixture.service.updateAssignment(
                courseSlug = "back-basic",
                assignmentId = "assignment-1",
                request = UpdateAssignmentRequest(
                    weekNo = 2,
                    orderInWeek = 2,
                ),
            )
        )
            .assertNext { updated ->
                updated.id shouldBe "assignment-1"
                updated.weekNo shouldBe 2
                updated.orderInWeek shouldBe 2
            }
            .verifyComplete()
    }

    "BANNED 상태 변경은 banReason이 필수다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val enrollment = CourseEnrollment(
            id = "enroll-1",
            courseId = "course-1",
            userId = "user-1",
            status = EnrollmentStatus.ENROLLED,
            joinedAt = Instant.parse("2026-02-20T00:00:00Z"),
            updatedAt = Instant.parse("2026-02-20T00:00:00Z"),
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", "user-1"))
            .thenReturn(Mono.just(enrollment))

        StepVerifier.create(
            fixture.service.updateEnrollmentStatus(
                courseSlug = "back-basic",
                userId = "user-1",
                request = UpdateEnrollmentRequest(
                    status = EnrollmentStatus.BANNED,
                    banReason = null,
                ),
            )
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.BAD_REQUEST
            }
            .verify()
    }

    "이미 PUBLISHED 과제를 게시하면 기존 publishedAt을 유지한다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val alreadyPublishedAt = Instant.parse("2026-03-01T00:00:00Z")
        val published = commandAssignment(
            id = "assignment-1",
            courseId = "course-1",
            status = AssignmentStatus.PUBLISHED,
        ).copy(publishedAt = alreadyPublishedAt)

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId("assignment-1", "course-1"))
            .thenReturn(Mono.just(published))

        StepVerifier.create(
            fixture.service.publishAssignment("back-basic", "assignment-1")
        )
            .assertNext {
                it.assignmentId shouldBe "assignment-1"
                it.status shouldBe AssignmentStatus.PUBLISHED
                it.publishedAt shouldBe alreadyPublishedAt
            }
            .verifyComplete()
    }

    "주차가 없으면 과제 생성 시 주차를 자동 생성한다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val request = CreateAssignmentRequest(
            weekNo = 2,
            orderInWeek = 1,
            startAt = Instant.parse("2026-03-10T00:00:00Z"),
            endAt = Instant.parse("2026-03-11T00:00:00Z"),
            metadata = AssignmentMetadataPayload(
                title = "터미널 계산기",
                difficulty = AssignmentDifficulty.MID,
                description = "문제 설명",
                timeLimitMinutes = 60,
            ),
            requirements = emptyList(),
            examples = emptyList(),
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(
            fixture.courseWeekRepository.findByCourseIdAndWeekNo(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyInt(),
            )
        ).thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseWeekRepository.save(ArgumentMatchers.any(CourseWeek::class.java)))
            .thenAnswer { invocation ->
                val week = invocation.arguments[0] as CourseWeek
                Mono.just(week.copy(id = "week-2"))
            }
        Mockito.`when`(
            fixture.assignmentRepository.findByCourseIdAndWeekNoAndOrderInWeek(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyInt(),
                ArgumentMatchers.anyInt(),
            )
        ).thenReturn(Mono.empty())
        Mockito.`when`(
            fixture.assignmentImportService.import(
                AssignmentImportSourcePayload(
                    platform = AssignmentSourcePlatform.BOJ,
                    problemId = 2557,
                    autoFillTemplates = true,
                )
            )
        ).thenReturn(
            Mono.just(
                ImportedAssignmentContent(
                    title = "Hello World!",
                    description = "BOJ 문제 설명",
                    inputDescription = "입력이 없다.",
                    outputDescription = "Hello World!를 출력한다.",
                )
            )
        )
        Mockito.`when`(fixture.assignmentRepository.save(ArgumentMatchers.any(Assignment::class.java)))
            .thenAnswer { invocation ->
                val assignment = invocation.arguments[0] as Assignment
                Mono.just(assignment.copy(id = "assignment-1"))
            }

        StepVerifier.create(
            fixture.service.createAssignment(
                courseSlug = "back-basic",
                request = request,
                createdBy = "admin",
            )
        )
            .assertNext { created ->
                created.id shouldBe "assignment-1"
                created.weekNo shouldBe 2
                created.orderInWeek shouldBe 1
            }
            .verifyComplete()

        Mockito.verify(fixture.courseWeekRepository).save(ArgumentMatchers.any(CourseWeek::class.java))
        Mockito.verify(fixture.assignmentRepository)
            .findByCourseIdAndWeekNoAndOrderInWeek("course-1", 2, 1)
    }

    "BOJ 과제 생성은 기본 제출 템플릿을 자동 주입한다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val request = CreateAssignmentRequest(
            weekNo = 1,
            orderInWeek = 1,
            startAt = Instant.parse("2026-03-10T00:00:00Z"),
            endAt = Instant.parse("2026-03-11T00:00:00Z"),
            metadata = AssignmentMetadataPayload(
                title = "Hello World!",
                difficulty = AssignmentDifficulty.LOW,
                description = "문제 설명",
                timeLimitMinutes = 60,
                problemDetail = AssignmentProblemDetailPayload(
                    source = AssignmentImportSourcePayload(
                        platform = AssignmentSourcePlatform.BOJ,
                        problemId = 2557,
                        autoFillTemplates = true,
                    ),
                    inputDescription = "입력이 없다.",
                    outputDescription = "Hello World!를 출력한다.",
                    classification = AssignmentProblemClassificationPayload(
                        algorithmStep = AssignmentProblemStep.STEP0,
                        difficultyStep = 1,
                    ),
                ),
            ),
            requirements = emptyList(),
            examples = emptyList(),
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(
            fixture.courseWeekRepository.findByCourseIdAndWeekNo(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyInt(),
            )
        ).thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseWeekRepository.save(ArgumentMatchers.any(CourseWeek::class.java)))
            .thenAnswer { invocation ->
                val week = invocation.arguments[0] as CourseWeek
                Mono.just(week.copy(id = "week-1"))
            }
        Mockito.`when`(
            fixture.assignmentRepository.findByCourseIdAndWeekNoAndOrderInWeek(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyInt(),
                ArgumentMatchers.anyInt(),
            )
        ).thenReturn(Mono.empty())
        Mockito.`when`(
            fixture.assignmentImportService.import(
                AssignmentImportSourcePayload(
                    platform = AssignmentSourcePlatform.BOJ,
                    problemId = 2557,
                    autoFillTemplates = true,
                )
            )
        ).thenReturn(
            Mono.just(
                ImportedAssignmentContent(
                    title = "Hello World!",
                    description = "Hello World!를 출력하는 문제입니다.",
                    inputDescription = "입력이 없다.",
                    outputDescription = "Hello World!를 출력한다.",
                )
            )
        )
        Mockito.`when`(fixture.assignmentRepository.save(ArgumentMatchers.any(Assignment::class.java)))
            .thenAnswer { invocation ->
                val assignment = invocation.arguments[0] as Assignment
                Mono.just(assignment.copy(id = "assignment-1"))
            }

        StepVerifier.create(
            fixture.service.createAssignment(
                courseSlug = "back-basic",
                request = request,
                createdBy = "admin",
            )
        )
            .assertNext { created ->
                created.metadata.problemDetail?.source?.platform shouldBe AssignmentSourcePlatform.BOJ
                created.metadata.problemDetail?.source?.problemId shouldBe 2557
                created.metadata.submissionGuide?.title shouldBe "문제 풀이 템플릿"
                created.metadata.codeTemplates.map { it.language.name } shouldBe listOf("KOTLIN", "DART")
            }
            .verifyComplete()
    }

    "BOJ 과제 생성은 import 결과로 제목 설명 예제를 채운다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val request = CreateAssignmentRequest(
            weekNo = 1,
            orderInWeek = 2,
            startAt = Instant.parse("2026-03-10T00:00:00Z"),
            endAt = Instant.parse("2026-03-11T00:00:00Z"),
            metadata = AssignmentMetadataPayload(
                title = null,
                difficulty = AssignmentDifficulty.LOW,
                description = null,
                timeLimitMinutes = 60,
                problemDetail = AssignmentProblemDetailPayload(
                    source = AssignmentImportSourcePayload(
                        platform = AssignmentSourcePlatform.BOJ,
                        problemId = 1000,
                        autoFillTemplates = true,
                    ),
                    classification = AssignmentProblemClassificationPayload(
                        algorithmStep = AssignmentProblemStep.STEP1,
                        difficultyStep = 2,
                    ),
                ),
            ),
            requirements = emptyList(),
            examples = emptyList(),
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(
            fixture.courseWeekRepository.findByCourseIdAndWeekNo(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyInt(),
            )
        ).thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseWeekRepository.save(ArgumentMatchers.any(CourseWeek::class.java)))
            .thenAnswer { invocation ->
                val week = invocation.arguments[0] as CourseWeek
                Mono.just(week.copy(id = "week-1"))
            }
        Mockito.`when`(
            fixture.assignmentRepository.findByCourseIdAndWeekNoAndOrderInWeek(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyInt(),
                ArgumentMatchers.anyInt(),
            )
        ).thenReturn(Mono.empty())
        Mockito.`when`(
            fixture.assignmentImportService.import(
                AssignmentImportSourcePayload(
                    platform = AssignmentSourcePlatform.BOJ,
                    problemId = 1000,
                    autoFillTemplates = true,
                )
            )
        ).thenReturn(
            Mono.just(
                ImportedAssignmentContent(
                    title = "A+B",
                    description = "두 수를 입력받아 합을 출력한다.",
                    inputDescription = "첫째 줄에 A와 B가 주어진다.",
                    outputDescription = "A+B를 출력한다.",
                    examples = listOf(
                        com.example.aandi_post_web_server.assignment.dtos.CreateAssignmentExampleRequest(
                            seq = 1,
                            inputText = "1 2",
                            outputText = "3",
                            description = "BOJ 예제 1",
                        )
                    ),
                )
            )
        )
        Mockito.`when`(fixture.assignmentRepository.save(ArgumentMatchers.any(Assignment::class.java)))
            .thenAnswer { invocation ->
                val assignment = invocation.arguments[0] as Assignment
                Mono.just(assignment.copy(id = "assignment-imported"))
            }
        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            Flux.fromIterable(invocation.arguments[0] as List<com.example.aandi_post_web_server.assignment.entity.AssignmentExample>)
        }.`when`(fixture.assignmentExampleRepository)
            .saveAll(ArgumentMatchers.anyList<com.example.aandi_post_web_server.assignment.entity.AssignmentExample>())

        StepVerifier.create(
            fixture.service.createAssignment(
                courseSlug = "back-basic",
                request = request,
                createdBy = "admin",
            )
        )
            .assertNext { created ->
                created.id shouldBe "assignment-imported"
                created.metadata.title shouldBe "A+B"
                created.metadata.description shouldBe "두 수를 입력받아 합을 출력한다."
                created.metadata.problemDetail?.inputDescription shouldBe "첫째 줄에 A와 B가 주어진다."
                created.metadata.problemDetail?.outputDescription shouldBe "A+B를 출력한다."
                created.examples.size shouldBe 1
                created.examples.first().inputText shouldBe "1 2"
                created.examples.first().outputText shouldBe "3"
            }
            .verifyComplete()
    }

    "DRAFT 과제는 배포 트리거할 수 없다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val draft = commandAssignment(
            id = "assignment-draft",
            courseId = "course-1",
            status = AssignmentStatus.DRAFT,
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId("assignment-draft", "course-1"))
            .thenReturn(Mono.just(draft))

        StepVerifier.create(
            fixture.service.triggerDeliveries("back-basic", "assignment-draft")
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.UNPROCESSABLE_ENTITY
            }
            .verify()
    }

    "배포 트리거는 ENROLLED 대상만 DELIVERED 처리한다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val published = commandAssignment(
            id = "assignment-1",
            courseId = "course-1",
            status = AssignmentStatus.PUBLISHED,
        )
        val enrollments = listOf(
            CourseEnrollment(courseId = "course-1", userId = "user-1", status = EnrollmentStatus.ENROLLED),
            CourseEnrollment(courseId = "course-1", userId = "user-2", status = EnrollmentStatus.ENROLLED),
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId("assignment-1", "course-1"))
            .thenReturn(Mono.just(published))
        Mockito.`when`(fixture.courseEnrollmentRepository.findAllByCourseIdAndStatus("course-1", EnrollmentStatus.ENROLLED))
            .thenReturn(Flux.fromIterable(enrollments))
        Mockito.`when`(
            fixture.assignmentDeliveryRepository.findByAssignmentIdAndUserId(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
            )
        ).thenReturn(Mono.empty())
        Mockito.`when`(fixture.assignmentDeliveryRepository.save(ArgumentMatchers.any(AssignmentDelivery::class.java)))
            .thenAnswer { invocation ->
                val delivery = invocation.arguments[0] as AssignmentDelivery
                Mono.just(delivery.copy(id = "${delivery.userId}-delivery"))
            }

        StepVerifier.create(
            fixture.service.triggerDeliveries("back-basic", "assignment-1")
        )
            .assertNext {
                it.targetCount shouldBe 2
                it.deliveredCount shouldBe 2
                it.failedCount shouldBe 0
            }
            .verifyComplete()
    }
})

private class CommandFixture {
    val courseRepository: CourseRepository = Mockito.mock(CourseRepository::class.java)
    val courseEnrollmentRepository: CourseEnrollmentRepository = Mockito.mock(CourseEnrollmentRepository::class.java)
    val courseWeekRepository: CourseWeekRepository = Mockito.mock(CourseWeekRepository::class.java)
    val assignmentRepository: AssignmentRepository = Mockito.mock(AssignmentRepository::class.java)
    val assignmentRequirementRepository: AssignmentRequirementRepository = Mockito.mock(AssignmentRequirementRepository::class.java)
    val assignmentExampleRepository: AssignmentExampleRepository = Mockito.mock(AssignmentExampleRepository::class.java)
    val assignmentDeliveryRepository: AssignmentDeliveryRepository = Mockito.mock(AssignmentDeliveryRepository::class.java)
    val assignmentImportService: AssignmentImportService = Mockito.mock(AssignmentImportService::class.java)

    val service = CourseCommandService(
        courseRepository = courseRepository,
        courseEnrollmentRepository = courseEnrollmentRepository,
        courseWeekRepository = courseWeekRepository,
        assignmentRepository = assignmentRepository,
        assignmentRequirementRepository = assignmentRequirementRepository,
        assignmentExampleRepository = assignmentExampleRepository,
        assignmentDeliveryRepository = assignmentDeliveryRepository,
        assignmentImportService = assignmentImportService,
    )
}

private fun commandAssignment(
    id: String,
    courseId: String,
    status: AssignmentStatus,
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
            title = "테스트 과제",
            difficulty = AssignmentDifficulty.MID,
            description = "content",
            timeLimitMinutes = 60,
        ),
        status = status,
        createdAt = now,
        updatedAt = now,
        publishedAt = if (status == AssignmentStatus.PUBLISHED) now else null,
    )
}

private fun queryCourse(id: String, slug: String, title: String): Course = Course(
    id = id,
    slug = slug,
    fieldTag = CourseTrack.FL,
    startDate = LocalDate.of(2026, 3, 1),
    endDate = LocalDate.of(2026, 3, 30),
    metadata = CourseMetadata(
        title = title,
        description = null,
        phase = CoursePhase.BASIC,
        attributes = emptyMap(),
    ),
)
