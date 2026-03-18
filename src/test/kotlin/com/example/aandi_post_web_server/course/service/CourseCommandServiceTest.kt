package com.example.aandi_post_web_server.course.service

import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.entity.AssignmentExample
import com.example.aandi_post_web_server.assignment.dtos.AssignmentMetadataPayload
import com.example.aandi_post_web_server.assignment.dtos.CreateAssignmentRequest
import com.example.aandi_post_web_server.assignment.dtos.CreateAssignmentExampleRequest
import com.example.aandi_post_web_server.assignment.dtos.UpdateAssignmentRequest
import com.example.aandi_post_web_server.assignment.enum.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import com.example.aandi_post_web_server.assignment.event.AssignmentReportTestCaseEvent
import com.example.aandi_post_web_server.assignment.event.AssignmentReportTestCaseEventMapper
import com.example.aandi_post_web_server.assignment.event.AssignmentReportTestCaseEventPublisher
import com.example.aandi_post_web_server.assignment.event.AssignmentReportTestCaseEventType
import com.example.aandi_post_web_server.assignment.repository.AssignmentDeliveryRepository
import com.example.aandi_post_web_server.assignment.repository.AssignmentExampleRepository
import com.example.aandi_post_web_server.assignment.repository.AssignmentRepository
import com.example.aandi_post_web_server.assignment.repository.AssignmentRequirementRepository
import com.example.aandi_post_web_server.course.dtos.CreateCourseRequest
import com.example.aandi_post_web_server.course.dtos.CourseMetadataPayload
import com.example.aandi_post_web_server.course.dtos.EnrollCourseRequest
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
import com.example.aandi_post_web_server.user.entity.ReportUser
import com.example.aandi_post_web_server.user.repository.ReportUserRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize
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
            commandAssignment(id = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111", courseId = "course-1", status = AssignmentStatus.PUBLISHED),
            commandAssignment(id = "7c53f1b3-0df8-4a9d-a56d-a5f50b96b7a1", courseId = "course-1", status = AssignmentStatus.DRAFT),
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
        fixture.assignmentReportTestCaseEventPublisher.events.map { it.problemId } shouldBe assignmentIds
        fixture.assignmentReportTestCaseEventPublisher.events.forEach {
            it.eventType shouldBe AssignmentReportTestCaseEventType.PROBLEM_DELETED
            it.testCases shouldBe emptyList()
        }
        Mockito.verify(fixture.courseWeekRepository).deleteAllByCourseId("course-1")
        Mockito.verify(fixture.courseEnrollmentRepository).deleteAllByCourseId("course-1")
        Mockito.verify(fixture.courseRepository).deleteById("course-1")
    }

    "과제 삭제는 과제 연관 데이터를 하드 삭제한다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val assignment = commandAssignment(id = assignmentId, courseId = "course-1", status = AssignmentStatus.DRAFT)

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId(assignmentId, "course-1"))
            .thenReturn(Mono.just(assignment))
        Mockito.`when`(fixture.assignmentRequirementRepository.deleteAllByAssignmentIdIn(listOf(assignmentId))).thenReturn(Mono.just(1))
        Mockito.`when`(fixture.assignmentExampleRepository.deleteAllByAssignmentIdIn(listOf(assignmentId))).thenReturn(Mono.just(1))
        Mockito.`when`(fixture.assignmentDeliveryRepository.deleteAllByAssignmentIdIn(listOf(assignmentId))).thenReturn(Mono.just(0))
        Mockito.`when`(fixture.assignmentRepository.deleteById(assignmentId)).thenReturn(Mono.empty())

        StepVerifier.create(fixture.service.deleteAssignment("back-basic", assignmentId))
            .verifyComplete()

        Mockito.verify(fixture.assignmentRequirementRepository).deleteAllByAssignmentIdIn(listOf(assignmentId))
        Mockito.verify(fixture.assignmentExampleRepository).deleteAllByAssignmentIdIn(listOf(assignmentId))
        Mockito.verify(fixture.assignmentDeliveryRepository).deleteAllByAssignmentIdIn(listOf(assignmentId))
        Mockito.verify(fixture.assignmentRepository).deleteById(assignmentId)
        fixture.assignmentReportTestCaseEventPublisher.events.single().eventType shouldBe AssignmentReportTestCaseEventType.PROBLEM_DELETED
        fixture.assignmentReportTestCaseEventPublisher.events.single().problemId shouldBe assignmentId
        fixture.assignmentReportTestCaseEventPublisher.events.single().testCases shouldBe emptyList()
    }

    "과제 수정은 전달된 필드를 반영해 저장한다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val target = commandAssignment(id = assignmentId, courseId = "course-1", status = AssignmentStatus.DRAFT)

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId(assignmentId, "course-1"))
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
        Mockito.`when`(fixture.assignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder(assignmentId))
            .thenReturn(Flux.empty())
        Mockito.`when`(fixture.assignmentExampleRepository.findAllByAssignmentIdOrderBySeq(assignmentId))
            .thenReturn(Flux.empty())

        StepVerifier.create(
            fixture.service.updateAssignment(
                courseSlug = "back-basic",
                assignmentId = assignmentId,
                request = UpdateAssignmentRequest(
                    weekNo = 2,
                    orderInWeek = 2,
                ),
            )
        )
            .assertNext { updated ->
                updated.id shouldBe assignmentId
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
            status = EnrollmentStatus.ENABLED,
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

    "수강생 삭제는 등록 정보를 제거한다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val enrollment = CourseEnrollment(
            id = "enroll-1",
            courseId = "course-1",
            userId = "user-1",
            status = EnrollmentStatus.ENABLED,
            joinedAt = Instant.parse("2026-02-20T00:00:00Z"),
            updatedAt = Instant.parse("2026-02-20T00:00:00Z"),
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", "user-1"))
            .thenReturn(Mono.just(enrollment))
        Mockito.`when`(fixture.courseEnrollmentRepository.delete(enrollment)).thenReturn(Mono.empty())

        StepVerifier.create(
            fixture.service.deleteEnrollment(
                courseSlug = "back-basic",
                userId = "user-1",
            )
        )
            .verifyComplete()

        Mockito.verify(fixture.courseEnrollmentRepository).delete(enrollment)
    }

    "삭제된 수강생은 다시 등록할 수 있다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val reportUser = ReportUser(
            id = "user-1",
            publicCode = "#FL301",
            username = "mekazon",
            role = "USER",
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.reportUserRepository.findByPublicCode("#FL301")).thenReturn(Mono.just(reportUser))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", "user-1"))
            .thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseEnrollmentRepository.save(ArgumentMatchers.any(CourseEnrollment::class.java)))
            .thenAnswer { invocation -> Mono.just(invocation.arguments[0] as CourseEnrollment) }

        StepVerifier.create(
            fixture.service.enrollMember(
                courseSlug = "back-basic",
                request = EnrollCourseRequest(publicCode = "FL301"),
            )
        )
            .assertNext { enrollment ->
                enrollment.status shouldBe EnrollmentStatus.ENABLED
                enrollment.userId shouldBe "user-1"
                enrollment.publicCode shouldBe "#FL301"
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
            ),
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
        Mockito.`when`(fixture.assignmentRepository.save(ArgumentMatchers.any(Assignment::class.java)))
            .thenAnswer { invocation ->
                val assignment = invocation.arguments[0] as Assignment
                Mono.just(assignment)
            }

        StepVerifier.create(
            fixture.service.createAssignment(
                courseSlug = "back-basic",
                request = request,
                createdBy = "admin",
            )
        )
            .assertNext { created ->
                java.util.UUID.fromString(created.id).toString() shouldBe created.id
                created.weekNo shouldBe 2
                created.orderInWeek shouldBe 1
            }
            .verifyComplete()

        Mockito.verify(fixture.courseWeekRepository).save(ArgumentMatchers.any(CourseWeek::class.java))
        Mockito.verify(fixture.assignmentRepository)
            .findByCourseIdAndWeekNoAndOrderInWeek("course-1", 2, 1)
    }

    "과제 생성은 PROBLEM_CREATED 이벤트를 발행한다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val request = CreateAssignmentRequest(
            weekNo = 1,
            orderInWeek = 3,
            startAt = Instant.parse("2026-03-10T00:00:00Z"),
            endAt = Instant.parse("2026-03-11T00:00:00Z"),
            metadata = AssignmentMetadataPayload(
                title = "Hello World!",
                difficulty = AssignmentDifficulty.LOW,
                description = "문제 설명",
                testCases = listOf(
                    CreateAssignmentExampleRequest(
                        seq = 1,
                        inputText = "ADD 1\nCLOSE",
                        outputText = "3",
                    )
                ),
            ),
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(
            fixture.courseWeekRepository.findByCourseIdAndWeekNo(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyInt(),
            )
        ).thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseWeekRepository.save(ArgumentMatchers.any(CourseWeek::class.java)))
            .thenAnswer { invocation -> Mono.just((invocation.arguments[0] as CourseWeek).copy(id = "week-1")) }
        Mockito.`when`(
            fixture.assignmentRepository.findByCourseIdAndWeekNoAndOrderInWeek(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyInt(),
                ArgumentMatchers.anyInt(),
            )
        ).thenReturn(Mono.empty())
        Mockito.`when`(fixture.assignmentRepository.save(ArgumentMatchers.any(Assignment::class.java)))
            .thenAnswer { invocation -> Mono.just(invocation.arguments[0] as Assignment) }
        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            Flux.fromIterable(invocation.arguments[0] as List<AssignmentExample>)
        }.`when`(fixture.assignmentExampleRepository)
            .saveAll(ArgumentMatchers.anyList<AssignmentExample>())

        StepVerifier.create(fixture.service.createAssignment("back-basic", request, "admin"))
            .assertNext { created ->
                java.util.UUID.fromString(created.id).toString() shouldBe created.id
            }
            .verifyComplete()

        fixture.assignmentReportTestCaseEventPublisher.events.single().eventType shouldBe AssignmentReportTestCaseEventType.PROBLEM_CREATED
        java.util.UUID.fromString(fixture.assignmentReportTestCaseEventPublisher.events.single().problemId).toString() shouldBe fixture.assignmentReportTestCaseEventPublisher.events.single().problemId
        fixture.assignmentReportTestCaseEventPublisher.events.single().testCases shouldHaveSize 1
        fixture.assignmentReportTestCaseEventPublisher.events.single().testCases.first().caseId shouldBe 1
        fixture.assignmentReportTestCaseEventPublisher.events.single().testCases.first().input shouldBe listOf("ADD 1\nCLOSE")
        fixture.assignmentReportTestCaseEventPublisher.events.single().testCases.first().output shouldBe "3"
    }

    "과제 수정은 일부 케이스 삭제가 있어도 최종 전체 배열로 PROBLEM_UPDATED 를 발행한다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val target = commandAssignment(id = assignmentId, courseId = "course-1", status = AssignmentStatus.DRAFT)
        val updateRequest = UpdateAssignmentRequest(
            metadata = AssignmentMetadataPayload(
                title = "updated title",
                difficulty = AssignmentDifficulty.LOW,
                description = "updated description",
                testCases = listOf(
                    CreateAssignmentExampleRequest(
                        seq = 1,
                        inputText = "updated input",
                        outputText = "updated output",
                    )
                ),
            )
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId(assignmentId, "course-1"))
            .thenReturn(Mono.just(target))
        Mockito.`when`(
            fixture.assignmentRepository.findByCourseIdAndWeekNoAndOrderInWeek(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyInt(),
                ArgumentMatchers.anyInt(),
            )
        ).thenReturn(Mono.just(target))
        Mockito.`when`(
            fixture.courseWeekRepository.findByCourseIdAndWeekNo(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyInt(),
            )
        ).thenReturn(Mono.just(CourseWeek(id = "week-1", courseId = "course-1", weekNo = 1, title = "1주차")))
        Mockito.`when`(fixture.assignmentRepository.save(ArgumentMatchers.any(Assignment::class.java)))
            .thenAnswer { invocation -> Mono.just(invocation.arguments[0] as Assignment) }
        Mockito.`when`(fixture.assignmentRequirementRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)))
            .thenReturn(Mono.just(0))
        Mockito.`when`(fixture.assignmentExampleRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)))
            .thenReturn(Mono.just(2))
        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            Flux.fromIterable(invocation.arguments[0] as List<AssignmentExample>)
        }.`when`(fixture.assignmentExampleRepository)
            .saveAll(ArgumentMatchers.anyList<AssignmentExample>())

        StepVerifier.create(
            fixture.service.updateAssignment(
                courseSlug = "back-basic",
                assignmentId = assignmentId,
                request = updateRequest,
            )
        )
            .assertNext { updated ->
                updated.metadata.examples shouldHaveSize 1
                updated.metadata.examples.first().inputText shouldBe "updated input"
            }
            .verifyComplete()

        fixture.assignmentReportTestCaseEventPublisher.events.single().eventType shouldBe AssignmentReportTestCaseEventType.PROBLEM_UPDATED
        fixture.assignmentReportTestCaseEventPublisher.events.single().problemId shouldBe assignmentId
        fixture.assignmentReportTestCaseEventPublisher.events.single().testCases shouldHaveSize 1
        fixture.assignmentReportTestCaseEventPublisher.events.single().testCases.first().caseId shouldBe 1
        fixture.assignmentReportTestCaseEventPublisher.events.single().testCases.first().input shouldBe listOf("updated input")
        fixture.assignmentReportTestCaseEventPublisher.events.single().testCases.first().output shouldBe "updated output"
    }

    "examples 를 건드리지 않는 과제 수정은 OJ test case 이벤트를 재발행하지 않는다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val target = commandAssignment(id = assignmentId, courseId = "course-1", status = AssignmentStatus.DRAFT)

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId(assignmentId, "course-1"))
            .thenReturn(Mono.just(target))
        Mockito.`when`(
            fixture.assignmentRepository.findByCourseIdAndWeekNoAndOrderInWeek(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyInt(),
                ArgumentMatchers.anyInt(),
            )
        ).thenReturn(Mono.just(target))
        Mockito.`when`(
            fixture.courseWeekRepository.findByCourseIdAndWeekNo(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyInt(),
            )
        ).thenReturn(Mono.just(CourseWeek(id = "week-1", courseId = "course-1", weekNo = 1, title = "1주차")))
        Mockito.`when`(fixture.assignmentRepository.save(ArgumentMatchers.any(Assignment::class.java)))
            .thenAnswer { invocation -> Mono.just(invocation.arguments[0] as Assignment) }
        Mockito.`when`(fixture.assignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder(assignmentId))
            .thenReturn(Flux.empty())
        Mockito.`when`(fixture.assignmentExampleRepository.findAllByAssignmentIdOrderBySeq(assignmentId))
            .thenReturn(
                Flux.just(
                    AssignmentExample(
                        id = "ex-1",
                        assignmentId = assignmentId,
                        seq = 1,
                        inputText = "persisted input",
                        outputText = "persisted output",
                    )
                )
            )

        StepVerifier.create(
            fixture.service.updateAssignment(
                courseSlug = "back-basic",
                assignmentId = assignmentId,
                request = UpdateAssignmentRequest(orderInWeek = 2),
            )
        )
            .assertNext { updated ->
                updated.orderInWeek shouldBe 2
                updated.metadata.examples shouldHaveSize 1
            }
            .verifyComplete()

        fixture.assignmentReportTestCaseEventPublisher.events shouldBe emptyList()
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
    val assignmentReportTestCaseEventMapper = AssignmentReportTestCaseEventMapper()
    val assignmentReportTestCaseEventPublisher = RecordingAssignmentReportTestCaseEventPublisher()
    val reportUserRepository: ReportUserRepository = Mockito.mock(ReportUserRepository::class.java)
    val service = CourseCommandService(
        courseRepository = courseRepository,
        courseEnrollmentRepository = courseEnrollmentRepository,
        courseWeekRepository = courseWeekRepository,
        assignmentRepository = assignmentRepository,
        assignmentRequirementRepository = assignmentRequirementRepository,
        assignmentTestCaseRepository = assignmentExampleRepository,
        assignmentDeliveryRepository = assignmentDeliveryRepository,
        assignmentReportTestCaseEventMapper = assignmentReportTestCaseEventMapper,
        assignmentReportTestCaseEventPublisher = assignmentReportTestCaseEventPublisher,
        reportUserRepository = reportUserRepository,
    )

}

private class RecordingAssignmentReportTestCaseEventPublisher : AssignmentReportTestCaseEventPublisher {
    val events = mutableListOf<AssignmentReportTestCaseEvent>()

    override fun publish(event: AssignmentReportTestCaseEvent): Mono<Void> {
        events += event
        return Mono.empty()
    }
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
