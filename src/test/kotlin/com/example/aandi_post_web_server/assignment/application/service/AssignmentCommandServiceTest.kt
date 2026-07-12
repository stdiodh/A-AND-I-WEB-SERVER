package com.example.aandi_post_web_server.assignment.application.service

import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseValidator
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentMetadataPayload
import com.example.aandi_post_web_server.assignment.api.dto.CopyAssignmentRequest
import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentRequest
import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentRequirementRequest
import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentTestCaseRequest
import com.example.aandi_post_web_server.assignment.api.dto.UpdateAssignmentRequest
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseVisibility
import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.entity.AssignmentRequirement
import com.example.aandi_post_web_server.assignment.entity.AssignmentTestCase
import com.example.aandi_post_web_server.assignment.infrastructure.event.AssignmentReportTestCaseEvent
import com.example.aandi_post_web_server.assignment.infrastructure.event.AssignmentReportTestCaseEventMapper
import com.example.aandi_post_web_server.assignment.infrastructure.event.AssignmentReportTestCaseEventPublisher
import com.example.aandi_post_web_server.assignment.infrastructure.event.AssignmentReportTestCaseEventType
import com.example.aandi_post_web_server.assignment.infrastructure.event.DirectAssignmentProblemSyncAdapter
import com.example.aandi_post_web_server.assignment.infrastructure.adapter.RepositoryAssignmentDocumentCleanupAdapter
import com.example.aandi_post_web_server.assignment.infrastructure.adapter.RepositoryAssignmentCommandStore
import com.example.aandi_post_web_server.assignment.infrastructure.adapter.RepositoryAssignmentCommandContentStore
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentDeliveryRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentTestCaseRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRequirementRepository
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseMetadata
import com.example.aandi_post_web_server.course.entity.CourseWeek
import com.example.aandi_post_web_server.course.domain.model.CoursePhase
import com.example.aandi_post_web_server.course.domain.model.CourseTrack
import com.example.aandi_post_web_server.course.infrastructure.adapter.AssignmentCourseAdapter
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseWeekRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.Exceptions
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import reactor.test.publisher.TestPublisher
import java.time.Instant
import java.time.LocalDate

