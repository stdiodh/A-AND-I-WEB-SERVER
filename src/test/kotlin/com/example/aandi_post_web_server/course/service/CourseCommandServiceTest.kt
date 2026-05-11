package com.example.aandi_post_web_server.course.application.service

import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseValidator
import com.example.aandi_post_web_server.assignment.application.service.AssignmentCopyFingerprintCalculator
import com.example.aandi_post_web_server.assignment.application.service.AssignmentCopyService
import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.entity.AssignmentExample
import com.example.aandi_post_web_server.assignment.entity.AssignmentRequirement
import com.example.aandi_post_web_server.assignment.infrastructure.jackson.AssignmentMetadataPayloadTestCasePresenceTracker
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentMetadataPayload
import com.example.aandi_post_web_server.assignment.api.dto.CopyAssignmentRequest
import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentRequest
import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentExampleRequest
import com.example.aandi_post_web_server.assignment.api.dto.UpdateAssignmentRequest
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseVisibility
import com.example.aandi_post_web_server.assignment.infrastructure.event.AssignmentReportTestCaseEvent
import com.example.aandi_post_web_server.assignment.infrastructure.event.AssignmentReportTestCaseEventMapper
import com.example.aandi_post_web_server.assignment.infrastructure.event.AssignmentReportTestCaseEventPublisher
import com.example.aandi_post_web_server.assignment.infrastructure.event.AssignmentReportTestCaseEventType
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentDeliveryRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentExampleRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRequirementRepository
import com.example.aandi_post_web_server.course.api.dto.CreateCourseRequest
import com.example.aandi_post_web_server.course.api.dto.CourseMetadataPayload
import com.example.aandi_post_web_server.course.api.dto.EnrollCourseRequest
import com.example.aandi_post_web_server.course.api.dto.UpdateEnrollmentRequest
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseEnrollment
import com.example.aandi_post_web_server.course.entity.CourseMetadata
import com.example.aandi_post_web_server.course.entity.CourseWeek
import com.example.aandi_post_web_server.course.domain.model.CoursePhase
import com.example.aandi_post_web_server.course.domain.model.CourseTrack
import com.example.aandi_post_web_server.course.domain.model.EnrollmentStatus
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseEnrollmentRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseWeekRepository
import com.example.aandi_post_web_server.user.entity.ReportUser
import com.example.aandi_post_web_server.user.infrastructure.repository.ReportUserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize
import org.springframework.dao.DuplicateKeyException
import org.springframework.web.server.ResponseStatusException
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.springframework.http.HttpStatus
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
        var persistedAssignment: Assignment = target

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
                persistedAssignment = assignment
                Mono.just(assignment)
            }
        Mockito.`when`(fixture.assignmentRepository.findById(assignmentId)).thenAnswer { Mono.just(persistedAssignment) }
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
                updated.status shouldBe AssignmentStatus.DRAFT
                updated.publishedAt shouldBe null
            }
            .verifyComplete()

        persistedAssignment.status shouldBe AssignmentStatus.PUBLISHED
        persistedAssignment.publishedAt shouldBe target.startAt
    }

    "과제 수정 슬롯 중복은 새 주차를 생성하지 않고 CONFLICT 를 반환한다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val target = commandAssignment(id = assignmentId, courseId = "course-1", status = AssignmentStatus.DRAFT)
        val duplicated = commandAssignment(
            id = "7c53f1b3-0df8-4a9d-a56d-a5f50b96b7a1",
            courseId = "course-1",
            status = AssignmentStatus.DRAFT,
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
        ).thenReturn(Mono.just(duplicated))
        Mockito.`when`(
            fixture.courseWeekRepository.findByCourseIdAndWeekNo(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyInt(),
            )
        )
            .thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseWeekRepository.save(ArgumentMatchers.any(CourseWeek::class.java)))
            .thenAnswer { invocation -> Mono.just(invocation.arguments[0] as CourseWeek) }

        StepVerifier.create(
            fixture.service.updateAssignment(
                courseSlug = "back-basic",
                assignmentId = assignmentId,
                request = UpdateAssignmentRequest(
                    weekNo = 9,
                    orderInWeek = 2,
                ),
            )
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.CONFLICT
                error.reason shouldBe "동일 코스/주차/순번 과제가 이미 존재합니다."
            }
            .verify()

        Mockito.verify(fixture.courseWeekRepository, Mockito.never()).save(ArgumentMatchers.any(CourseWeek::class.java))
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
        val reportUser = reportUser(id = "user-1", publicCode = "#FL301", role = "USER")

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

    "FL 코스는 FL 일반 유저만 등록할 수 있다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "fl-basic", title = "FL 기초", fieldTag = CourseTrack.FL)
        val reportUser = reportUser(id = "user-sp", publicCode = "#SP201", role = "USER")

        Mockito.`when`(fixture.courseRepository.findBySlug("fl-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.reportUserRepository.findByPublicCode("#SP201")).thenReturn(Mono.just(reportUser))

        StepVerifier.create(
            fixture.service.enrollMember(
                courseSlug = "fl-basic",
                request = EnrollCourseRequest(publicCode = "#SP201"),
            )
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.FORBIDDEN
                error.reason shouldBe "FL 코스에는 FL 트랙 사용자만 등록할 수 있습니다."
            }
            .verify()
    }

    "SP 코스는 SP 일반 유저를 등록할 수 있다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "sp-basic", title = "SP 기초", fieldTag = CourseTrack.SP)
        val reportUser = reportUser(id = "user-sp", publicCode = "#SP201", role = "USER")

        Mockito.`when`(fixture.courseRepository.findBySlug("sp-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.reportUserRepository.findByPublicCode("#SP201")).thenReturn(Mono.just(reportUser))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", "user-sp"))
            .thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseEnrollmentRepository.save(ArgumentMatchers.any(CourseEnrollment::class.java)))
            .thenAnswer { invocation -> Mono.just(invocation.arguments[0] as CourseEnrollment) }

        StepVerifier.create(
            fixture.service.enrollMember(
                courseSlug = "sp-basic",
                request = EnrollCourseRequest(publicCode = "#SP201"),
            )
        )
            .assertNext { enrollment ->
                enrollment.userId shouldBe "user-sp"
                enrollment.publicCode shouldBe "#SP201"
            }
            .verifyComplete()
    }

    "SP 코스는 SP가 아닌 일반 유저 등록을 막는다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "sp-basic", title = "SP 기초", fieldTag = CourseTrack.SP)
        val reportUser = reportUser(id = "user-fl", publicCode = "#FL301", role = "USER")

        Mockito.`when`(fixture.courseRepository.findBySlug("sp-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.reportUserRepository.findByPublicCode("#FL301")).thenReturn(Mono.just(reportUser))

        StepVerifier.create(
            fixture.service.enrollMember(
                courseSlug = "sp-basic",
                request = EnrollCourseRequest(publicCode = "#FL301"),
            )
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.FORBIDDEN
                error.reason shouldBe "SP 코스에는 SP 트랙 사용자만 등록할 수 있습니다."
            }
            .verify()
    }

    "NO 코스는 모든 일반 유저를 등록할 수 있다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "no-basic", title = "NO 기초", fieldTag = CourseTrack.NO)
        val reportUser = reportUser(id = "user-fl", publicCode = "#FL301", role = "USER")

        Mockito.`when`(fixture.courseRepository.findBySlug("no-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.reportUserRepository.findByPublicCode("#FL301")).thenReturn(Mono.just(reportUser))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", "user-fl"))
            .thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseEnrollmentRepository.save(ArgumentMatchers.any(CourseEnrollment::class.java)))
            .thenAnswer { invocation -> Mono.just(invocation.arguments[0] as CourseEnrollment) }

        StepVerifier.create(
            fixture.service.enrollMember(
                courseSlug = "no-basic",
                request = EnrollCourseRequest(publicCode = "#FL301"),
            )
        )
            .assertNext { enrollment ->
                enrollment.userId shouldBe "user-fl"
                enrollment.publicCode shouldBe "#FL301"
            }
            .verifyComplete()
    }

    "NO 코스는 SP 일반 유저도 등록할 수 있다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "no-basic", title = "NO 기초", fieldTag = CourseTrack.NO)
        val reportUser = reportUser(id = "user-sp", publicCode = "#SP201", role = "USER")

        Mockito.`when`(fixture.courseRepository.findBySlug("no-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.reportUserRepository.findByPublicCode("#SP201")).thenReturn(Mono.just(reportUser))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", "user-sp"))
            .thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseEnrollmentRepository.save(ArgumentMatchers.any(CourseEnrollment::class.java)))
            .thenAnswer { invocation -> Mono.just(invocation.arguments[0] as CourseEnrollment) }

        StepVerifier.create(
            fixture.service.enrollMember(
                courseSlug = "no-basic",
                request = EnrollCourseRequest(publicCode = "#SP201"),
            )
        )
            .assertNext { enrollment ->
                enrollment.userId shouldBe "user-sp"
                enrollment.publicCode shouldBe "#SP201"
            }
            .verifyComplete()
    }

    "ADMIN 은 어떤 코스에도 예외적으로 등록할 수 있다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "sp-basic", title = "SP 기초", fieldTag = CourseTrack.SP)
        val reportUser = reportUser(id = "admin-1", publicCode = "#AD001", role = "ADMIN")

        Mockito.`when`(fixture.courseRepository.findBySlug("sp-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.reportUserRepository.findByPublicCode("#AD001")).thenReturn(Mono.just(reportUser))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", "admin-1"))
            .thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseEnrollmentRepository.save(ArgumentMatchers.any(CourseEnrollment::class.java)))
            .thenAnswer { invocation -> Mono.just(invocation.arguments[0] as CourseEnrollment) }

        StepVerifier.create(
            fixture.service.enrollMember(
                courseSlug = "sp-basic",
                request = EnrollCourseRequest(publicCode = "#AD001"),
            )
        )
            .assertNext { enrollment ->
                enrollment.userId shouldBe "admin-1"
                enrollment.publicCode shouldBe "#AD001"
            }
            .verifyComplete()
    }

    "ORGANIZER 는 어떤 코스에도 예외적으로 등록할 수 있다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "fl-basic", title = "FL 기초", fieldTag = CourseTrack.FL)
        val reportUser = reportUser(id = "organizer-1", publicCode = "#OR001", role = "ORGANIZER")

        Mockito.`when`(fixture.courseRepository.findBySlug("fl-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.reportUserRepository.findByPublicCode("#OR001")).thenReturn(Mono.just(reportUser))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", "organizer-1"))
            .thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseEnrollmentRepository.save(ArgumentMatchers.any(CourseEnrollment::class.java)))
            .thenAnswer { invocation -> Mono.just(invocation.arguments[0] as CourseEnrollment) }

        StepVerifier.create(
            fixture.service.enrollMember(
                courseSlug = "fl-basic",
                request = EnrollCourseRequest(publicCode = "#OR001"),
            )
        )
            .assertNext { enrollment ->
                enrollment.userId shouldBe "organizer-1"
                enrollment.publicCode shouldBe "#OR001"
            }
            .verifyComplete()
    }

    "주차가 없으면 과제 생성 시 주차를 자동 생성한다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        var persistedAssignment: Assignment? = null
        val request = CreateAssignmentRequest(
            weekNo = 2,
            orderInWeek = 1,
            startAt = Instant.now().plusSeconds(3600),
            endAt = Instant.now().plusSeconds(7200),
            metadata = AssignmentMetadataPayload(
                title = "터미널 계산기",
                difficulty = AssignmentDifficulty.MID,
                description = "문제 설명",
                testCases = listOf(
                    CreateAssignmentExampleRequest(
                        seq = 1,
                        inputValues = emptyList(),
                        outputText = "0",
                        visibility = AssignmentTestCaseVisibility.PUBLIC,
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
                persistedAssignment = assignment
                Mono.just(assignment)
            }
        Mockito.`when`(fixture.assignmentRepository.findById(ArgumentMatchers.anyString()))
            .thenAnswer { Mono.just(requireNotNull(persistedAssignment)) }
        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val saved = invocation.arguments[0] as List<AssignmentExample>
            Flux.fromIterable(saved)
        }.`when`(fixture.assignmentExampleRepository)
            .saveAll(ArgumentMatchers.anyList<AssignmentExample>())
        Mockito.`when`(fixture.assignmentExampleRepository.findAllByAssignmentIdOrderBySeq(ArgumentMatchers.anyString()))
            .thenReturn(Flux.empty())

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

    "과제 생성 슬롯 중복은 새 주차를 생성하지 않고 CONFLICT 를 반환한다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val existing = commandAssignment(id = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111", courseId = "course-1", status = AssignmentStatus.DRAFT)
        val request = CreateAssignmentRequest(
            weekNo = 9,
            orderInWeek = 2,
            startAt = Instant.now().plusSeconds(3600),
            endAt = Instant.now().plusSeconds(7200),
            metadata = AssignmentMetadataPayload(
                title = "중복 슬롯",
                difficulty = AssignmentDifficulty.MID,
                description = "문제 설명",
                testCases = listOf(
                    CreateAssignmentExampleRequest(
                        seq = 1,
                        inputValues = listOf("1 2"),
                        outputText = "3",
                        visibility = AssignmentTestCaseVisibility.PUBLIC,
                    )
                ),
            ),
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(
            fixture.assignmentRepository.findByCourseIdAndWeekNoAndOrderInWeek(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyInt(),
                ArgumentMatchers.anyInt(),
            )
        )
            .thenReturn(Mono.just(existing))
        Mockito.`when`(
            fixture.courseWeekRepository.findByCourseIdAndWeekNo(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyInt(),
            )
        )
            .thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseWeekRepository.save(ArgumentMatchers.any(CourseWeek::class.java)))
            .thenAnswer { invocation -> Mono.just(invocation.arguments[0] as CourseWeek) }

        StepVerifier.create(fixture.service.createAssignment("back-basic", request, "admin"))
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.CONFLICT
                error.reason shouldBe "동일 코스/주차/순번 과제가 이미 존재합니다."
            }
            .verify()

        Mockito.verify(fixture.courseWeekRepository, Mockito.never()).save(ArgumentMatchers.any(CourseWeek::class.java))
    }

    "게시 상태로 생성된 과제는 EXCLUDED 를 제외한 케이스만 PROBLEM_CREATED 로 발행한다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val startAt = Instant.now().minusSeconds(3600)
        val endAt = Instant.now().plusSeconds(3600)
        var persistedAssignment: Assignment? = null
        var persistedTestCases: List<AssignmentExample> = emptyList()
        val request = CreateAssignmentRequest(
            weekNo = 1,
            orderInWeek = 3,
            startAt = startAt,
            endAt = endAt,
            metadata = AssignmentMetadataPayload(
                title = "Hello World!",
                difficulty = AssignmentDifficulty.LOW,
                description = "문제 설명",
                testCases = listOf(
                    CreateAssignmentExampleRequest(
                        seq = 1,
                        inputValues = listOf("ADD 1", "CLOSE"),
                        outputText = "3",
                        visibility = AssignmentTestCaseVisibility.PUBLIC,
                    ),
                    CreateAssignmentExampleRequest(
                        seq = 2,
                        inputValues = listOf("2 3"),
                        outputText = "5",
                        visibility = AssignmentTestCaseVisibility.HIDDEN,
                    ),
                    CreateAssignmentExampleRequest(
                        seq = 3,
                        inputValues = listOf("9 9"),
                        outputText = "18",
                        visibility = AssignmentTestCaseVisibility.EXCLUDED,
                    ),
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
            .thenAnswer { invocation ->
                val assignment = invocation.arguments[0] as Assignment
                persistedAssignment = assignment
                Mono.just(assignment)
            }
        Mockito.`when`(fixture.assignmentRepository.findById(ArgumentMatchers.anyString()))
            .thenAnswer { Mono.just(requireNotNull(persistedAssignment)) }
        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val saved = invocation.arguments[0] as List<AssignmentExample>
            persistedTestCases = saved
            Flux.fromIterable(saved)
        }.`when`(fixture.assignmentExampleRepository)
            .saveAll(ArgumentMatchers.anyList<AssignmentExample>())
        Mockito.`when`(fixture.assignmentExampleRepository.findAllByAssignmentIdOrderBySeq(ArgumentMatchers.anyString()))
            .thenAnswer { Flux.fromIterable(persistedTestCases) }

        StepVerifier.create(fixture.service.createAssignment("back-basic", request, "admin"))
            .assertNext { created ->
                java.util.UUID.fromString(created.id).toString() shouldBe created.id
            }
            .verifyComplete()

        fixture.assignmentReportTestCaseEventPublisher.events.single().eventType shouldBe AssignmentReportTestCaseEventType.PROBLEM_CREATED
        java.util.UUID.fromString(fixture.assignmentReportTestCaseEventPublisher.events.single().problemId).toString() shouldBe fixture.assignmentReportTestCaseEventPublisher.events.single().problemId
        fixture.assignmentReportTestCaseEventPublisher.events.single().testCases shouldHaveSize 2
        fixture.assignmentReportTestCaseEventPublisher.events.single().testCases.first().caseId shouldBe 1
        fixture.assignmentReportTestCaseEventPublisher.events.single().testCases.first().input shouldBe listOf("ADD 1", "CLOSE")
        fixture.assignmentReportTestCaseEventPublisher.events.single().testCases.first().output shouldBe "3"
        fixture.assignmentReportTestCaseEventPublisher.events.single().testCases.last().caseId shouldBe 2
    }

    "예약 공개 과제는 PUBLISHED 상태로 저장하고 응답은 DRAFT 로 반환한다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val startAt = Instant.now().plusSeconds(3600)
        val endAt = Instant.now().plusSeconds(7200)
        var persistedAssignment: Assignment? = null
        var persistedTestCases: List<AssignmentExample> = emptyList()
        val request = CreateAssignmentRequest(
            weekNo = 1,
            orderInWeek = 4,
            startAt = startAt,
            endAt = endAt,
            metadata = AssignmentMetadataPayload(
                title = "Draft Assignment",
                difficulty = AssignmentDifficulty.LOW,
                description = "문제 설명",
                testCases = listOf(
                    CreateAssignmentExampleRequest(
                        seq = 1,
                        inputValues = listOf("1 2"),
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
            .thenAnswer { invocation ->
                val assignment = invocation.arguments[0] as Assignment
                persistedAssignment = assignment
                Mono.just(assignment)
            }
        Mockito.`when`(fixture.assignmentRepository.findById(ArgumentMatchers.anyString()))
            .thenAnswer { Mono.just(requireNotNull(persistedAssignment)) }
        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val saved = invocation.arguments[0] as List<AssignmentExample>
            persistedTestCases = saved
            Flux.fromIterable(saved)
        }.`when`(fixture.assignmentExampleRepository)
            .saveAll(ArgumentMatchers.anyList<AssignmentExample>())
        Mockito.`when`(fixture.assignmentExampleRepository.findAllByAssignmentIdOrderBySeq(ArgumentMatchers.anyString()))
            .thenAnswer { Flux.fromIterable(persistedTestCases) }

        StepVerifier.create(fixture.service.createAssignment("back-basic", request, "admin"))
            .assertNext { created ->
                created.status shouldBe AssignmentStatus.DRAFT
                created.publishedAt shouldBe null
            }
            .verifyComplete()

        requireNotNull(persistedAssignment).status shouldBe AssignmentStatus.PUBLISHED
        requireNotNull(persistedAssignment).publishedAt shouldBe startAt
        fixture.assignmentReportTestCaseEventPublisher.events.single().eventType shouldBe AssignmentReportTestCaseEventType.PROBLEM_CREATED
        java.util.UUID.fromString(fixture.assignmentReportTestCaseEventPublisher.events.single().problemId).toString() shouldBe fixture.assignmentReportTestCaseEventPublisher.events.single().problemId
        fixture.assignmentReportTestCaseEventPublisher.events.single().testCases shouldHaveSize 1
        fixture.assignmentReportTestCaseEventPublisher.events.single().testCases.first().caseId shouldBe 1
        fixture.assignmentReportTestCaseEventPublisher.events.single().testCases.first().input shouldBe listOf("1 2")
        fixture.assignmentReportTestCaseEventPublisher.events.single().testCases.first().output shouldBe "3"
    }

    "과제 복사는 새 ID로 본문을 복사하고 DRAFT 로 저장한 뒤 생성 이벤트를 발행한다" {
        val fixture = CommandFixture()
        val sourceId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val sourceCourse = queryCourse(id = "source-course-id", slug = "source-course", title = "원본 코스")
        val targetCourse = queryCourse(id = "target-course-id", slug = "target-course", title = "대상 코스")
        val sourceAssignment = commandAssignment(
            id = sourceId,
            courseId = "source-course-id",
            courseSlug = "source-course",
            status = AssignmentStatus.PUBLISHED,
        )
        val sourceRequirements = listOf(AssignmentRequirement(assignmentId = sourceId, sortOrder = 1, requirementText = "함수 분리 필수"))
        val sourceTestCases = listOf(
            AssignmentExample(
                assignmentId = sourceId,
                seq = 1,
                inputValues = listOf("1 2"),
                outputText = "3",
                visibility = AssignmentTestCaseVisibility.PUBLIC,
            )
        )
        var persistedAssignment: Assignment? = null
        var persistedRequirements: List<AssignmentRequirement> = emptyList()
        var persistedTestCases: List<AssignmentExample> = emptyList()

        stubCopyHappyPath(
            fixture = fixture,
            sourceCourse = sourceCourse,
            targetCourse = targetCourse,
            sourceAssignment = sourceAssignment,
            sourceRequirements = sourceRequirements,
            sourceTestCases = sourceTestCases,
            persistedAssignment = { persistedAssignment },
            onPersistAssignment = { persistedAssignment = it },
            onPersistRequirements = { persistedRequirements = it },
            onPersistTestCases = { persistedTestCases = it },
        )

        StepVerifier.create(
            fixture.service.copyAssignment(
                targetCourseSlug = "target-course",
                request = CopyAssignmentRequest(
                    sourceAssignmentId = sourceId,
                    targetWeekNo = 2,
                    targetOrderInWeek = 3,
                ),
                createdBy = "admin",
            )
        )
            .assertNext { copied ->
                val saved = requireNotNull(persistedAssignment)
                copied.id shouldBe saved.id
                (copied.id == sourceId) shouldBe false
                copied.courseSlug shouldBe "target-course"
                copied.weekNo shouldBe 2
                copied.orderInWeek shouldBe 3
                copied.status shouldBe AssignmentStatus.DRAFT
                copied.publishedAt shouldBe null
                copied.metadata.title shouldBe sourceAssignment.metadata.title
                copied.metadata.difficulty shouldBe sourceAssignment.metadata.difficulty
                copied.metadata.description shouldBe sourceAssignment.metadata.description
                copied.metadata.requirements.single().requirementText shouldBe "함수 분리 필수"
                copied.metadata.testCases.single().outputText shouldBe "3"
            }
            .verifyComplete()

        val saved = requireNotNull(persistedAssignment)
        saved.id shouldBe fixture.assignmentReportTestCaseEventPublisher.events.single().problemId
        saved.id shouldBe persistedRequirements.single().assignmentId
        saved.id shouldBe persistedTestCases.single().assignmentId
        saved.status shouldBe AssignmentStatus.DRAFT
        saved.publishedAt shouldBe null
        saved.originAssignmentId shouldBe sourceId
        saved.originCourseSlug shouldBe "source-course"
        saved.copyFingerprint.isNullOrBlank() shouldBe false
        fixture.assignmentReportTestCaseEventPublisher.events.single().eventType shouldBe AssignmentReportTestCaseEventType.PROBLEM_CREATED
        fixture.assignmentReportTestCaseEventPublisher.events.single().testCases shouldHaveSize 1
    }

    "같은 원본 과제를 같은 대상 코스에 다시 복사하면 CONFLICT 를 반환한다" {
        val fixture = CommandFixture()
        val sourceId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val sourceCourse = queryCourse(id = "source-course-id", slug = "source-course", title = "원본 코스")
        val targetCourse = queryCourse(id = "target-course-id", slug = "target-course", title = "대상 코스")
        val sourceAssignment = commandAssignment(id = sourceId, courseId = "source-course-id", courseSlug = "source-course", status = AssignmentStatus.PUBLISHED)
        val existing = commandAssignment(
            id = "7c53f1b3-0df8-4a9d-a56d-a5f50b96b7a1",
            courseId = "target-course-id",
            courseSlug = "target-course",
            status = AssignmentStatus.DRAFT,
            originAssignmentId = sourceId,
        )

        stubCopyHappyPath(
            fixture = fixture,
            sourceCourse = sourceCourse,
            targetCourse = targetCourse,
            sourceAssignment = sourceAssignment,
            persistedAssignment = { null },
            onPersistAssignment = {},
        )
        Mockito.`when`(fixture.assignmentRepository.findByCourseIdAndOriginAssignmentId("target-course-id", sourceId))
            .thenReturn(Mono.just(existing))
        Mockito.`when`(fixture.courseWeekRepository.findByCourseIdAndWeekNo(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt()))
            .thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseWeekRepository.save(ArgumentMatchers.any(CourseWeek::class.java)))
            .thenAnswer { invocation -> Mono.just(invocation.arguments[0] as CourseWeek) }

        StepVerifier.create(
            fixture.service.copyAssignment("target-course", CopyAssignmentRequest(sourceAssignmentId = sourceId), "admin")
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.CONFLICT
                error.reason shouldBe "이미 대상 코스에 동일한 원본 과제가 존재합니다."
            }
            .verify()

        Mockito.verify(fixture.courseWeekRepository, Mockito.never()).save(ArgumentMatchers.any(CourseWeek::class.java))
    }

    "복사본을 다시 복사해도 최초 원본 ID를 유지한다" {
        val fixture = CommandFixture()
        val originalId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val copyId = "7c53f1b3-0df8-4a9d-a56d-a5f50b96b7a1"
        val copiedSourceCourse = queryCourse(id = "copied-course-id", slug = "copied-course", title = "복사본 코스")
        val targetCourse = queryCourse(id = "target-course-id", slug = "target-course", title = "대상 코스")
        val copiedSource = commandAssignment(
            id = copyId,
            courseId = "copied-course-id",
            courseSlug = "copied-course",
            status = AssignmentStatus.DRAFT,
            originAssignmentId = originalId,
            originCourseSlug = "source-course",
        )
        var persistedAssignment: Assignment? = null

        stubCopyHappyPath(
            fixture = fixture,
            sourceCourse = copiedSourceCourse,
            targetCourse = targetCourse,
            sourceAssignment = copiedSource,
            persistedAssignment = { persistedAssignment },
            onPersistAssignment = { persistedAssignment = it },
        )

        StepVerifier.create(
            fixture.service.copyAssignment("target-course", CopyAssignmentRequest(sourceAssignmentId = copyId), "admin")
        )
            .assertNext { it.id shouldBe requireNotNull(persistedAssignment).id }
            .verifyComplete()

        requireNotNull(persistedAssignment).originAssignmentId shouldBe originalId
        requireNotNull(persistedAssignment).originCourseSlug shouldBe "source-course"
    }

    "내용 fingerprint 가 같은 과제가 대상 코스에 있으면 CONFLICT 를 반환한다" {
        val fixture = CommandFixture()
        val sourceId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val sourceCourse = queryCourse(id = "source-course-id", slug = "source-course", title = "원본 코스")
        val targetCourse = queryCourse(id = "target-course-id", slug = "target-course", title = "대상 코스")
        val sourceAssignment = commandAssignment(id = sourceId, courseId = "source-course-id", courseSlug = "source-course", status = AssignmentStatus.PUBLISHED)
        val existing = commandAssignment(id = "7c53f1b3-0df8-4a9d-a56d-a5f50b96b7a1", courseId = "target-course-id", courseSlug = "target-course", status = AssignmentStatus.DRAFT)

        stubCopyHappyPath(
            fixture = fixture,
            sourceCourse = sourceCourse,
            targetCourse = targetCourse,
            sourceAssignment = sourceAssignment,
            persistedAssignment = { null },
            onPersistAssignment = {},
        )
        Mockito.`when`(fixture.assignmentRepository.findByCourseIdAndCopyFingerprint(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
            .thenReturn(Mono.just(existing))
        Mockito.`when`(fixture.courseWeekRepository.findByCourseIdAndWeekNo(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt()))
            .thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseWeekRepository.save(ArgumentMatchers.any(CourseWeek::class.java)))
            .thenAnswer { invocation -> Mono.just(invocation.arguments[0] as CourseWeek) }

        StepVerifier.create(
            fixture.service.copyAssignment("target-course", CopyAssignmentRequest(sourceAssignmentId = sourceId), "admin")
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.CONFLICT
                error.reason shouldBe "이미 대상 코스에 동일한 내용의 과제가 존재합니다."
            }
            .verify()

        Mockito.verify(fixture.courseWeekRepository, Mockito.never()).save(ArgumentMatchers.any(CourseWeek::class.java))
    }

    "과제 복사 저장 중 DuplicateKeyException 이 발생하면 CONFLICT 로 변환한다" {
        val fixture = CommandFixture()
        val sourceId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val sourceCourse = queryCourse(id = "source-course-id", slug = "source-course", title = "원본 코스")
        val targetCourse = queryCourse(id = "target-course-id", slug = "target-course", title = "대상 코스")
        val sourceAssignment = commandAssignment(id = sourceId, courseId = "source-course-id", courseSlug = "source-course", status = AssignmentStatus.PUBLISHED)

        stubCopyHappyPath(
            fixture = fixture,
            sourceCourse = sourceCourse,
            targetCourse = targetCourse,
            sourceAssignment = sourceAssignment,
            persistedAssignment = { null },
            onPersistAssignment = {},
        )
        Mockito.`when`(fixture.assignmentRepository.save(ArgumentMatchers.any(Assignment::class.java)))
            .thenReturn(Mono.error(DuplicateKeyException("duplicate copy")))

        StepVerifier.create(
            fixture.service.copyAssignment("target-course", CopyAssignmentRequest(sourceAssignmentId = sourceId), "admin")
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.CONFLICT
                error.reason shouldBe "이미 대상 코스에 동일한 원본 또는 동일한 내용의 과제가 존재합니다."
            }
            .verify()
    }

    "과제 복사 본문 저장 중 DuplicateKeyException 이 발생하면 정리 후 CONFLICT 로 변환한다" {
        val fixture = CommandFixture()
        val sourceId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val sourceCourse = queryCourse(id = "source-course-id", slug = "source-course", title = "원본 코스")
        val targetCourse = queryCourse(id = "target-course-id", slug = "target-course", title = "대상 코스")
        val sourceAssignment = commandAssignment(id = sourceId, courseId = "source-course-id", courseSlug = "source-course", status = AssignmentStatus.PUBLISHED)
        val sourceRequirements = listOf(AssignmentRequirement(assignmentId = sourceId, sortOrder = 1, requirementText = "함수 분리 필수"))
        var persistedAssignment: Assignment? = null

        stubCopyHappyPath(
            fixture = fixture,
            sourceCourse = sourceCourse,
            targetCourse = targetCourse,
            sourceAssignment = sourceAssignment,
            sourceRequirements = sourceRequirements,
            persistedAssignment = { persistedAssignment },
            onPersistAssignment = { persistedAssignment = it },
        )
        Mockito.`when`(fixture.assignmentRequirementRepository.saveAll(ArgumentMatchers.anyList<AssignmentRequirement>()))
            .thenReturn(Flux.error(DuplicateKeyException("duplicate requirement")))
        Mockito.`when`(fixture.assignmentRequirementRepository.deleteAllByAssignmentIdIn(ArgumentMatchers.anyCollection()))
            .thenReturn(Mono.just(0))
        Mockito.`when`(fixture.assignmentExampleRepository.deleteAllByAssignmentIdIn(ArgumentMatchers.anyCollection()))
            .thenReturn(Mono.just(0))
        Mockito.`when`(fixture.assignmentDeliveryRepository.deleteAllByAssignmentIdIn(ArgumentMatchers.anyCollection()))
            .thenReturn(Mono.just(0))
        Mockito.`when`(fixture.assignmentRepository.deleteById(ArgumentMatchers.anyString()))
            .thenReturn(Mono.empty())

        StepVerifier.create(
            fixture.service.copyAssignment("target-course", CopyAssignmentRequest(sourceAssignmentId = sourceId), "admin")
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.CONFLICT
                error.reason shouldBe "이미 대상 코스에 동일한 원본 또는 동일한 내용의 과제가 존재합니다."
            }
            .verify()

        Mockito.verify(fixture.assignmentRepository).deleteById(requireNotNull(requireNotNull(persistedAssignment).id))
    }

    "대상 코스의 같은 주차 순번 슬롯이 사용 중이면 CONFLICT 를 반환한다" {
        val fixture = CommandFixture()
        val sourceId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val sourceCourse = queryCourse(id = "source-course-id", slug = "source-course", title = "원본 코스")
        val targetCourse = queryCourse(id = "target-course-id", slug = "target-course", title = "대상 코스")
        val sourceAssignment = commandAssignment(id = sourceId, courseId = "source-course-id", courseSlug = "source-course", status = AssignmentStatus.PUBLISHED)
        val existing = commandAssignment(id = "7c53f1b3-0df8-4a9d-a56d-a5f50b96b7a1", courseId = "target-course-id", courseSlug = "target-course", status = AssignmentStatus.DRAFT)

        stubCopyHappyPath(
            fixture = fixture,
            sourceCourse = sourceCourse,
            targetCourse = targetCourse,
            sourceAssignment = sourceAssignment,
            persistedAssignment = { null },
            onPersistAssignment = {},
        )
        Mockito.`when`(fixture.assignmentRepository.findByCourseIdAndWeekNoAndOrderInWeek("target-course-id", 2, 1))
            .thenReturn(Mono.just(existing))
        Mockito.`when`(fixture.courseWeekRepository.findByCourseIdAndWeekNo("target-course-id", 2))
            .thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseWeekRepository.save(ArgumentMatchers.any(CourseWeek::class.java)))
            .thenAnswer { invocation -> Mono.just(invocation.arguments[0] as CourseWeek) }

        StepVerifier.create(
            fixture.service.copyAssignment(
                "target-course",
                CopyAssignmentRequest(sourceAssignmentId = sourceId, targetWeekNo = 2),
                "admin",
            )
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.CONFLICT
                error.reason shouldBe "동일 코스/주차/순번 과제가 이미 존재합니다."
            }
            .verify()

        Mockito.verify(fixture.courseWeekRepository, Mockito.never()).save(ArgumentMatchers.any(CourseWeek::class.java))
    }

    "과제 복사는 대상 코스와 원본 과제 없음 및 잘못된 날짜를 명확히 거절한다" {
        val fixture = CommandFixture()
        val sourceId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"

        Mockito.`when`(fixture.courseRepository.findBySlug("missing-course")).thenReturn(Mono.empty())
        StepVerifier.create(
            fixture.service.copyAssignment("missing-course", CopyAssignmentRequest(sourceAssignmentId = sourceId), "admin")
        )
            .expectErrorSatisfies { (it as ResponseStatusException).statusCode shouldBe HttpStatus.NOT_FOUND }
            .verify()

        val targetCourse = queryCourse(id = "target-course-id", slug = "target-course", title = "대상 코스")
        Mockito.`when`(fixture.courseRepository.findBySlug("target-course")).thenReturn(Mono.just(targetCourse))
        Mockito.`when`(fixture.assignmentRepository.findById(sourceId)).thenReturn(Mono.empty())
        StepVerifier.create(
            fixture.service.copyAssignment("target-course", CopyAssignmentRequest(sourceAssignmentId = sourceId), "admin")
        )
            .expectErrorSatisfies { (it as ResponseStatusException).statusCode shouldBe HttpStatus.NOT_FOUND }
            .verify()

        val sourceCourse = queryCourse(id = "source-course-id", slug = "source-course", title = "원본 코스")
        val sourceAssignment = commandAssignment(id = sourceId, courseId = "source-course-id", courseSlug = "source-course", status = AssignmentStatus.PUBLISHED)
        stubCopyHappyPath(
            fixture = fixture,
            sourceCourse = sourceCourse,
            targetCourse = targetCourse,
            sourceAssignment = sourceAssignment,
            persistedAssignment = { null },
            onPersistAssignment = {},
        )
        StepVerifier.create(
            fixture.service.copyAssignment(
                "target-course",
                CopyAssignmentRequest(
                    sourceAssignmentId = sourceId,
                    targetStartAt = Instant.parse("2026-05-19T00:00:00Z"),
                    targetEndAt = Instant.parse("2026-05-12T00:00:00Z"),
                ),
                "admin",
            )
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.BAD_REQUEST
                error.reason shouldBe "과제 종료 시간은 시작 시간보다 빠를 수 없습니다."
            }
            .verify()
    }

    "과제 복사는 필수 sourceAssignmentId 와 대상 주차 순번 최소값을 검증한다" {
        val fixture = CommandFixture()
        val sourceId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"

        StepVerifier.create(
            fixture.service.copyAssignment("target-course", CopyAssignmentRequest(sourceAssignmentId = " "), "admin")
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.BAD_REQUEST
                error.reason shouldBe "sourceAssignmentId는 필수입니다."
            }
            .verify()

        val sourceCourse = queryCourse(id = "source-course-id", slug = "source-course", title = "원본 코스")
        val targetCourse = queryCourse(id = "target-course-id", slug = "target-course", title = "대상 코스")
        val sourceAssignment = commandAssignment(id = sourceId, courseId = "source-course-id", courseSlug = "source-course", status = AssignmentStatus.PUBLISHED)
        stubCopyHappyPath(
            fixture = fixture,
            sourceCourse = sourceCourse,
            targetCourse = targetCourse,
            sourceAssignment = sourceAssignment,
            persistedAssignment = { null },
            onPersistAssignment = {},
        )

        StepVerifier.create(
            fixture.service.copyAssignment("target-course", CopyAssignmentRequest(sourceAssignmentId = sourceId, targetWeekNo = 0), "admin")
        )
            .expectErrorSatisfies { (it as ResponseStatusException).statusCode shouldBe HttpStatus.BAD_REQUEST }
            .verify()

        StepVerifier.create(
            fixture.service.copyAssignment("target-course", CopyAssignmentRequest(sourceAssignmentId = sourceId, targetOrderInWeek = 0), "admin")
        )
            .expectErrorSatisfies { (it as ResponseStatusException).statusCode shouldBe HttpStatus.BAD_REQUEST }
            .verify()
    }

    "EXCLUDED 만 있는 testCases 로 과제를 생성할 수 없다" {
        val fixture = CommandFixture()
        val startAt = Instant.now().plusSeconds(3600)
        val endAt = Instant.now().plusSeconds(7200)
        val request = CreateAssignmentRequest(
            weekNo = 1,
            orderInWeek = 5,
            startAt = startAt,
            endAt = endAt,
            metadata = AssignmentMetadataPayload(
                title = "Excluded only",
                difficulty = AssignmentDifficulty.LOW,
                description = "문제 설명",
                testCases = listOf(
                    CreateAssignmentExampleRequest(
                        seq = 1,
                        inputValues = listOf("1 2"),
                        outputText = "3",
                        visibility = AssignmentTestCaseVisibility.EXCLUDED,
                    )
                ),
            ),
        )

        val error = shouldThrow<ResponseStatusException> {
            fixture.service.createAssignment("back-basic", request, "admin")
        }

        error.statusCode.value() shouldBe 400
        error.reason shouldBe "testCases must contain at least one gradable case"

        fixture.assignmentReportTestCaseEventPublisher.events shouldBe emptyList()
    }

    "게시된 과제 수정은 일부 케이스 삭제가 있어도 최종 전체 배열로 PROBLEM_UPDATED 를 발행한다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val target = commandAssignment(id = assignmentId, courseId = "course-1", status = AssignmentStatus.PUBLISHED)
        var persistedAssignment: Assignment = target
        var persistedTestCases: List<AssignmentExample> = emptyList()
        val updateRequest = UpdateAssignmentRequest(
            metadata = AssignmentMetadataPayload(
                title = "updated title",
                difficulty = AssignmentDifficulty.LOW,
                description = "updated description",
                testCases = listOf(
                    CreateAssignmentExampleRequest(
                        seq = 1,
                        inputValues = listOf("updated input"),
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
            .thenAnswer { invocation ->
                val assignment = invocation.arguments[0] as Assignment
                persistedAssignment = assignment
                Mono.just(assignment)
            }
        Mockito.`when`(fixture.assignmentRepository.findById(assignmentId)).thenAnswer { Mono.just(persistedAssignment) }
        Mockito.`when`(fixture.assignmentRequirementRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)))
            .thenReturn(Mono.just(0))
        Mockito.`when`(fixture.assignmentExampleRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)))
            .thenReturn(Mono.just(2))
        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val saved = invocation.arguments[0] as List<AssignmentExample>
            persistedTestCases = saved
            Flux.fromIterable(saved)
        }.`when`(fixture.assignmentExampleRepository)
            .saveAll(ArgumentMatchers.anyList<AssignmentExample>())
        Mockito.`when`(fixture.assignmentExampleRepository.findAllByAssignmentIdOrderBySeq(assignmentId))
            .thenAnswer { Flux.fromIterable(persistedTestCases) }

        StepVerifier.create(
            fixture.service.updateAssignment(
                courseSlug = "back-basic",
                assignmentId = assignmentId,
                request = updateRequest,
            )
        )
            .assertNext { updated ->
                updated.metadata.examples shouldHaveSize 1
                updated.metadata.examples.first().inputValues shouldBe listOf("updated input")
                updated.status shouldBe AssignmentStatus.PUBLISHED
                updated.publishedAt shouldBe target.publishedAt
            }
            .verifyComplete()

        fixture.assignmentReportTestCaseEventPublisher.events.single().eventType shouldBe AssignmentReportTestCaseEventType.PROBLEM_UPDATED
        fixture.assignmentReportTestCaseEventPublisher.events.single().problemId shouldBe assignmentId
        fixture.assignmentReportTestCaseEventPublisher.events.single().testCases shouldHaveSize 1
        fixture.assignmentReportTestCaseEventPublisher.events.single().testCases.first().caseId shouldBe 1
        fixture.assignmentReportTestCaseEventPublisher.events.single().testCases.first().input shouldBe listOf("updated input")
        fixture.assignmentReportTestCaseEventPublisher.events.single().testCases.first().output shouldBe "updated output"
    }

    "게시된 과제의 비테스트케이스 수정도 최신 snapshot 으로 PROBLEM_UPDATED 를 재발행한다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val target = commandAssignment(id = assignmentId, courseId = "course-1", status = AssignmentStatus.PUBLISHED)
        val persistedAssignment = target.copy(orderInWeek = 2)

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
            .thenReturn(Mono.just(persistedAssignment))
        Mockito.`when`(fixture.assignmentRepository.findById(assignmentId)).thenReturn(Mono.just(persistedAssignment))
        Mockito.`when`(fixture.assignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder(assignmentId))
            .thenReturn(Flux.empty())
        Mockito.`when`(fixture.assignmentExampleRepository.findAllByAssignmentIdOrderBySeq(assignmentId))
            .thenReturn(
                Flux.just(
                    AssignmentExample(
                        id = "ex-1",
                        assignmentId = assignmentId,
                        seq = 1,
                        inputValues = listOf("persisted input"),
                        outputText = "persisted output",
                        visibility = AssignmentTestCaseVisibility.PUBLIC,
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

        fixture.assignmentReportTestCaseEventPublisher.events.single().eventType shouldBe AssignmentReportTestCaseEventType.PROBLEM_UPDATED
        fixture.assignmentReportTestCaseEventPublisher.events.single().testCases shouldHaveSize 1
    }

    "PATCH 에서 metadata 는 있지만 testCases 를 생략하면 기존 testCases 를 유지한다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val target = commandAssignment(id = assignmentId, courseId = "course-1", status = AssignmentStatus.PUBLISHED)
        val persistedAssignment = target.copy(metadata = target.metadata.copy(title = "updated title"))

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
            .thenReturn(Mono.just(persistedAssignment))
        Mockito.`when`(fixture.assignmentRepository.findById(assignmentId)).thenReturn(Mono.just(persistedAssignment))
        Mockito.`when`(fixture.assignmentRequirementRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)))
            .thenReturn(Mono.just(0))
        Mockito.`when`(fixture.assignmentExampleRepository.findAllByAssignmentIdOrderBySeq(assignmentId))
            .thenReturn(
                Flux.just(
                    AssignmentExample(
                        id = "ex-1",
                        assignmentId = assignmentId,
                        seq = 1,
                        inputValues = listOf("persisted input"),
                        outputText = "persisted output",
                        visibility = AssignmentTestCaseVisibility.PUBLIC,
                    )
                )
            )

        StepVerifier.create(
            fixture.service.updateAssignment(
                courseSlug = "back-basic",
                assignmentId = assignmentId,
                request = UpdateAssignmentRequest(
                    metadata = AssignmentMetadataPayload(
                        title = "updated title",
                        difficulty = AssignmentDifficulty.LOW,
                        description = "updated description",
                    )
                ),
            )
        )
            .assertNext { updated ->
                updated.metadata.examples shouldHaveSize 1
                updated.metadata.examples.first().inputValues shouldBe listOf("persisted input")
            }
            .verifyComplete()

        Mockito.verify(fixture.assignmentExampleRepository, Mockito.never())
            .deleteAllByAssignmentIdIn(listOf(assignmentId))
        fixture.assignmentReportTestCaseEventPublisher.events.single().testCases shouldHaveSize 1
    }

    "draft 과제 수정도 OJ test case 이벤트를 PROBLEM_UPDATED 로 재발행한다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val target = commandAssignment(id = assignmentId, courseId = "course-1", status = AssignmentStatus.DRAFT)
        var persistedAssignment: Assignment = target

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
            .thenAnswer { invocation ->
                val assignment = invocation.arguments[0] as Assignment
                persistedAssignment = assignment
                Mono.just(assignment)
            }
        Mockito.`when`(fixture.assignmentRepository.findById(assignmentId)).thenAnswer { Mono.just(persistedAssignment) }
        Mockito.`when`(fixture.assignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder(assignmentId))
            .thenReturn(Flux.empty())
        Mockito.`when`(fixture.assignmentExampleRepository.findAllByAssignmentIdOrderBySeq(assignmentId))
            .thenReturn(
                Flux.just(
                    AssignmentExample(
                        id = "ex-1",
                        assignmentId = assignmentId,
                        seq = 1,
                        inputValues = listOf("persisted input"),
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

        fixture.assignmentReportTestCaseEventPublisher.events.single().eventType shouldBe AssignmentReportTestCaseEventType.PROBLEM_UPDATED
        fixture.assignmentReportTestCaseEventPublisher.events.single().problemId shouldBe assignmentId
        fixture.assignmentReportTestCaseEventPublisher.events.single().testCases shouldHaveSize 1
    }

    "EXCLUDED 만 있는 testCases 로 과제를 수정할 수 없다" {
        val fixture = CommandFixture()
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val updateRequest = UpdateAssignmentRequest(
            metadata = AssignmentMetadataPayload(
                title = "updated title",
                difficulty = AssignmentDifficulty.LOW,
                description = "updated description",
                testCases = listOf(
                    CreateAssignmentExampleRequest(
                        seq = 1,
                        inputValues = listOf("new input"),
                        outputText = "new output",
                        visibility = AssignmentTestCaseVisibility.EXCLUDED,
                    )
                ),
            )
        )

        val error = shouldThrow<ResponseStatusException> {
            fixture.service.updateAssignment(
                courseSlug = "back-basic",
                assignmentId = assignmentId,
                request = updateRequest,
            )
        }

        error.statusCode.value() shouldBe 400
        error.reason shouldBe "testCases must contain at least one gradable case"

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
    val assignmentTestCaseValidator = AssignmentTestCaseValidator()
    val assignmentCopyFingerprintCalculator = AssignmentCopyFingerprintCalculator()
    val assignmentMetadataPayloadTestCasePresenceTracker = AssignmentMetadataPayloadTestCasePresenceTracker()
    val courseEnrollmentCommandService = CourseEnrollmentCommandService(
        courseRepository = courseRepository,
        courseEnrollmentRepository = courseEnrollmentRepository,
        reportUserRepository = reportUserRepository,
    )
    val assignmentCopyService = AssignmentCopyService(
        courseRepository = courseRepository,
        courseWeekRepository = courseWeekRepository,
        assignmentRepository = assignmentRepository,
        assignmentRequirementRepository = assignmentRequirementRepository,
        assignmentTestCaseRepository = assignmentExampleRepository,
        assignmentDeliveryRepository = assignmentDeliveryRepository,
        assignmentReportTestCaseEventMapper = assignmentReportTestCaseEventMapper,
        assignmentReportTestCaseEventPublisher = assignmentReportTestCaseEventPublisher,
        assignmentCopyFingerprintCalculator = assignmentCopyFingerprintCalculator,
    )
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
        courseEnrollmentCommandService = courseEnrollmentCommandService,
        assignmentCopyService = assignmentCopyService,
        assignmentTestCaseValidator = assignmentTestCaseValidator,
        assignmentMetadataPayloadTestCasePresenceTracker = assignmentMetadataPayloadTestCasePresenceTracker,
    )

}

private class RecordingAssignmentReportTestCaseEventPublisher : AssignmentReportTestCaseEventPublisher {
    val events = mutableListOf<AssignmentReportTestCaseEvent>()

    override fun publish(event: AssignmentReportTestCaseEvent): Mono<Void> {
        events += event
        return Mono.empty()
    }
}

private fun stubCopyHappyPath(
    fixture: CommandFixture,
    sourceCourse: Course,
    targetCourse: Course,
    sourceAssignment: Assignment,
    sourceRequirements: List<AssignmentRequirement> = emptyList(),
    sourceTestCases: List<AssignmentExample> = emptyList(),
    persistedAssignment: () -> Assignment?,
    onPersistAssignment: (Assignment) -> Unit,
    onPersistRequirements: (List<AssignmentRequirement>) -> Unit = {},
    onPersistTestCases: (List<AssignmentExample>) -> Unit = {},
) {
    val sourceId = requireNotNull(sourceAssignment.id)
    val targetCourseId = requireNotNull(targetCourse.id)

    Mockito.`when`(fixture.courseRepository.findBySlug(targetCourse.slug)).thenReturn(Mono.just(targetCourse))
    Mockito.`when`(fixture.courseRepository.findById(sourceAssignment.courseId)).thenReturn(Mono.just(sourceCourse))
    Mockito.`when`(fixture.assignmentRepository.findById(ArgumentMatchers.anyString()))
        .thenAnswer { invocation ->
            when (invocation.arguments[0] as String) {
                sourceId -> Mono.just(sourceAssignment)
                persistedAssignment()?.id -> Mono.just(requireNotNull(persistedAssignment()))
                else -> Mono.empty<Assignment>()
            }
        }
    Mockito.`when`(fixture.assignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder(sourceId))
        .thenReturn(Flux.fromIterable(sourceRequirements))
    Mockito.`when`(fixture.assignmentExampleRepository.findAllByAssignmentIdOrderBySeq(ArgumentMatchers.anyString()))
        .thenAnswer { invocation ->
            val assignmentId = invocation.arguments[0] as String
            if (assignmentId == sourceId) {
                Flux.fromIterable(sourceTestCases)
            } else {
                Flux.fromIterable(sourceTestCases.map { it.copy(assignmentId = requireNotNull(persistedAssignment()?.id)) })
            }
        }
    Mockito.`when`(fixture.courseWeekRepository.findByCourseIdAndWeekNo(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt()))
        .thenReturn(Mono.just(CourseWeek(id = "target-week", courseId = targetCourseId, weekNo = 1, title = "1주차")))
    Mockito.`when`(fixture.assignmentRepository.findByCourseIdAndOriginAssignmentId(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
        .thenReturn(Mono.empty())
    Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
        .thenReturn(Mono.empty())
    Mockito.`when`(fixture.assignmentRepository.findByCourseIdAndCopyFingerprint(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
        .thenReturn(Mono.empty())
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
            onPersistAssignment(assignment)
            Mono.just(assignment)
        }
    Mockito.doAnswer { invocation ->
        @Suppress("UNCHECKED_CAST")
        val saved = invocation.arguments[0] as List<AssignmentRequirement>
        onPersistRequirements(saved)
        Flux.fromIterable(saved)
    }.`when`(fixture.assignmentRequirementRepository)
        .saveAll(ArgumentMatchers.anyList<AssignmentRequirement>())
    Mockito.doAnswer { invocation ->
        @Suppress("UNCHECKED_CAST")
        val saved = invocation.arguments[0] as List<AssignmentExample>
        onPersistTestCases(saved)
        Flux.fromIterable(saved)
    }.`when`(fixture.assignmentExampleRepository)
        .saveAll(ArgumentMatchers.anyList<AssignmentExample>())
}

private fun commandAssignment(
    id: String,
    courseId: String,
    status: AssignmentStatus,
    courseSlug: String = "back-basic",
    originAssignmentId: String? = null,
    originCourseSlug: String? = null,
    copyFingerprint: String? = null,
): Assignment {
    val now = Instant.now()
    val startAt = if (status == AssignmentStatus.PUBLISHED) now.minusSeconds(3600) else now.plusSeconds(3600)
    val endAt = startAt.plusSeconds(3600)
    return Assignment(
        id = id,
        courseId = courseId,
        courseSlug = courseSlug,
        createdBy = "admin",
        weekNo = 1,
        orderInWeek = 1,
        startAt = startAt,
        endAt = endAt,
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
        originAssignmentId = originAssignmentId,
        originCourseSlug = originCourseSlug,
        copyFingerprint = copyFingerprint,
    )
}

private fun queryCourse(id: String, slug: String, title: String, fieldTag: CourseTrack = CourseTrack.FL): Course = Course(
    id = id,
    slug = slug,
    fieldTag = fieldTag,
    startDate = LocalDate.of(2026, 3, 1),
    endDate = LocalDate.of(2026, 3, 30),
    metadata = CourseMetadata(
        title = title,
        description = null,
        phase = CoursePhase.BASIC,
        attributes = emptyMap(),
    ),
)

private fun reportUser(
    id: String,
    publicCode: String,
    role: String,
): ReportUser = ReportUser(
    id = id,
    publicCode = publicCode,
    username = id,
    role = role,
)