class AssignmentCommandServiceTest : StringSpec({
    "과제 삭제는 과제 연관 데이터를 하드 삭제한다" {
        val fixture = AssignmentCommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val assignment = commandAssignment(id = assignmentId, courseId = "course-1", status = AssignmentStatus.DRAFT)

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId(assignmentId, "course-1"))
            .thenReturn(Mono.just(assignment))
        Mockito.`when`(fixture.assignmentRequirementRepository.deleteAllByAssignmentIdIn(listOf(assignmentId))).thenReturn(Mono.just(1))
        Mockito.`when`(fixture.assignmentTestCaseRepository.deleteAllByAssignmentIdIn(listOf(assignmentId))).thenReturn(Mono.just(1))
        Mockito.`when`(fixture.assignmentDeliveryRepository.deleteAllByAssignmentIdIn(listOf(assignmentId))).thenReturn(Mono.just(0))
        Mockito.`when`(fixture.assignmentRepository.deleteById(assignmentId)).thenReturn(Mono.empty())

        StepVerifier.create(fixture.service.deleteAssignment("back-basic", assignmentId))
            .verifyComplete()

        Mockito.verify(fixture.assignmentRequirementRepository).deleteAllByAssignmentIdIn(listOf(assignmentId))
        Mockito.verify(fixture.assignmentTestCaseRepository).deleteAllByAssignmentIdIn(listOf(assignmentId))
        Mockito.verify(fixture.assignmentDeliveryRepository).deleteAllByAssignmentIdIn(listOf(assignmentId))
        Mockito.verify(fixture.assignmentRepository).deleteById(assignmentId)
        fixture.assignmentReportTestCaseEventPublisher.events.single().eventType shouldBe AssignmentReportTestCaseEventType.PROBLEM_DELETED
        fixture.assignmentReportTestCaseEventPublisher.events.single().problemId shouldBe assignmentId
        fixture.assignmentReportTestCaseEventPublisher.events.single().testCases shouldBe emptyList()
    }

    "과제 삭제 publisher 오류는 모든 연관 데이터 삭제가 끝난 뒤 전파된다" {
        var assignmentDeletesCompleted = 0
        val publisher = RecordingAssignmentCommandEventPublisher {
            Mono.error(IllegalStateException("publisher unavailable"))
        }
        publisher.beforePublish = { assignmentDeletesCompleted shouldBe 4 }
        val fixture = AssignmentCommandFixture(publisher)
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val assignment = commandAssignment(id = assignmentId, courseId = "course-1", status = AssignmentStatus.DRAFT)

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId(assignmentId, "course-1"))
            .thenReturn(Mono.just(assignment))
        Mockito.`when`(fixture.assignmentRequirementRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)))
            .thenReturn(Mono.just(1L).doOnSuccess { assignmentDeletesCompleted++ })
        Mockito.`when`(fixture.assignmentTestCaseRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)))
            .thenReturn(Mono.just(1L).doOnSuccess { assignmentDeletesCompleted++ })
        Mockito.`when`(fixture.assignmentDeliveryRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)))
            .thenReturn(Mono.just(1L).doOnSuccess { assignmentDeletesCompleted++ })
        Mockito.`when`(fixture.assignmentRepository.deleteById(assignmentId))
            .thenReturn(Mono.empty<Void>().doOnSuccess { assignmentDeletesCompleted++ })

        StepVerifier.create(fixture.service.deleteAssignment("back-basic", assignmentId))
            .expectErrorSatisfies { error ->
                error::class shouldBe IllegalStateException::class
                error.message shouldBe "publisher unavailable"
            }
            .verify()

        publisher.events.single().eventType shouldBe AssignmentReportTestCaseEventType.PROBLEM_DELETED
        publisher.events.single().problemId shouldBe assignmentId
    }

    "코스의 모든 과제 삭제는 자식 문서를 하드 삭제하고 과제별 삭제 이벤트를 발행한다" {
        val fixture = AssignmentCommandFixture()
        val assignments = listOf(
            commandAssignment(
                id = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111",
                courseId = "course-1",
                status = AssignmentStatus.PUBLISHED,
            ),
            commandAssignment(
                id = "7c53f1b3-0df8-4a9d-a56d-a5f50b96b7a1",
                courseId = "course-1",
                status = AssignmentStatus.DRAFT,
            ),
        )
        val assignmentIds = assignments.map { requireNotNull(it.id) }

        Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.fromIterable(assignments))
        Mockito.`when`(fixture.assignmentRequirementRepository.deleteAllByAssignmentIdIn(assignmentIds))
            .thenReturn(Mono.just(2L))
        Mockito.`when`(fixture.assignmentTestCaseRepository.deleteAllByAssignmentIdIn(assignmentIds))
            .thenReturn(Mono.just(2L))
        Mockito.`when`(fixture.assignmentDeliveryRepository.deleteAllByAssignmentIdIn(assignmentIds))
            .thenReturn(Mono.just(4L))
        Mockito.`when`(fixture.assignmentRepository.deleteAllById(assignmentIds)).thenReturn(Mono.empty())

        StepVerifier.create(fixture.service.deleteAllByCourseId("course-1"))
            .verifyComplete()

        Mockito.verify(fixture.assignmentRequirementRepository).deleteAllByAssignmentIdIn(assignmentIds)
        Mockito.verify(fixture.assignmentTestCaseRepository).deleteAllByAssignmentIdIn(assignmentIds)
        Mockito.verify(fixture.assignmentDeliveryRepository).deleteAllByAssignmentIdIn(assignmentIds)
        Mockito.verify(fixture.assignmentRepository).deleteAllById(assignmentIds)
        fixture.assignmentReportTestCaseEventPublisher.events.map { it.problemId } shouldBe assignmentIds
        fixture.assignmentReportTestCaseEventPublisher.events.forEach { event ->
            event.eventType shouldBe AssignmentReportTestCaseEventType.PROBLEM_DELETED
            event.testCases shouldBe emptyList()
        }
    }

    "코스의 과제 삭제 이벤트는 모든 저장소 삭제가 완료된 뒤 발행한다" {
        var repositoryDeletesCompleted = 0
        val publisher = RecordingAssignmentCommandEventPublisher()
        publisher.beforePublish = { repositoryDeletesCompleted shouldBe 4 }
        val fixture = AssignmentCommandFixture(publisher)
        val assignment = commandAssignment(
            id = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111",
            courseId = "course-1",
            status = AssignmentStatus.PUBLISHED,
        )
        val assignmentIds = listOf(requireNotNull(assignment.id))

        Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.just(assignment))
        Mockito.`when`(fixture.assignmentRequirementRepository.deleteAllByAssignmentIdIn(assignmentIds))
            .thenReturn(Mono.just(1L).doOnSuccess { repositoryDeletesCompleted++ })
        Mockito.`when`(fixture.assignmentTestCaseRepository.deleteAllByAssignmentIdIn(assignmentIds))
            .thenReturn(Mono.just(1L).doOnSuccess { repositoryDeletesCompleted++ })
        Mockito.`when`(fixture.assignmentDeliveryRepository.deleteAllByAssignmentIdIn(assignmentIds))
            .thenReturn(Mono.just(1L).doOnSuccess { repositoryDeletesCompleted++ })
        Mockito.`when`(fixture.assignmentRepository.deleteAllById(assignmentIds))
            .thenReturn(Mono.empty<Void>().doOnSuccess { repositoryDeletesCompleted++ })

        StepVerifier.create(fixture.service.deleteAllByCourseId("course-1"))
            .verifyComplete()

        repositoryDeletesCompleted shouldBe 4
        publisher.events.single().problemId shouldBe assignment.id
    }

    "코스의 과제 삭제 중 두 번째 publisher 오류는 후속 삭제 이벤트를 중단한다" {
        var publishAttempt = 0
        val publisher = RecordingAssignmentCommandEventPublisher {
            Mono.defer {
                publishAttempt++
                if (publishAttempt == 2) {
                    Mono.error(IllegalStateException("publisher unavailable"))
                } else {
                    Mono.empty<Void>()
                }
            }
        }
        val fixture = AssignmentCommandFixture(publisher)
        val assignments = listOf(
            commandAssignment(
                id = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111",
                courseId = "course-1",
                status = AssignmentStatus.PUBLISHED,
            ),
            commandAssignment(
                id = "7c53f1b3-0df8-4a9d-a56d-a5f50b96b7a1",
                courseId = "course-1",
                status = AssignmentStatus.DRAFT,
            ),
            commandAssignment(
                id = "f32d1a17-7059-4811-8d85-9773c7d21b53",
                courseId = "course-1",
                status = AssignmentStatus.PUBLISHED,
            ),
        )
        val assignmentIds = assignments.map { requireNotNull(it.id) }

        Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.fromIterable(assignments))
        Mockito.`when`(fixture.assignmentRequirementRepository.deleteAllByAssignmentIdIn(assignmentIds))
            .thenReturn(Mono.just(3L))
        Mockito.`when`(fixture.assignmentTestCaseRepository.deleteAllByAssignmentIdIn(assignmentIds))
            .thenReturn(Mono.just(3L))
        Mockito.`when`(fixture.assignmentDeliveryRepository.deleteAllByAssignmentIdIn(assignmentIds))
            .thenReturn(Mono.just(3L))
        Mockito.`when`(fixture.assignmentRepository.deleteAllById(assignmentIds)).thenReturn(Mono.empty())

        StepVerifier.create(fixture.service.deleteAllByCourseId("course-1"))
            .expectErrorSatisfies { error ->
                error::class shouldBe IllegalStateException::class
                error.message shouldBe "publisher unavailable"
            }
            .verify()

        publishAttempt shouldBe 2
        publisher.events.map { it.problemId } shouldBe assignmentIds.take(2)
    }

    "과제 삭제는 중간 삭제가 실패해도 나머지 저장소를 순서대로 삭제하고 이벤트를 발행하지 않는다" {
        val fixture = AssignmentCommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val assignment = commandAssignment(id = assignmentId, courseId = "course-1", status = AssignmentStatus.DRAFT)
        val requirementDeletePublisher = TestPublisher.create<Long>()
        val testCaseDeletePublisher = TestPublisher.create<Long>()
        val deliveryDeletePublisher = TestPublisher.create<Long>()
        val assignmentDeletePublisher = TestPublisher.create<Void>()
        val failure = IllegalStateException("testcase delete failed")

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId(assignmentId, "course-1"))
            .thenReturn(Mono.just(assignment))
        Mockito.`when`(fixture.assignmentRequirementRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)))
            .thenReturn(requirementDeletePublisher.mono())
        Mockito.`when`(fixture.assignmentTestCaseRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)))
            .thenReturn(testCaseDeletePublisher.mono())
        Mockito.`when`(fixture.assignmentDeliveryRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)))
            .thenReturn(deliveryDeletePublisher.mono())
        Mockito.`when`(fixture.assignmentRepository.deleteById(assignmentId))
            .thenReturn(assignmentDeletePublisher.mono())

        StepVerifier.create(fixture.service.deleteAssignment("back-basic", assignmentId))
            .then {
                requirementDeletePublisher.assertSubscribers(1)
                testCaseDeletePublisher.assertNoSubscribers()
                deliveryDeletePublisher.assertNoSubscribers()
                assignmentDeletePublisher.assertNoSubscribers()
            }
            .then { requirementDeletePublisher.emit(1L) }
            .then {
                testCaseDeletePublisher.assertSubscribers(1)
                deliveryDeletePublisher.assertNoSubscribers()
                assignmentDeletePublisher.assertNoSubscribers()
            }
            .then { testCaseDeletePublisher.error(failure) }
            .then {
                deliveryDeletePublisher.assertSubscribers(1)
                assignmentDeletePublisher.assertNoSubscribers()
            }
            .then { deliveryDeletePublisher.emit(1L) }
            .then { assignmentDeletePublisher.assertSubscribers(1) }
            .then { assignmentDeletePublisher.complete() }
            .expectErrorSatisfies { error -> error shouldBe failure }
            .verify()

        fixture.assignmentReportTestCaseEventPublisher.events shouldBe emptyList()
    }

    "코스 과제 삭제는 복수 실패를 모아 전파하면서 모든 저장소를 순서대로 시도한다" {
        val fixture = AssignmentCommandFixture()
        val assignment = commandAssignment(
            id = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111",
            courseId = "course-1",
            status = AssignmentStatus.PUBLISHED,
        )
        val assignmentIds = listOf(requireNotNull(assignment.id))
        val requirementDeletePublisher = TestPublisher.create<Long>()
        val testCaseDeletePublisher = TestPublisher.create<Long>()
        val deliveryDeletePublisher = TestPublisher.create<Long>()
        val assignmentDeletePublisher = TestPublisher.create<Void>()
        val requirementFailure = IllegalStateException("requirement delete failed")
        val deliveryFailure = IllegalArgumentException("delivery delete failed")

        Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.just(assignment))
        Mockito.`when`(fixture.assignmentRequirementRepository.deleteAllByAssignmentIdIn(assignmentIds))
            .thenReturn(requirementDeletePublisher.mono())
        Mockito.`when`(fixture.assignmentTestCaseRepository.deleteAllByAssignmentIdIn(assignmentIds))
            .thenReturn(testCaseDeletePublisher.mono())
        Mockito.`when`(fixture.assignmentDeliveryRepository.deleteAllByAssignmentIdIn(assignmentIds))
            .thenReturn(deliveryDeletePublisher.mono())
        Mockito.`when`(fixture.assignmentRepository.deleteAllById(assignmentIds))
            .thenReturn(assignmentDeletePublisher.mono())

        StepVerifier.create(fixture.service.deleteAllByCourseId("course-1"))
            .then {
                requirementDeletePublisher.assertSubscribers(1)
                testCaseDeletePublisher.assertNoSubscribers()
                deliveryDeletePublisher.assertNoSubscribers()
                assignmentDeletePublisher.assertNoSubscribers()
            }
            .then { requirementDeletePublisher.error(requirementFailure) }
            .then {
                testCaseDeletePublisher.assertSubscribers(1)
                deliveryDeletePublisher.assertNoSubscribers()
                assignmentDeletePublisher.assertNoSubscribers()
            }
            .then { testCaseDeletePublisher.emit(1L) }
            .then {
                deliveryDeletePublisher.assertSubscribers(1)
                assignmentDeletePublisher.assertNoSubscribers()
            }
            .then { deliveryDeletePublisher.error(deliveryFailure) }
            .then { assignmentDeletePublisher.assertSubscribers(1) }
            .then { assignmentDeletePublisher.complete() }
            .expectErrorSatisfies { error ->
                val failures = Exceptions.unwrapMultipleExcludingTracebacks(error)
                failures shouldHaveSize 2
                failures.contains(requirementFailure) shouldBe true
                failures.contains(deliveryFailure) shouldBe true
            }
            .verify()

        fixture.assignmentReportTestCaseEventPublisher.events shouldBe emptyList()
    }

    "과제 복사는 요청을 복사 서비스에 그대로 위임한다" {
        val fixture = AssignmentCommandFixture()
        val targetCourseSlug = "front-advanced"
        val createdBy = "admin"
        val request = CopyAssignmentRequest(
            sourceAssignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111",
            targetWeekNo = 3,
            targetOrderInWeek = 2,
            targetStartAt = Instant.parse("2026-05-12T00:00:00Z"),
            targetEndAt = Instant.parse("2026-05-19T00:00:00Z"),
        )
        val expectedResponse = Mockito.mock(AssignmentDetailResponse::class.java)

        Mockito.`when`(fixture.assignmentCopyService.copyAssignment(targetCourseSlug, request, createdBy))
            .thenReturn(Mono.just(expectedResponse))

        StepVerifier.create(fixture.service.copyAssignment(targetCourseSlug, request, createdBy))
            .expectNext(expectedResponse)
            .verifyComplete()

        Mockito.verify(fixture.assignmentCopyService)
            .copyAssignment(targetCourseSlug, request, createdBy)
    }

    "과제 수정은 전달된 필드를 반영해 저장한다" {
        val fixture = AssignmentCommandFixture()
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
        Mockito.`when`(fixture.assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(assignmentId))
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

    "draft 과제를 즉시 공개하면 publishedAt 을 저장하고 응답과 이벤트 problemId 를 채운다" {
        val fixture = AssignmentCommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val startAt = Instant.now().minusSeconds(3600)
        val target = commandAssignment(id = assignmentId, courseId = "course-1", status = AssignmentStatus.DRAFT)
            .copy(startAt = startAt, endAt = startAt.plusSeconds(7200), publishedAt = null)
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
        Mockito.`when`(fixture.assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(assignmentId))
            .thenReturn(Flux.empty())

        StepVerifier.create(
            fixture.service.updateAssignment(
                courseSlug = "back-basic",
                assignmentId = assignmentId,
                request = UpdateAssignmentRequest(orderInWeek = 1),
            )
        )
            .assertNext { updated ->
                updated.status shouldBe AssignmentStatus.PUBLISHED
                (updated.publishedAt == null) shouldBe false
                updated.title shouldBe "테스트 과제"
                updated.problemId shouldBe assignmentId
            }
            .verifyComplete()

        (persistedAssignment.publishedAt == null) shouldBe false
        persistedAssignment.publishedAt!!.isBefore(startAt) shouldBe false
        fixture.assignmentReportTestCaseEventPublisher.events.single().problemId shouldBe assignmentId
    }

    "과제 수정 슬롯 중복은 새 주차를 생성하지 않고 CONFLICT 를 반환한다" {
        val fixture = AssignmentCommandFixture()
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

    "과제 수정 주차 생성 race 실패는 슬롯 CONFLICT 로 바꾸지 않는다" {
        val fixture = AssignmentCommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val target = commandAssignment(id = assignmentId, courseId = "course-1", status = AssignmentStatus.DRAFT)
        val failure = DuplicateKeyException("course week race")

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId(assignmentId, "course-1"))
            .thenReturn(Mono.just(target))
        Mockito.`when`(
            fixture.assignmentRepository.findByCourseIdAndWeekNoAndOrderInWeek("course-1", 2, target.orderInWeek)
        ).thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseWeekRepository.findByCourseIdAndWeekNo("course-1", 2))
            .thenReturn(Mono.empty(), Mono.empty())
        Mockito.`when`(fixture.courseWeekRepository.save(ArgumentMatchers.any(CourseWeek::class.java)))
            .thenReturn(Mono.error(failure))

        StepVerifier.create(
            fixture.service.updateAssignment(
                courseSlug = "back-basic",
                assignmentId = assignmentId,
                request = UpdateAssignmentRequest(weekNo = 2),
            )
        )
            .expectErrorSatisfies { error -> (error === failure) shouldBe true }
            .verify()

        Mockito.verify(fixture.courseWeekRepository, Mockito.times(2))
            .findByCourseIdAndWeekNo("course-1", 2)
        Mockito.verify(fixture.assignmentRepository, Mockito.never())
            .save(ArgumentMatchers.any(Assignment::class.java))
        fixture.assignmentReportTestCaseEventPublisher.events shouldBe emptyList()
    }

    "과제 수정 저장 DuplicateKey 는 슬롯 CONFLICT 로 바꾸고 하위 작업을 시작하지 않는다" {
        val fixture = AssignmentCommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val target = commandAssignment(id = assignmentId, courseId = "course-1", status = AssignmentStatus.DRAFT)

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId(assignmentId, "course-1"))
            .thenReturn(Mono.just(target))
        Mockito.`when`(
            fixture.assignmentRepository.findByCourseIdAndWeekNoAndOrderInWeek(
                "course-1",
                target.weekNo,
                target.orderInWeek + 1,
            )
        ).thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseWeekRepository.findByCourseIdAndWeekNo("course-1", target.weekNo))
            .thenReturn(Mono.just(CourseWeek(id = "week-1", courseId = "course-1", weekNo = 1, title = "1주차")))
        Mockito.`when`(fixture.assignmentRepository.save(ArgumentMatchers.any(Assignment::class.java)))
            .thenReturn(Mono.error(DuplicateKeyException("assignment slot race")))

        StepVerifier.create(
            fixture.service.updateAssignment(
                courseSlug = "back-basic",
                assignmentId = assignmentId,
                request = UpdateAssignmentRequest(orderInWeek = target.orderInWeek + 1),
            )
        )
            .expectErrorSatisfies(::assertDuplicateAssignmentSlotConflict)
            .verify()

        Mockito.verifyNoInteractions(fixture.assignmentRequirementRepository, fixture.assignmentTestCaseRepository)
        fixture.assignmentReportTestCaseEventPublisher.events shouldBe emptyList()
    }

    "주차가 없으면 과제 생성 시 주차를 자동 생성한다" {
        val fixture = AssignmentCommandFixture()
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
                    CreateAssignmentTestCaseRequest(
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
            val saved = invocation.arguments[0] as List<AssignmentTestCase>
            Flux.fromIterable(saved)
        }.`when`(fixture.assignmentTestCaseRepository)
            .saveAll(ArgumentMatchers.anyList<AssignmentTestCase>())
        Mockito.`when`(fixture.assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(ArgumentMatchers.anyString()))
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
        val fixture = AssignmentCommandFixture()
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
                    CreateAssignmentTestCaseRequest(
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

    "과제 생성 주차 생성 race 실패는 슬롯 CONFLICT 로 바꾸지 않는다" {
        val fixture = AssignmentCommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val failure = DuplicateKeyException("course week race")

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(
            fixture.assignmentRepository.findByCourseIdAndWeekNoAndOrderInWeek("course-1", 1, 1)
        ).thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseWeekRepository.findByCourseIdAndWeekNo("course-1", 1))
            .thenReturn(Mono.empty(), Mono.empty())
        Mockito.`when`(fixture.courseWeekRepository.save(ArgumentMatchers.any(CourseWeek::class.java)))
            .thenReturn(Mono.error(failure))

        StepVerifier.create(
            fixture.service.createAssignment("back-basic", sequentialChildWriteCreateRequest(), "admin")
        )
            .expectErrorSatisfies { error -> (error === failure) shouldBe true }
            .verify()

        Mockito.verify(fixture.courseWeekRepository, Mockito.times(2))
            .findByCourseIdAndWeekNo("course-1", 1)
        Mockito.verify(fixture.assignmentRepository, Mockito.never())
            .save(ArgumentMatchers.any(Assignment::class.java))
        fixture.assignmentReportTestCaseEventPublisher.events shouldBe emptyList()
    }

    "과제 생성 저장 DuplicateKey 는 슬롯 CONFLICT 로 바꾸고 하위 작업을 시작하지 않는다" {
        val fixture = AssignmentCommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(
            fixture.assignmentRepository.findByCourseIdAndWeekNoAndOrderInWeek("course-1", 1, 1)
        ).thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseWeekRepository.findByCourseIdAndWeekNo("course-1", 1))
            .thenReturn(Mono.just(CourseWeek(id = "week-1", courseId = "course-1", weekNo = 1, title = "1주차")))
        Mockito.`when`(fixture.assignmentRepository.save(ArgumentMatchers.any(Assignment::class.java)))
            .thenReturn(Mono.error(DuplicateKeyException("assignment slot race")))

        StepVerifier.create(fixture.service.createAssignment("back-basic", sequentialChildWriteCreateRequest(), "admin"))
            .expectErrorSatisfies(::assertDuplicateAssignmentSlotConflict)
            .verify()

        Mockito.verifyNoInteractions(fixture.assignmentRequirementRepository, fixture.assignmentTestCaseRepository)
        fixture.assignmentReportTestCaseEventPublisher.events shouldBe emptyList()
    }

    "게시 상태로 생성된 과제는 EXCLUDED 를 제외한 케이스만 PROBLEM_CREATED 로 발행한다" {
        val fixture = AssignmentCommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val startAt = Instant.now().minusSeconds(3600)
        val endAt = Instant.now().plusSeconds(3600)
        var persistedAssignment: Assignment? = null
        var persistedTestCases: List<AssignmentTestCase> = emptyList()
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
                    CreateAssignmentTestCaseRequest(
                        seq = 1,
                        inputValues = listOf("ADD 1", "CLOSE"),
                        outputText = "3",
                        visibility = AssignmentTestCaseVisibility.PUBLIC,
                    ),
                    CreateAssignmentTestCaseRequest(
                        seq = 2,
                        inputValues = listOf("2 3"),
                        outputText = "5",
                        visibility = AssignmentTestCaseVisibility.HIDDEN,
                    ),
                    CreateAssignmentTestCaseRequest(
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
            val saved = invocation.arguments[0] as List<AssignmentTestCase>
            persistedTestCases = saved
            Flux.fromIterable(saved)
        }.`when`(fixture.assignmentTestCaseRepository)
            .saveAll(ArgumentMatchers.anyList<AssignmentTestCase>())
        Mockito.`when`(fixture.assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(ArgumentMatchers.anyString()))
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
        val fixture = AssignmentCommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val startAt = Instant.now().plusSeconds(3600)
        val endAt = Instant.now().plusSeconds(7200)
        var persistedAssignment: Assignment? = null
        var persistedTestCases: List<AssignmentTestCase> = emptyList()
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
                    CreateAssignmentTestCaseRequest(
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
            val saved = invocation.arguments[0] as List<AssignmentTestCase>
            persistedTestCases = saved
            Flux.fromIterable(saved)
        }.`when`(fixture.assignmentTestCaseRepository)
            .saveAll(ArgumentMatchers.anyList<AssignmentTestCase>())
        Mockito.`when`(fixture.assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(ArgumentMatchers.anyString()))
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

    "EXCLUDED 만 있는 testCases 로 과제를 생성할 수 없다" {
        val fixture = AssignmentCommandFixture()
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
                    CreateAssignmentTestCaseRequest(
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
        val fixture = AssignmentCommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val target = commandAssignment(id = assignmentId, courseId = "course-1", status = AssignmentStatus.PUBLISHED)
        var persistedAssignment: Assignment = target
        var persistedTestCases: List<AssignmentTestCase> = emptyList()
        val updateRequest = UpdateAssignmentRequest(
            metadata = AssignmentMetadataPayload(
                title = "updated title",
                difficulty = AssignmentDifficulty.LOW,
                description = "updated description",
                testCases = listOf(
                    CreateAssignmentTestCaseRequest(
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
        Mockito.`when`(fixture.assignmentTestCaseRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)))
            .thenReturn(Mono.just(2))
        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val saved = invocation.arguments[0] as List<AssignmentTestCase>
            persistedTestCases = saved
            Flux.fromIterable(saved)
        }.`when`(fixture.assignmentTestCaseRepository)
            .saveAll(ArgumentMatchers.anyList<AssignmentTestCase>())
        Mockito.`when`(fixture.assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(assignmentId))
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
        val fixture = AssignmentCommandFixture()
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
        Mockito.`when`(fixture.assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(assignmentId))
            .thenReturn(
                Flux.just(
                    AssignmentTestCase(
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
        val fixture = AssignmentCommandFixture()
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
        Mockito.`when`(fixture.assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(assignmentId))
            .thenReturn(
                Flux.just(
                    AssignmentTestCase(
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

        Mockito.verify(fixture.assignmentTestCaseRepository, Mockito.never())
            .deleteAllByAssignmentIdIn(listOf(assignmentId))
        fixture.assignmentReportTestCaseEventPublisher.events.single().testCases shouldHaveSize 1
    }

    "draft 과제 수정도 OJ test case 이벤트를 PROBLEM_UPDATED 로 재발행한다" {
        val fixture = AssignmentCommandFixture()
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
        Mockito.`when`(fixture.assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(assignmentId))
            .thenReturn(
                Flux.just(
                    AssignmentTestCase(
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
        val fixture = AssignmentCommandFixture()
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val updateRequest = UpdateAssignmentRequest(
            metadata = AssignmentMetadataPayload(
                title = "updated title",
                difficulty = AssignmentDifficulty.LOW,
                description = "updated description",
                testCases = listOf(
                    CreateAssignmentTestCaseRequest(
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

    "과제 생성은 요구사항 저장 완료 후 테스트케이스 저장을 구독한다" {
        val fixture = AssignmentCommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val requirementPublisher = TestPublisher.create<AssignmentRequirement>()
        val testCasePublisher = TestPublisher.create<AssignmentTestCase>()
        var persistedAssignment: Assignment? = null
        var pendingRequirement: AssignmentRequirement? = null
        var pendingTestCase: AssignmentTestCase? = null

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(
            fixture.assignmentRepository.findByCourseIdAndWeekNoAndOrderInWeek(
                "course-1",
                1,
                1,
            )
        ).thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseWeekRepository.findByCourseIdAndWeekNo("course-1", 1))
            .thenReturn(Mono.just(CourseWeek(id = "week-1", courseId = "course-1", weekNo = 1, title = "1주차")))
        Mockito.`when`(fixture.assignmentRepository.save(ArgumentMatchers.any(Assignment::class.java)))
            .thenAnswer { invocation ->
                val assignment = invocation.arguments[0] as Assignment
                persistedAssignment = assignment
                Mono.just(assignment)
            }
        Mockito.`when`(fixture.assignmentRequirementRepository.saveAll(ArgumentMatchers.anyList<AssignmentRequirement>()))
            .thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val requirements = invocation.arguments[0] as List<AssignmentRequirement>
                pendingRequirement = requirements.single()
                requirementPublisher.flux()
            }
        Mockito.`when`(fixture.assignmentTestCaseRepository.saveAll(ArgumentMatchers.anyList<AssignmentTestCase>()))
            .thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val testCases = invocation.arguments[0] as List<AssignmentTestCase>
                pendingTestCase = testCases.single()
                testCasePublisher.flux()
            }
        Mockito.`when`(fixture.assignmentRepository.findById(ArgumentMatchers.anyString()))
            .thenAnswer { Mono.just(requireNotNull(persistedAssignment)) }
        Mockito.`when`(fixture.assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(ArgumentMatchers.anyString()))
            .thenAnswer {
                pendingTestCase?.let { Flux.just(it) } ?: Flux.empty<AssignmentTestCase>()
            }

        StepVerifier.create(
            fixture.service.createAssignment(
                courseSlug = "back-basic",
                request = sequentialChildWriteCreateRequest(),
                createdBy = "admin",
            )
        )
            .then {
                requirementPublisher.assertSubscribers(1)
                testCasePublisher.assertNoSubscribers()
            }
            .then { requirementPublisher.emit(requireNotNull(pendingRequirement)) }
            .then { testCasePublisher.assertSubscribers(1) }
            .then { testCasePublisher.emit(requireNotNull(pendingTestCase)) }
            .assertNext { response ->
                response.metadata.requirements.single().requirementText shouldBe "함수 분리 필수"
                response.metadata.testCases.single().outputText shouldBe "3"
            }
            .verifyComplete()

        fixture.assignmentReportTestCaseEventPublisher.events shouldHaveSize 1
    }

    "과제 생성 요구사항 저장 실패는 테스트케이스 저장과 problem event 발행을 시작하지 않는다" {
        val fixture = AssignmentCommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val requirementPublisher = TestPublisher.create<AssignmentRequirement>()
        val testCasePublisher = TestPublisher.create<AssignmentTestCase>()
        val failure = IllegalStateException("requirement save failed")

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(
            fixture.assignmentRepository.findByCourseIdAndWeekNoAndOrderInWeek(
                "course-1",
                1,
                1,
            )
        ).thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseWeekRepository.findByCourseIdAndWeekNo("course-1", 1))
            .thenReturn(Mono.just(CourseWeek(id = "week-1", courseId = "course-1", weekNo = 1, title = "1주차")))
        Mockito.`when`(fixture.assignmentRepository.save(ArgumentMatchers.any(Assignment::class.java)))
            .thenAnswer { invocation -> Mono.just(invocation.arguments[0] as Assignment) }
        Mockito.`when`(fixture.assignmentRequirementRepository.saveAll(ArgumentMatchers.anyList<AssignmentRequirement>()))
            .thenReturn(requirementPublisher.flux())
        Mockito.`when`(fixture.assignmentTestCaseRepository.saveAll(ArgumentMatchers.anyList<AssignmentTestCase>()))
            .thenReturn(testCasePublisher.flux())

        StepVerifier.create(
            fixture.service.createAssignment(
                courseSlug = "back-basic",
                request = sequentialChildWriteCreateRequest(),
                createdBy = "admin",
            )
        )
            .then {
                requirementPublisher.assertSubscribers(1)
                testCasePublisher.assertNoSubscribers()
                requirementPublisher.error(failure)
            }
            .expectErrorSatisfies { error -> error shouldBe failure }
            .verify()

        testCasePublisher.assertNoSubscribers()
        fixture.assignmentReportTestCaseEventPublisher.events shouldBe emptyList()
    }

    "과제 수정은 요구사항 교체 완료 후 테스트케이스 교체를 구독한다" {
        val fixture = AssignmentCommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val target = commandAssignment(id = assignmentId, courseId = "course-1", status = AssignmentStatus.DRAFT)
        val requirementDeletePublisher = TestPublisher.create<Long>()
        val requirementSavePublisher = TestPublisher.create<AssignmentRequirement>()
        val testCaseDeletePublisher = TestPublisher.create<Long>()
        val testCaseSavePublisher = TestPublisher.create<AssignmentTestCase>()
        var persistedAssignment: Assignment = target
        var pendingRequirement: AssignmentRequirement? = null
        var pendingTestCase: AssignmentTestCase? = null

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId(assignmentId, "course-1"))
            .thenReturn(Mono.just(target))
        Mockito.`when`(
            fixture.assignmentRepository.findByCourseIdAndWeekNoAndOrderInWeek(
                "course-1",
                target.weekNo,
                target.orderInWeek,
            )
        ).thenReturn(Mono.just(target))
        Mockito.`when`(fixture.courseWeekRepository.findByCourseIdAndWeekNo("course-1", target.weekNo))
            .thenReturn(
                Mono.just(
                    CourseWeek(
                        id = "week-${target.weekNo}",
                        courseId = "course-1",
                        weekNo = target.weekNo,
                        title = "${target.weekNo}주차",
                    )
                )
            )
        Mockito.`when`(fixture.assignmentRepository.save(ArgumentMatchers.any(Assignment::class.java)))
            .thenAnswer { invocation ->
                val assignment = invocation.arguments[0] as Assignment
                persistedAssignment = assignment
                Mono.just(assignment)
            }
        Mockito.`when`(fixture.assignmentRepository.findById(assignmentId))
            .thenAnswer { Mono.just(persistedAssignment) }
        Mockito.`when`(fixture.assignmentRequirementRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)))
            .thenReturn(requirementDeletePublisher.mono())
        Mockito.`when`(fixture.assignmentRequirementRepository.saveAll(ArgumentMatchers.anyList<AssignmentRequirement>()))
            .thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val requirements = invocation.arguments[0] as List<AssignmentRequirement>
                pendingRequirement = requirements.single()
                requirementSavePublisher.flux()
            }
        Mockito.`when`(fixture.assignmentTestCaseRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)))
            .thenReturn(testCaseDeletePublisher.mono())
        Mockito.`when`(fixture.assignmentTestCaseRepository.saveAll(ArgumentMatchers.anyList<AssignmentTestCase>()))
            .thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val testCases = invocation.arguments[0] as List<AssignmentTestCase>
                pendingTestCase = testCases.single()
                testCaseSavePublisher.flux()
            }
        Mockito.`when`(fixture.assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(assignmentId))
            .thenAnswer {
                pendingTestCase?.let { Flux.just(it) } ?: Flux.empty<AssignmentTestCase>()
            }

        StepVerifier.create(
            fixture.service.updateAssignment(
                courseSlug = "back-basic",
                assignmentId = assignmentId,
                request = sequentialChildWriteUpdateRequest(),
            )
        )
            .then {
                requirementDeletePublisher.assertSubscribers(1)
                requirementSavePublisher.assertNoSubscribers()
                testCaseDeletePublisher.assertNoSubscribers()
                testCaseSavePublisher.assertNoSubscribers()
            }
            .then { requirementDeletePublisher.emit(1L) }
            .then {
                requirementSavePublisher.assertSubscribers(1)
                testCaseDeletePublisher.assertNoSubscribers()
                testCaseSavePublisher.assertNoSubscribers()
            }
            .then { requirementSavePublisher.emit(requireNotNull(pendingRequirement)) }
            .then {
                testCaseDeletePublisher.assertSubscribers(1)
                testCaseSavePublisher.assertNoSubscribers()
            }
            .then { testCaseDeletePublisher.emit(1L) }
            .then { testCaseSavePublisher.assertSubscribers(1) }
            .then { testCaseSavePublisher.emit(requireNotNull(pendingTestCase)) }
            .assertNext { response ->
                response.metadata.requirements.single().requirementText shouldBe "함수 분리 필수"
                response.metadata.testCases.single().outputText shouldBe "3"
            }
            .verifyComplete()

        fixture.assignmentReportTestCaseEventPublisher.events shouldHaveSize 1
    }

    "과제 수정 요구사항 저장 실패는 테스트케이스 교체와 problem event 발행을 시작하지 않는다" {
        val fixture = AssignmentCommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val target = commandAssignment(id = assignmentId, courseId = "course-1", status = AssignmentStatus.DRAFT)
        val requirementDeletePublisher = TestPublisher.create<Long>()
        val requirementSavePublisher = TestPublisher.create<AssignmentRequirement>()
        val testCaseDeletePublisher = TestPublisher.create<Long>()
        val testCaseSavePublisher = TestPublisher.create<AssignmentTestCase>()
        val failure = IllegalStateException("requirement save failed")

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId(assignmentId, "course-1"))
            .thenReturn(Mono.just(target))
        Mockito.`when`(
            fixture.assignmentRepository.findByCourseIdAndWeekNoAndOrderInWeek(
                "course-1",
                target.weekNo,
                target.orderInWeek,
            )
        ).thenReturn(Mono.just(target))
        Mockito.`when`(fixture.courseWeekRepository.findByCourseIdAndWeekNo("course-1", target.weekNo))
            .thenReturn(
                Mono.just(
                    CourseWeek(
                        id = "week-${target.weekNo}",
                        courseId = "course-1",
                        weekNo = target.weekNo,
                        title = "${target.weekNo}주차",
                    )
                )
            )
        Mockito.`when`(fixture.assignmentRepository.save(ArgumentMatchers.any(Assignment::class.java)))
            .thenAnswer { invocation -> Mono.just(invocation.arguments[0] as Assignment) }
        Mockito.`when`(fixture.assignmentRequirementRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)))
            .thenReturn(requirementDeletePublisher.mono())
        Mockito.`when`(fixture.assignmentRequirementRepository.saveAll(ArgumentMatchers.anyList<AssignmentRequirement>()))
            .thenReturn(requirementSavePublisher.flux())
        Mockito.`when`(fixture.assignmentTestCaseRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)))
            .thenReturn(testCaseDeletePublisher.mono())
        Mockito.`when`(fixture.assignmentTestCaseRepository.saveAll(ArgumentMatchers.anyList<AssignmentTestCase>()))
            .thenReturn(testCaseSavePublisher.flux())

        StepVerifier.create(
            fixture.service.updateAssignment(
                courseSlug = "back-basic",
                assignmentId = assignmentId,
                request = sequentialChildWriteUpdateRequest(),
            )
        )
            .then {
                requirementDeletePublisher.assertSubscribers(1)
                requirementSavePublisher.assertNoSubscribers()
                testCaseDeletePublisher.assertNoSubscribers()
                testCaseSavePublisher.assertNoSubscribers()
            }
            .then { requirementDeletePublisher.emit(1L) }
            .then {
                requirementSavePublisher.assertSubscribers(1)
                testCaseDeletePublisher.assertNoSubscribers()
                testCaseSavePublisher.assertNoSubscribers()
                requirementSavePublisher.error(failure)
            }
            .expectErrorSatisfies { error -> error shouldBe failure }
            .verify()

        testCaseDeletePublisher.assertNoSubscribers()
        testCaseSavePublisher.assertNoSubscribers()
        fixture.assignmentReportTestCaseEventPublisher.events shouldBe emptyList()
    }

})

private fun assertDuplicateAssignmentSlotConflict(error: Throwable) {
    (error as ResponseStatusException).statusCode shouldBe HttpStatus.CONFLICT
    error.reason shouldBe "동일 코스/주차/순번 과제가 이미 존재합니다."
}

private fun sequentialChildWriteCreateRequest(): CreateAssignmentRequest {
    val startAt = Instant.now().plusSeconds(3600)
    return CreateAssignmentRequest(
        weekNo = 1,
        orderInWeek = 1,
        startAt = startAt,
        endAt = startAt.plusSeconds(3600),
        metadata = sequentialChildWriteMetadata(),
    )
}

private fun sequentialChildWriteUpdateRequest(): UpdateAssignmentRequest =
    UpdateAssignmentRequest(metadata = sequentialChildWriteMetadata())

private fun sequentialChildWriteMetadata(): AssignmentMetadataPayload =
    AssignmentMetadataPayload(
        title = "순차 저장 과제",
        difficulty = AssignmentDifficulty.MID,
        description = "순차 저장 검증",
        requirements = listOf(
            CreateAssignmentRequirementRequest(
                sortOrder = 1,
                requirementText = "함수 분리 필수",
            )
        ),
        testCases = listOf(
            CreateAssignmentTestCaseRequest(
                seq = 1,
                inputValues = listOf("1 2"),
                outputText = "3",
            )
        ),
    )

private class AssignmentCommandFixture(
    val assignmentReportTestCaseEventPublisher: RecordingAssignmentCommandEventPublisher =
        RecordingAssignmentCommandEventPublisher(),
) {
    val courseRepository: CourseRepository = Mockito.mock(CourseRepository::class.java)
    val courseWeekRepository: CourseWeekRepository = Mockito.mock(CourseWeekRepository::class.java)
    val assignmentRepository: AssignmentRepository = Mockito.mock(AssignmentRepository::class.java)
    val assignmentRequirementRepository: AssignmentRequirementRepository = Mockito.mock(AssignmentRequirementRepository::class.java)
    val assignmentTestCaseRepository: AssignmentTestCaseRepository = Mockito.mock(AssignmentTestCaseRepository::class.java)
    val assignmentDeliveryRepository: AssignmentDeliveryRepository = Mockito.mock(AssignmentDeliveryRepository::class.java)
    val assignmentReportTestCaseEventMapper = AssignmentReportTestCaseEventMapper()
    val assignmentTestCaseValidator = AssignmentTestCaseValidator()
    val assignmentCommandRequestResolver = AssignmentCommandRequestResolver(assignmentTestCaseValidator)
    val assignmentCoursePort = AssignmentCourseAdapter(courseRepository, courseWeekRepository)
    val assignmentProblemSyncPort = DirectAssignmentProblemSyncAdapter(
        assignmentRepository = assignmentRepository,
        assignmentTestCaseRepository = assignmentTestCaseRepository,
        eventMapper = assignmentReportTestCaseEventMapper,
        eventPublisher = assignmentReportTestCaseEventPublisher,
    )
    val assignmentCopyService: AssignmentCopyService = Mockito.mock(AssignmentCopyService::class.java)
    val service = AssignmentCommandService(
        assignmentCoursePort = assignmentCoursePort,
        assignmentCommandStore = RepositoryAssignmentCommandStore(assignmentRepository),
        assignmentCommandContentStore = RepositoryAssignmentCommandContentStore(
            assignmentRequirementRepository,
            assignmentTestCaseRepository,
        ),
        assignmentDocumentCleanupPort = RepositoryAssignmentDocumentCleanupAdapter(
            assignmentRepository,
            assignmentRequirementRepository,
            assignmentTestCaseRepository,
            assignmentDeliveryRepository,
        ),
        assignmentProblemSyncPort = assignmentProblemSyncPort,
        assignmentCopyService = assignmentCopyService,
        assignmentCommandRequestResolver = assignmentCommandRequestResolver,
    )
}

private class RecordingAssignmentCommandEventPublisher(
    private val publishResult: (AssignmentReportTestCaseEvent) -> Mono<Void> = { Mono.empty() },
) : AssignmentReportTestCaseEventPublisher {
    val events = mutableListOf<AssignmentReportTestCaseEvent>()
    var beforePublish: (AssignmentReportTestCaseEvent) -> Unit = {}

    override fun publish(event: AssignmentReportTestCaseEvent): Mono<Void> {
        beforePublish(event)
        events += event
        return publishResult(event)
    }
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
