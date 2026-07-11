package com.example.aandi_post_web_server.assignment.application.service

import com.example.aandi_post_web_server.assignment.api.dto.CopyAssignmentRequest
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTemplateLanguage
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseVisibility
import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.entity.AssignmentCodeTemplate
import com.example.aandi_post_web_server.assignment.entity.AssignmentMetadata
import com.example.aandi_post_web_server.assignment.entity.AssignmentRequirement
import com.example.aandi_post_web_server.assignment.entity.AssignmentTestCase
import com.example.aandi_post_web_server.assignment.infrastructure.event.AssignmentReportTestCaseEvent
import com.example.aandi_post_web_server.assignment.infrastructure.event.AssignmentReportTestCaseEventMapper
import com.example.aandi_post_web_server.assignment.infrastructure.event.AssignmentReportTestCaseEventPublisher
import com.example.aandi_post_web_server.assignment.infrastructure.event.AssignmentReportTestCaseEventType
import com.example.aandi_post_web_server.assignment.infrastructure.event.DirectAssignmentProblemSyncAdapter
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentDeliveryRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRequirementRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentTestCaseRepository
import com.example.aandi_post_web_server.course.domain.model.CoursePhase
import com.example.aandi_post_web_server.course.domain.model.CourseStatus
import com.example.aandi_post_web_server.course.domain.model.CourseTrack
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseMetadata
import com.example.aandi_post_web_server.course.entity.CourseWeek
import com.example.aandi_post_web_server.course.infrastructure.adapter.AssignmentCourseAdapter
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseWeekRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import reactor.test.publisher.TestPublisher
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class AssignmentCopyServiceTest : StringSpec({
    "blank sourceAssignmentId returns BAD_REQUEST without repository calls" {
        val fixture = AssignmentCopyFixture()

        StepVerifier.create(
            fixture.service.copyAssignment(
                targetCourseSlug = TARGET_COURSE_SLUG,
                request = CopyAssignmentRequest(sourceAssignmentId = " "),
                createdBy = CREATED_BY,
            )
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.BAD_REQUEST
                error.reason shouldBe "sourceAssignmentId는 필수입니다."
            }
            .verify()

        fixture.verifyNoRepositoryInteractions()
    }

    "invalid UUID sourceAssignmentId returns BAD_REQUEST without writes" {
        val fixture = AssignmentCopyFixture()

        val error = shouldThrow<ResponseStatusException> {
            fixture.service.copyAssignment(
                targetCourseSlug = TARGET_COURSE_SLUG,
                request = CopyAssignmentRequest(sourceAssignmentId = "not-a-uuid"),
                createdBy = CREATED_BY,
            )
        }

        error.statusCode shouldBe HttpStatus.BAD_REQUEST
        error.reason shouldBe "sourceAssignmentId는 UUID 형식이어야 합니다."
        fixture.verifyNoWrites()
    }

    "invalid targetWeekNo values return BAD_REQUEST before saving the copied assignment" {
        listOf(0, -1).forEach { invalidWeekNo ->
            val fixture = AssignmentCopyFixture()
            fixture.stubCopyPath()

            StepVerifier.create(
                fixture.service.copyAssignment(
                    targetCourseSlug = TARGET_COURSE_SLUG,
                    request = CopyAssignmentRequest(
                        sourceAssignmentId = SOURCE_ASSIGNMENT_ID,
                        targetWeekNo = invalidWeekNo,
                    ),
                    createdBy = CREATED_BY,
                )
            )
                .expectErrorSatisfies { error ->
                    (error as ResponseStatusException).statusCode shouldBe HttpStatus.BAD_REQUEST
                    error.reason shouldBe "targetWeekNo는 1 이상이어야 합니다."
                }
                .verify()

            Mockito.verify(fixture.assignmentRepository, Mockito.never())
                .save(ArgumentMatchers.any(Assignment::class.java))
        }
    }

    "invalid targetOrderInWeek values return BAD_REQUEST before saving the copied assignment" {
        listOf(0, -1).forEach { invalidOrder ->
            val fixture = AssignmentCopyFixture()
            fixture.stubCopyPath()

            StepVerifier.create(
                fixture.service.copyAssignment(
                    targetCourseSlug = TARGET_COURSE_SLUG,
                    request = CopyAssignmentRequest(
                        sourceAssignmentId = SOURCE_ASSIGNMENT_ID,
                        targetOrderInWeek = invalidOrder,
                    ),
                    createdBy = CREATED_BY,
                )
            )
                .expectErrorSatisfies { error ->
                    (error as ResponseStatusException).statusCode shouldBe HttpStatus.BAD_REQUEST
                    error.reason shouldBe "targetOrderInWeek는 1 이상이어야 합니다."
                }
                .verify()

            Mockito.verify(fixture.assignmentRepository, Mockito.never())
                .save(ArgumentMatchers.any(Assignment::class.java))
        }
    }

    "targetEndAt before targetStartAt returns BAD_REQUEST without saving" {
        val fixture = AssignmentCopyFixture()
        fixture.stubCopyPath()

        StepVerifier.create(
            fixture.service.copyAssignment(
                targetCourseSlug = TARGET_COURSE_SLUG,
                request = CopyAssignmentRequest(
                    sourceAssignmentId = SOURCE_ASSIGNMENT_ID,
                    targetStartAt = Instant.parse("2026-05-19T00:00:00Z"),
                    targetEndAt = Instant.parse("2026-05-12T00:00:00Z"),
                ),
                createdBy = CREATED_BY,
            )
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.BAD_REQUEST
                error.reason shouldBe "과제 종료 시간은 시작 시간보다 빠를 수 없습니다."
            }
            .verify()

        Mockito.verify(fixture.assignmentRepository, Mockito.never())
            .save(ArgumentMatchers.any(Assignment::class.java))
    }

    "target course lookup failure returns NOT_FOUND" {
        val fixture = AssignmentCopyFixture()
        Mockito.`when`(fixture.courseRepository.findBySlug("missing-course")).thenReturn(Mono.empty())

        StepVerifier.create(
            fixture.service.copyAssignment(
                targetCourseSlug = "missing-course",
                request = CopyAssignmentRequest(sourceAssignmentId = SOURCE_ASSIGNMENT_ID),
                createdBy = CREATED_BY,
            )
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.NOT_FOUND
                error.reason shouldBe "코스를 찾을 수 없습니다: missing-course"
            }
            .verify()
    }

    "missing source assignment returns NOT_FOUND without copying child documents" {
        val fixture = AssignmentCopyFixture()
        Mockito.`when`(fixture.courseRepository.findBySlug(TARGET_COURSE_SLUG))
            .thenReturn(Mono.just(targetCourse()))
        Mockito.`when`(fixture.assignmentRepository.findById(SOURCE_ASSIGNMENT_ID))
            .thenReturn(Mono.empty())

        StepVerifier.create(
            fixture.service.copyAssignment(
                targetCourseSlug = TARGET_COURSE_SLUG,
                request = CopyAssignmentRequest(sourceAssignmentId = SOURCE_ASSIGNMENT_ID),
                createdBy = CREATED_BY,
            )
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.NOT_FOUND
                error.reason shouldBe "원본 과제를 찾을 수 없습니다: $SOURCE_ASSIGNMENT_ID"
            }
            .verify()

        Mockito.verifyNoInteractions(fixture.assignmentRequirementRepository)
        Mockito.verifyNoInteractions(fixture.assignmentTestCaseRepository)
    }

    "source assignment course lookup uses assignment courseSlug fallback when present" {
        val sourceAssignment = sourceAssignment(courseSlug = "legacy-source-course")
        val fixture = AssignmentCopyFixture()
        fixture.stubCopyPath(sourceAssignment = sourceAssignment, sourceCourse = null)

        StepVerifier.create(
            fixture.service.copyAssignment(
                targetCourseSlug = TARGET_COURSE_SLUG,
                request = CopyAssignmentRequest(sourceAssignmentId = SOURCE_ASSIGNMENT_ID),
                createdBy = CREATED_BY,
            )
        )
            .assertNext { response ->
                response.courseSlug shouldBe TARGET_COURSE_SLUG
            }
            .verifyComplete()

        fixture.savedAssignment.shouldNotBeNull().originCourseSlug shouldBe "legacy-source-course"
    }

    "source assignment course lookup returns NOT_FOUND when repository and fallback slug are unavailable" {
        val fixture = AssignmentCopyFixture()
        fixture.stubCopyPath(sourceAssignment = sourceAssignment(courseSlug = ""), sourceCourse = null)

        StepVerifier.create(
            fixture.service.copyAssignment(
                targetCourseSlug = TARGET_COURSE_SLUG,
                request = CopyAssignmentRequest(sourceAssignmentId = SOURCE_ASSIGNMENT_ID),
                createdBy = CREATED_BY,
            )
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.NOT_FOUND
                error.reason shouldBe "원본 과제의 코스를 찾을 수 없습니다: $SOURCE_COURSE_ID"
            }
            .verify()
    }

    "existing assignment with the same originAssignmentId returns CONFLICT without saving" {
        val fixture = AssignmentCopyFixture()
        fixture.stubCopyPath(targetWeekExists = false)
        Mockito.`when`(
            fixture.assignmentRepository.findByCourseIdAndOriginAssignmentId(
                TARGET_COURSE_ID,
                SOURCE_ASSIGNMENT_ID,
            )
        ).thenReturn(Mono.just(existingTargetAssignment(originAssignmentId = SOURCE_ASSIGNMENT_ID)))

        StepVerifier.create(
            fixture.service.copyAssignment(
                targetCourseSlug = TARGET_COURSE_SLUG,
                request = CopyAssignmentRequest(sourceAssignmentId = SOURCE_ASSIGNMENT_ID),
                createdBy = CREATED_BY,
            )
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.CONFLICT
                error.reason shouldBe "이미 대상 코스에 동일한 원본 과제가 존재합니다."
            }
            .verify()

        Mockito.verify(fixture.assignmentRepository, Mockito.never())
            .save(ArgumentMatchers.any(Assignment::class.java))
        Mockito.verify(fixture.courseWeekRepository, Mockito.never())
            .save(ArgumentMatchers.any(CourseWeek::class.java))
    }

    "existing direct assignment with the same origin id fallback returns CONFLICT without saving" {
        val fixture = AssignmentCopyFixture()
        fixture.stubCopyPath()
        Mockito.`when`(fixture.assignmentRepository.findByCourseIdAndOriginAssignmentId(TARGET_COURSE_ID, SOURCE_ASSIGNMENT_ID))
            .thenReturn(Mono.empty())
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId(SOURCE_ASSIGNMENT_ID, TARGET_COURSE_ID))
            .thenReturn(Mono.just(existingTargetAssignment(id = SOURCE_ASSIGNMENT_ID)))

        StepVerifier.create(
            fixture.service.copyAssignment(
                targetCourseSlug = TARGET_COURSE_SLUG,
                request = CopyAssignmentRequest(sourceAssignmentId = SOURCE_ASSIGNMENT_ID),
                createdBy = CREATED_BY,
            )
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.CONFLICT
            }
            .verify()

        Mockito.verify(fixture.assignmentRepository, Mockito.never())
            .save(ArgumentMatchers.any(Assignment::class.java))
    }

    "existing assignment with the same copy fingerprint returns CONFLICT without saving" {
        val fixture = AssignmentCopyFixture()
        fixture.stubCopyPath(targetWeekExists = false)
        Mockito.`when`(
            fixture.assignmentRepository.findByCourseIdAndCopyFingerprint(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
            )
        ).thenReturn(Mono.just(existingTargetAssignment()))

        StepVerifier.create(
            fixture.service.copyAssignment(
                targetCourseSlug = TARGET_COURSE_SLUG,
                request = CopyAssignmentRequest(sourceAssignmentId = SOURCE_ASSIGNMENT_ID),
                createdBy = CREATED_BY,
            )
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.CONFLICT
                error.reason shouldBe "이미 대상 코스에 동일한 내용의 과제가 존재합니다."
            }
            .verify()

        Mockito.verify(fixture.assignmentRepository, Mockito.never())
            .save(ArgumentMatchers.any(Assignment::class.java))
        Mockito.verify(fixture.courseWeekRepository, Mockito.never())
            .save(ArgumentMatchers.any(CourseWeek::class.java))
    }

    "existing assignment in the same course week order slot returns CONFLICT without saving" {
        val fixture = AssignmentCopyFixture()
        fixture.stubCopyPath(targetWeekExists = false)
        Mockito.`when`(fixture.assignmentRepository.findByCourseIdAndWeekNoAndOrderInWeek(TARGET_COURSE_ID, 1, 1))
            .thenReturn(Mono.just(existingTargetAssignment()))

        StepVerifier.create(
            fixture.service.copyAssignment(
                targetCourseSlug = TARGET_COURSE_SLUG,
                request = CopyAssignmentRequest(sourceAssignmentId = SOURCE_ASSIGNMENT_ID),
                createdBy = CREATED_BY,
            )
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.CONFLICT
                error.reason shouldBe "동일 코스/주차/순번 과제가 이미 존재합니다."
            }
            .verify()

        Mockito.verify(fixture.assignmentRepository, Mockito.never())
            .save(ArgumentMatchers.any(Assignment::class.java))
        Mockito.verify(fixture.courseWeekRepository, Mockito.never())
            .save(ArgumentMatchers.any(CourseWeek::class.java))
    }

    "DuplicateKeyException while saving the assignment maps to copy CONFLICT" {
        val fixture = AssignmentCopyFixture()
        fixture.stubCopyPath()
        Mockito.`when`(fixture.assignmentRepository.save(ArgumentMatchers.any(Assignment::class.java)))
            .thenReturn(Mono.error(DuplicateKeyException("duplicate assignment copy")))

        StepVerifier.create(
            fixture.service.copyAssignment(
                targetCourseSlug = TARGET_COURSE_SLUG,
                request = CopyAssignmentRequest(sourceAssignmentId = SOURCE_ASSIGNMENT_ID),
                createdBy = CREATED_BY,
            )
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.CONFLICT
                error.reason shouldBe "이미 대상 코스에 동일한 원본 또는 동일한 내용의 과제가 존재합니다."
            }
            .verify()
    }

    "full successful copy creates a draft assignment, deep-copies children, and publishes one create event" {
        val sourceInputs = mutableListOf("2 3", "ADD")
        val sourceAssignment = sourceAssignment()
        val sourceAssignmentBefore = sourceAssignment.copy()
        val sourceRequirements = listOf(
            sourceRequirement(sortOrder = 2, requirementText = "예외 처리"),
            sourceRequirement(sortOrder = 1, requirementText = "함수 분리"),
        )
        val sourceTestCases = listOf(
            sourceTestCase(seq = 2, inputValues = sourceInputs, outputText = "5", visibility = AssignmentTestCaseVisibility.HIDDEN),
            sourceTestCase(seq = 1, inputValues = listOf("1 2"), outputText = "3", visibility = AssignmentTestCaseVisibility.PUBLIC),
            sourceTestCase(seq = 3, inputValues = listOf("debug"), outputText = "skip", visibility = AssignmentTestCaseVisibility.EXCLUDED),
        )
        val fixture = AssignmentCopyFixture()
        fixture.stubCopyPath(
            sourceAssignment = sourceAssignment,
            sourceRequirements = sourceRequirements,
            sourceTestCases = sourceTestCases,
        )

        StepVerifier.create(
            fixture.service.copyAssignment(
                targetCourseSlug = TARGET_COURSE_SLUG,
                request = CopyAssignmentRequest(
                    sourceAssignmentId = SOURCE_ASSIGNMENT_ID,
                    targetWeekNo = 3,
                    targetOrderInWeek = 4,
                    targetStartAt = OVERRIDE_START_AT,
                    targetEndAt = OVERRIDE_END_AT,
                ),
                createdBy = CREATED_BY,
            )
        )
            .assertNext { response ->
                val saved = fixture.savedAssignment.shouldNotBeNull()
                response.id shouldBe saved.id
                response.id shouldNotBe SOURCE_ASSIGNMENT_ID
                UUID.fromString(response.id).toString() shouldBe response.id
                response.courseSlug shouldBe TARGET_COURSE_SLUG
                response.weekNo shouldBe 3
                response.orderInWeek shouldBe 4
                response.startAt shouldBe OVERRIDE_START_AT
                response.endAt shouldBe OVERRIDE_END_AT
                response.status shouldBe AssignmentStatus.DRAFT
                response.publishedAt.shouldBeNull()
                response.metadata.title shouldBe sourceAssignment.metadata.title
                response.metadata.difficulty shouldBe sourceAssignment.metadata.difficulty
                response.metadata.description shouldBe sourceAssignment.metadata.description
                response.metadata.requirements.map { it.sortOrder } shouldContainExactly listOf(1, 2)
                response.metadata.requirements.map { it.requirementText } shouldContainExactly listOf("함수 분리", "예외 처리")
                response.metadata.testCases.map { it.seq } shouldContainExactly listOf(1, 2, 3)
                response.metadata.testCases.map { it.outputText } shouldContainExactly listOf("3", "5", "skip")
                response.metadata.testCases.map { it.visibility } shouldContainExactly listOf(
                    AssignmentTestCaseVisibility.PUBLIC,
                    AssignmentTestCaseVisibility.HIDDEN,
                    AssignmentTestCaseVisibility.EXCLUDED,
                )
            }
            .verifyComplete()

        val saved = fixture.savedAssignment.shouldNotBeNull()
        saved.courseId shouldBe TARGET_COURSE_ID
        saved.courseSlug shouldBe TARGET_COURSE_SLUG
        saved.createdBy shouldBe CREATED_BY
        saved.status shouldBe AssignmentStatus.DRAFT
        saved.publishedAt.shouldBeNull()
        saved.originAssignmentId shouldBe SOURCE_ASSIGNMENT_ID
        saved.originCourseSlug shouldBe SOURCE_COURSE_SLUG
        saved.copyFingerprint.isNullOrBlank() shouldBe false
        sourceAssignment shouldBe sourceAssignmentBefore

        val savedId = requireNotNull(saved.id)
        fixture.savedRequirements.map { it.assignmentId }.toSet() shouldBe setOf(savedId)
        fixture.savedRequirements.map { it.sortOrder } shouldContainExactly listOf(1, 2)
        fixture.savedTestCases.map { it.assignmentId }.toSet() shouldBe setOf(savedId)
        fixture.savedTestCases.map { it.seq } shouldContainExactly listOf(1, 2, 3)
        fixture.savedTestCases.first { it.seq == 2 }.inputValues shouldBe listOf("2 3", "ADD")
        sourceInputs += "MUTATED_AFTER_COPY"
        fixture.savedTestCases.first { it.seq == 2 }.inputValues shouldBe listOf("2 3", "ADD")

        Mockito.verify(fixture.assignmentRepository, Mockito.atLeastOnce()).findById(savedId)
        val event = fixture.eventPublisher.events.single()
        event.eventType shouldBe AssignmentReportTestCaseEventType.PROBLEM_CREATED
        event.problemId shouldBe savedId
        event.testCases.map { it.caseId } shouldContainExactly listOf(1, 2)
    }

    "child writes subscribe sequentially and publish only after both complete" {
        val fixture = AssignmentCopyFixture()
        fixture.stubCopyPath(
            sourceRequirements = listOf(sourceRequirement()),
            sourceTestCases = listOf(sourceTestCase()),
        )
        val requirementSavePublisher = TestPublisher.create<AssignmentRequirement>()
        val testCaseSavePublisher = TestPublisher.create<AssignmentTestCase>()
        var requirementSaveSubscribed = false
        var testCaseSaveSubscribed = false
        Mockito.`when`(fixture.assignmentRequirementRepository.saveAll(ArgumentMatchers.anyList<AssignmentRequirement>()))
            .thenReturn(
                requirementSavePublisher.flux()
                    .doOnSubscribe { requirementSaveSubscribed = true }
            )
        Mockito.`when`(fixture.assignmentTestCaseRepository.saveAll(ArgumentMatchers.anyList<AssignmentTestCase>()))
            .thenReturn(
                testCaseSavePublisher.flux()
                    .doOnSubscribe { testCaseSaveSubscribed = true }
            )

        StepVerifier.create(
            fixture.service.copyAssignment(
                targetCourseSlug = TARGET_COURSE_SLUG,
                request = CopyAssignmentRequest(sourceAssignmentId = SOURCE_ASSIGNMENT_ID),
                createdBy = CREATED_BY,
            )
        )
            .then {
                requirementSaveSubscribed shouldBe true
                testCaseSaveSubscribed shouldBe false
                fixture.eventPublisher.events shouldBe emptyList()
                requirementSavePublisher.emit(sourceRequirement())
            }
            .then {
                testCaseSaveSubscribed shouldBe true
                fixture.eventPublisher.events shouldBe emptyList()
                testCaseSavePublisher.emit(sourceTestCase())
            }
            .assertNext { response ->
                response.metadata.requirements shouldHaveSize 1
                response.metadata.testCases shouldHaveSize 1
            }
            .verifyComplete()

        fixture.eventPublisher.events.single().eventType shouldBe AssignmentReportTestCaseEventType.PROBLEM_CREATED
    }

    "copying an already-copied assignment preserves the first origin assignment and course" {
        val firstOriginAssignmentId = "5e67701e-7671-4f71-92ce-a9a6ce12fbb3"
        val firstOriginCourseSlug = "first-origin-course"
        val copiedSource = sourceAssignment(
            originAssignmentId = firstOriginAssignmentId,
            originCourseSlug = firstOriginCourseSlug,
        )
        val fixture = AssignmentCopyFixture()
        fixture.stubCopyPath(sourceAssignment = copiedSource)

        StepVerifier.create(
            fixture.service.copyAssignment(
                targetCourseSlug = TARGET_COURSE_SLUG,
                request = CopyAssignmentRequest(sourceAssignmentId = SOURCE_ASSIGNMENT_ID),
                createdBy = CREATED_BY,
            )
        )
            .expectNextCount(1)
            .verifyComplete()

        val saved = fixture.savedAssignment.shouldNotBeNull()
        saved.originAssignmentId shouldBe firstOriginAssignmentId
        saved.originAssignmentId shouldNotBe SOURCE_ASSIGNMENT_ID
        saved.originCourseSlug shouldBe firstOriginCourseSlug
    }

    "no override values use source week order and schedule" {
        val source = sourceAssignment(weekNo = 5, orderInWeek = 6, startAt = SOURCE_START_AT, endAt = SOURCE_END_AT)
        val fixture = AssignmentCopyFixture()
        fixture.stubCopyPath(sourceAssignment = source)

        StepVerifier.create(
            fixture.service.copyAssignment(
                targetCourseSlug = TARGET_COURSE_SLUG,
                request = CopyAssignmentRequest(sourceAssignmentId = SOURCE_ASSIGNMENT_ID),
                createdBy = CREATED_BY,
            )
        )
            .assertNext { response ->
                response.weekNo shouldBe 5
                response.orderInWeek shouldBe 6
                response.startAt shouldBe SOURCE_START_AT
                response.endAt shouldBe SOURCE_END_AT
            }
            .verifyComplete()
    }

    "empty requirements and testcases succeed without saveAll calls" {
        val fixture = AssignmentCopyFixture()
        fixture.stubCopyPath(sourceRequirements = emptyList(), sourceTestCases = emptyList())

        StepVerifier.create(
            fixture.service.copyAssignment(
                targetCourseSlug = TARGET_COURSE_SLUG,
                request = CopyAssignmentRequest(sourceAssignmentId = SOURCE_ASSIGNMENT_ID),
                createdBy = CREATED_BY,
            )
        )
            .assertNext { response ->
                response.metadata.requirements shouldBe emptyList()
                response.metadata.testCases shouldBe emptyList()
            }
            .verifyComplete()

        Mockito.verify(fixture.assignmentRequirementRepository, Mockito.never())
            .saveAll(ArgumentMatchers.anyList<AssignmentRequirement>())
        Mockito.verify(fixture.assignmentTestCaseRepository, Mockito.never())
            .saveAll(ArgumentMatchers.anyList<AssignmentTestCase>())
    }

    "missing target CourseWeek creates the week with KST local dates" {
        val fixture = AssignmentCopyFixture()
        fixture.stubCopyPath(targetWeekExists = false)

        StepVerifier.create(
            fixture.service.copyAssignment(
                targetCourseSlug = TARGET_COURSE_SLUG,
                request = CopyAssignmentRequest(
                    sourceAssignmentId = SOURCE_ASSIGNMENT_ID,
                    targetWeekNo = 7,
                    targetStartAt = Instant.parse("2026-05-11T15:00:00Z"),
                    targetEndAt = Instant.parse("2026-05-18T14:59:59Z"),
                ),
                createdBy = CREATED_BY,
            )
        )
            .expectNextCount(1)
            .verifyComplete()

        val savedWeek = fixture.savedWeek.shouldNotBeNull()
        savedWeek.courseId shouldBe TARGET_COURSE_ID
        savedWeek.weekNo shouldBe 7
        savedWeek.title shouldBe "7주차"
        savedWeek.startDate shouldBe LocalDate.of(2026, 5, 12)
        savedWeek.endDate shouldBe LocalDate.of(2026, 5, 18)
    }

    "existing target CourseWeek does not create a duplicate week" {
        val fixture = AssignmentCopyFixture()
        fixture.stubCopyPath(targetWeekExists = true)

        StepVerifier.create(
            fixture.service.copyAssignment(
                targetCourseSlug = TARGET_COURSE_SLUG,
                request = CopyAssignmentRequest(sourceAssignmentId = SOURCE_ASSIGNMENT_ID),
                createdBy = CREATED_BY,
            )
        )
            .expectNextCount(1)
            .verifyComplete()

        Mockito.verify(fixture.courseWeekRepository, Mockito.never())
            .save(ArgumentMatchers.any(CourseWeek::class.java))
    }

    "requirement copy failure does not subscribe testcase copy and starts complete cleanup" {
        val fixture = AssignmentCopyFixture()
        fixture.stubCopyPath(
            sourceRequirements = listOf(sourceRequirement()),
            sourceTestCases = listOf(sourceTestCase()),
        )
        val requirementSavePublisher = TestPublisher.create<AssignmentRequirement>()
        val testCaseSavePublisher = TestPublisher.create<AssignmentTestCase>()
        val copyFailure = IllegalStateException("requirement save failed")
        var requirementSaveSubscribed = false
        var testCaseSaveSubscribed = false
        Mockito.`when`(fixture.assignmentRequirementRepository.saveAll(ArgumentMatchers.anyList<AssignmentRequirement>()))
            .thenReturn(
                requirementSavePublisher.flux()
                    .doOnSubscribe { requirementSaveSubscribed = true }
            )
        Mockito.`when`(fixture.assignmentTestCaseRepository.saveAll(ArgumentMatchers.anyList<AssignmentTestCase>()))
            .thenReturn(
                testCaseSavePublisher.flux()
                    .doOnSubscribe { testCaseSaveSubscribed = true }
            )
        val cleanupSubscriptions = fixture.trackCleanupSubscriptions()

        StepVerifier.create(
            fixture.service.copyAssignment(
                targetCourseSlug = TARGET_COURSE_SLUG,
                request = CopyAssignmentRequest(sourceAssignmentId = SOURCE_ASSIGNMENT_ID),
                createdBy = CREATED_BY,
            )
        )
            .then {
                requirementSaveSubscribed shouldBe true
                testCaseSaveSubscribed shouldBe false
                requirementSavePublisher.error(copyFailure)
            }
            .expectErrorSatisfies { error ->
                error shouldBe copyFailure
            }
            .verify()

        testCaseSaveSubscribed shouldBe false
        cleanupSubscriptions.allSubscribed() shouldBe true
        fixture.verifyCleanupForSavedAssignment()
    }

    "testcase copy failure after assignment save performs complete cleanup" {
        val fixture = AssignmentCopyFixture()
        fixture.stubCopyPath(sourceTestCases = listOf(sourceTestCase()))
        val copyFailure = IllegalStateException("testcase save failed")
        Mockito.`when`(fixture.assignmentTestCaseRepository.saveAll(ArgumentMatchers.anyList<AssignmentTestCase>()))
            .thenReturn(Flux.error(copyFailure))
        val cleanupSubscriptions = fixture.trackCleanupSubscriptions()

        StepVerifier.create(
            fixture.service.copyAssignment(
                targetCourseSlug = TARGET_COURSE_SLUG,
                request = CopyAssignmentRequest(sourceAssignmentId = SOURCE_ASSIGNMENT_ID),
                createdBy = CREATED_BY,
            )
        )
            .expectErrorSatisfies { error ->
                error shouldBe copyFailure
            }
            .verify()

        cleanupSubscriptions.allSubscribed() shouldBe true
        fixture.verifyCleanupForSavedAssignment()
    }

    "DuplicateKeyException during child document save maps to copy CONFLICT and still cleans up" {
        val fixture = AssignmentCopyFixture()
        fixture.stubCopyPath(sourceRequirements = listOf(sourceRequirement()))
        Mockito.`when`(fixture.assignmentRequirementRepository.saveAll(ArgumentMatchers.anyList<AssignmentRequirement>()))
            .thenReturn(Flux.error(DuplicateKeyException("duplicate child")))

        StepVerifier.create(
            fixture.service.copyAssignment(
                targetCourseSlug = TARGET_COURSE_SLUG,
                request = CopyAssignmentRequest(sourceAssignmentId = SOURCE_ASSIGNMENT_ID),
                createdBy = CREATED_BY,
            )
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.CONFLICT
                error.reason shouldBe "이미 대상 코스에 동일한 원본 또는 동일한 내용의 과제가 존재합니다."
            }
            .verify()

        fixture.verifyCleanupForSavedAssignment()
    }

    "cleanup failure does not hide the original copy failure" {
        val fixture = AssignmentCopyFixture()
        fixture.stubCopyPath(sourceRequirements = listOf(sourceRequirement()))
        val copyFailure = IllegalStateException("requirement save failed")
        val cleanupFailure = IllegalStateException("cleanup failed")
        Mockito.`when`(fixture.assignmentRequirementRepository.saveAll(ArgumentMatchers.anyList<AssignmentRequirement>()))
            .thenReturn(Flux.error(copyFailure))
        val cleanupSubscriptions = fixture.trackCleanupSubscriptions(
            deliveryDeleteResult = Mono.error(cleanupFailure),
        )

        StepVerifier.create(
            fixture.service.copyAssignment(
                targetCourseSlug = TARGET_COURSE_SLUG,
                request = CopyAssignmentRequest(sourceAssignmentId = SOURCE_ASSIGNMENT_ID),
                createdBy = CREATED_BY,
            )
        )
            .expectErrorSatisfies { error ->
                error shouldBe copyFailure
            }
            .verify()

        cleanupSubscriptions.allSubscribed() shouldBe true
        fixture.verifyCleanupForSavedAssignment()
    }

    "missing saved assignment snapshot emits IllegalStateException and does not publish an incomplete event" {
        val fixture = AssignmentCopyFixture()
        fixture.stubCopyPath(eventSnapshotAssignment = { null })

        StepVerifier.create(
            fixture.service.copyAssignment(
                targetCourseSlug = TARGET_COURSE_SLUG,
                request = CopyAssignmentRequest(sourceAssignmentId = SOURCE_ASSIGNMENT_ID),
                createdBy = CREATED_BY,
            )
        )
            .expectErrorSatisfies { error ->
                error::class shouldBe IllegalStateException::class
                error.message shouldBe "problem sync 대상 assignment snapshot을 찾을 수 없습니다: ${fixture.savedAssignment?.id}"
            }
            .verify()

        fixture.eventPublisher.events shouldBe emptyList()
    }

    "event publisher failure propagates the publisher error" {
        val fixture = AssignmentCopyFixture()
        fixture.eventPublisher.failure = IllegalStateException("publisher unavailable")
        fixture.stubCopyPath()

        StepVerifier.create(
            fixture.service.copyAssignment(
                targetCourseSlug = TARGET_COURSE_SLUG,
                request = CopyAssignmentRequest(sourceAssignmentId = SOURCE_ASSIGNMENT_ID),
                createdBy = CREATED_BY,
            )
        )
            .expectErrorSatisfies { error ->
                error::class shouldBe IllegalStateException::class
                error.message shouldBe "publisher unavailable"
            }
            .verify()

        fixture.eventPublisher.events shouldHaveSize 1
    }
})

private class AssignmentCopyFixture {
    val courseRepository: CourseRepository = Mockito.mock(CourseRepository::class.java)
    val courseWeekRepository: CourseWeekRepository = Mockito.mock(CourseWeekRepository::class.java)
    val assignmentRepository: AssignmentRepository = Mockito.mock(AssignmentRepository::class.java)
    val assignmentRequirementRepository: AssignmentRequirementRepository = Mockito.mock(AssignmentRequirementRepository::class.java)
    val assignmentTestCaseRepository: AssignmentTestCaseRepository = Mockito.mock(AssignmentTestCaseRepository::class.java)
    val assignmentDeliveryRepository: AssignmentDeliveryRepository = Mockito.mock(AssignmentDeliveryRepository::class.java)
    val eventPublisher = RecordingAssignmentReportTestCaseEventPublisher()
    val assignmentCoursePort = AssignmentCourseAdapter(courseRepository, courseWeekRepository)
    val assignmentProblemSyncPort = DirectAssignmentProblemSyncAdapter(
        assignmentRepository = assignmentRepository,
        assignmentTestCaseRepository = assignmentTestCaseRepository,
        eventMapper = AssignmentReportTestCaseEventMapper(),
        eventPublisher = eventPublisher,
    )
    val service = AssignmentCopyService(
        assignmentCoursePort = assignmentCoursePort,
        assignmentRepository = assignmentRepository,
        assignmentRequirementRepository = assignmentRequirementRepository,
        assignmentTestCaseRepository = assignmentTestCaseRepository,
        assignmentDeliveryRepository = assignmentDeliveryRepository,
        assignmentProblemSyncPort = assignmentProblemSyncPort,
        assignmentCopyFingerprintCalculator = AssignmentCopyFingerprintCalculator(),
    )

    var savedAssignment: Assignment? = null
        private set
    var savedRequirements: List<AssignmentRequirement> = emptyList()
        private set
    var savedTestCases: List<AssignmentTestCase> = emptyList()
        private set
    var savedWeek: CourseWeek? = null
        private set

    fun stubCopyPath(
        targetCourse: Course = targetCourse(),
        sourceCourse: Course? = sourceCourse(),
        sourceAssignment: Assignment = sourceAssignment(),
        sourceRequirements: List<AssignmentRequirement> = listOf(sourceRequirement()),
        sourceTestCases: List<AssignmentTestCase> = listOf(sourceTestCase()),
        targetWeekExists: Boolean = true,
        eventSnapshotAssignment: (Assignment) -> Assignment? = { it },
    ) {
        val sourceId = requireNotNull(sourceAssignment.id)

        Mockito.`when`(courseRepository.findBySlug(targetCourse.slug)).thenReturn(Mono.just(targetCourse))
        Mockito.`when`(courseRepository.findById(sourceAssignment.courseId))
            .thenReturn(sourceCourse?.let { Mono.just(it) } ?: Mono.empty())

        Mockito.`when`(assignmentRepository.findById(ArgumentMatchers.anyString()))
            .thenAnswer { invocation ->
                val assignmentId = invocation.arguments[0] as String
                when {
                    assignmentId == sourceId -> Mono.just(sourceAssignment)
                    assignmentId == savedAssignment?.id -> {
                        val snapshot = savedAssignment?.let(eventSnapshotAssignment)
                        snapshot?.let { Mono.just(it) } ?: Mono.empty<Assignment>()
                    }
                    else -> Mono.empty<Assignment>()
                }
            }
        Mockito.`when`(assignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder(sourceId))
            .thenReturn(Flux.fromIterable(sourceRequirements))
        Mockito.`when`(assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(ArgumentMatchers.anyString()))
            .thenAnswer { invocation ->
                val assignmentId = invocation.arguments[0] as String
                when {
                    assignmentId == sourceId -> Flux.fromIterable(sourceTestCases)
                    assignmentId == savedAssignment?.id -> Flux.fromIterable(savedTestCases)
                    else -> Flux.empty<AssignmentTestCase>()
                }
            }
        Mockito.`when`(courseWeekRepository.findByCourseIdAndWeekNo(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt()))
            .thenAnswer { invocation ->
                if (targetWeekExists) {
                    Mono.just(
                        CourseWeek(
                            id = "week-${invocation.arguments[1]}",
                            courseId = invocation.arguments[0] as String,
                            weekNo = invocation.arguments[1] as Int,
                            title = "${invocation.arguments[1]}주차",
                        )
                    )
                } else {
                    Mono.empty<CourseWeek>()
                }
            }
        Mockito.`when`(courseWeekRepository.save(ArgumentMatchers.any(CourseWeek::class.java)))
            .thenAnswer { invocation ->
                val week = invocation.arguments[0] as CourseWeek
                savedWeek = week
                Mono.just(week)
            }
        Mockito.`when`(assignmentRepository.findByCourseIdAndOriginAssignmentId(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
            .thenReturn(Mono.empty())
        Mockito.`when`(assignmentRepository.findByIdAndCourseId(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
            .thenReturn(Mono.empty())
        Mockito.`when`(assignmentRepository.findByCourseIdAndCopyFingerprint(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
            .thenReturn(Mono.empty())
        Mockito.`when`(
            assignmentRepository.findByCourseIdAndWeekNoAndOrderInWeek(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyInt(),
                ArgumentMatchers.anyInt(),
            )
        ).thenReturn(Mono.empty())
        Mockito.`when`(assignmentRepository.save(ArgumentMatchers.any(Assignment::class.java)))
            .thenAnswer { invocation ->
                val assignment = invocation.arguments[0] as Assignment
                savedAssignment = assignment
                Mono.just(assignment)
            }
        Mockito.`when`(assignmentRequirementRepository.saveAll(ArgumentMatchers.anyList<AssignmentRequirement>()))
            .thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val requirements = invocation.arguments[0] as List<AssignmentRequirement>
                savedRequirements = requirements
                Flux.fromIterable(requirements)
            }
        Mockito.`when`(assignmentTestCaseRepository.saveAll(ArgumentMatchers.anyList<AssignmentTestCase>()))
            .thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val testCases = invocation.arguments[0] as List<AssignmentTestCase>
                savedTestCases = testCases
                Flux.fromIterable(testCases)
            }
        Mockito.`when`(assignmentRequirementRepository.deleteAllByAssignmentIdIn(ArgumentMatchers.anyCollection()))
            .thenReturn(Mono.just(0))
        Mockito.`when`(assignmentTestCaseRepository.deleteAllByAssignmentIdIn(ArgumentMatchers.anyCollection()))
            .thenReturn(Mono.just(0))
        Mockito.`when`(assignmentDeliveryRepository.deleteAllByAssignmentIdIn(ArgumentMatchers.anyCollection()))
            .thenReturn(Mono.just(0))
        Mockito.`when`(assignmentRepository.deleteById(ArgumentMatchers.anyString()))
            .thenReturn(Mono.empty())
    }

    fun verifyNoRepositoryInteractions() {
        Mockito.verifyNoInteractions(
            courseRepository,
            courseWeekRepository,
            assignmentRepository,
            assignmentRequirementRepository,
            assignmentTestCaseRepository,
            assignmentDeliveryRepository,
        )
    }

    fun verifyNoWrites() {
        Mockito.verify(assignmentRepository, Mockito.never()).save(ArgumentMatchers.any(Assignment::class.java))
        Mockito.verify(assignmentRequirementRepository, Mockito.never()).saveAll(ArgumentMatchers.anyList<AssignmentRequirement>())
        Mockito.verify(assignmentTestCaseRepository, Mockito.never()).saveAll(ArgumentMatchers.anyList<AssignmentTestCase>())
    }

    fun verifyCleanupForSavedAssignment() {
        val assignmentId = requireNotNull(savedAssignment?.id)
        val ids = listOf(assignmentId)
        Mockito.verify(assignmentRequirementRepository).deleteAllByAssignmentIdIn(ids)
        Mockito.verify(assignmentTestCaseRepository).deleteAllByAssignmentIdIn(ids)
        Mockito.verify(assignmentDeliveryRepository).deleteAllByAssignmentIdIn(ids)
        Mockito.verify(assignmentRepository).deleteById(assignmentId)
    }

    fun trackCleanupSubscriptions(
        deliveryDeleteResult: Mono<Long> = Mono.just(0L),
    ): CleanupSubscriptions {
        val subscriptions = CleanupSubscriptions()
        Mockito.`when`(assignmentRequirementRepository.deleteAllByAssignmentIdIn(ArgumentMatchers.anyCollection()))
            .thenReturn(
                Mono.just(0L)
                    .doOnSubscribe { subscriptions.requirements = true }
            )
        Mockito.`when`(assignmentTestCaseRepository.deleteAllByAssignmentIdIn(ArgumentMatchers.anyCollection()))
            .thenReturn(
                Mono.just(0L)
                    .doOnSubscribe { subscriptions.testCases = true }
            )
        Mockito.`when`(assignmentDeliveryRepository.deleteAllByAssignmentIdIn(ArgumentMatchers.anyCollection()))
            .thenReturn(
                deliveryDeleteResult
                    .doOnSubscribe { subscriptions.deliveries = true }
            )
        Mockito.`when`(assignmentRepository.deleteById(ArgumentMatchers.anyString()))
            .thenReturn(
                Mono.empty<Void>()
                    .doOnSubscribe { subscriptions.assignment = true }
            )
        return subscriptions
    }
}

private data class CleanupSubscriptions(
    var requirements: Boolean = false,
    var testCases: Boolean = false,
    var deliveries: Boolean = false,
    var assignment: Boolean = false,
) {
    fun allSubscribed(): Boolean = requirements && testCases && deliveries && assignment
}

private class RecordingAssignmentReportTestCaseEventPublisher : AssignmentReportTestCaseEventPublisher {
    val events = mutableListOf<AssignmentReportTestCaseEvent>()
    var failure: Throwable? = null

    override fun publish(event: AssignmentReportTestCaseEvent): Mono<Void> {
        events += event
        return failure?.let { Mono.error(it) } ?: Mono.empty()
    }
}

private const val SOURCE_ASSIGNMENT_ID = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
private const val TARGET_ASSIGNMENT_ID = "7c53f1b3-0df8-4a9d-a56d-a5f50b96b7a1"
private const val SOURCE_COURSE_ID = "source-course-id"
private const val SOURCE_COURSE_SLUG = "source-course"
private const val TARGET_COURSE_ID = "target-course-id"
private const val TARGET_COURSE_SLUG = "target-course"
private const val CREATED_BY = "copy-admin"
private val SOURCE_START_AT = Instant.parse("2026-05-12T00:00:00Z")
private val SOURCE_END_AT = Instant.parse("2026-05-19T00:00:00Z")
private val OVERRIDE_START_AT = Instant.parse("2026-06-02T00:00:00Z")
private val OVERRIDE_END_AT = Instant.parse("2026-06-09T00:00:00Z")

private fun sourceAssignment(
    id: String = SOURCE_ASSIGNMENT_ID,
    courseId: String = SOURCE_COURSE_ID,
    courseSlug: String = SOURCE_COURSE_SLUG,
    weekNo: Int = 1,
    orderInWeek: Int = 1,
    startAt: Instant = SOURCE_START_AT,
    endAt: Instant = SOURCE_END_AT,
    status: AssignmentStatus = AssignmentStatus.PUBLISHED,
    originAssignmentId: String? = null,
    originCourseSlug: String? = null,
): Assignment =
    Assignment(
        id = id,
        courseId = courseId,
        courseSlug = courseSlug,
        createdBy = "source-admin",
        weekNo = weekNo,
        orderInWeek = orderInWeek,
        startAt = startAt,
        endAt = endAt,
        metadata = AssignmentMetadata(
            title = "복사 원본 과제",
            difficulty = AssignmentDifficulty.MID,
            description = "copy source",
            timeLimitMinutes = 60,
            learningGoals = listOf("입출력", "함수 분리"),
            codeTemplates = listOf(
                AssignmentCodeTemplate(
                    language = AssignmentTemplateLanguage.KOTLIN,
                    functionTemplate = "fun solution(): String = TODO()",
                )
            ),
        ),
        status = status,
        createdAt = Instant.parse("2026-05-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-05-01T00:00:00Z"),
        publishedAt = if (status == AssignmentStatus.PUBLISHED) Instant.parse("2026-05-01T00:00:00Z") else null,
        originAssignmentId = originAssignmentId,
        originCourseSlug = originCourseSlug,
    )

private fun existingTargetAssignment(
    id: String = TARGET_ASSIGNMENT_ID,
    originAssignmentId: String? = null,
): Assignment =
    sourceAssignment(
        id = id,
        courseId = TARGET_COURSE_ID,
        courseSlug = TARGET_COURSE_SLUG,
        status = AssignmentStatus.DRAFT,
        originAssignmentId = originAssignmentId,
    )

private fun sourceRequirement(
    sortOrder: Int = 1,
    requirementText: String = "함수 분리",
): AssignmentRequirement =
    AssignmentRequirement(
        assignmentId = SOURCE_ASSIGNMENT_ID,
        sortOrder = sortOrder,
        requirementText = requirementText,
    )

private fun sourceTestCase(
    seq: Int = 1,
    inputValues: List<String> = listOf("1 2"),
    outputText: String = "3",
    visibility: AssignmentTestCaseVisibility = AssignmentTestCaseVisibility.PUBLIC,
): AssignmentTestCase =
    AssignmentTestCase(
        assignmentId = SOURCE_ASSIGNMENT_ID,
        seq = seq,
        inputValues = inputValues,
        outputText = outputText,
        visibility = visibility,
    )

private fun sourceCourse(): Course =
    course(id = SOURCE_COURSE_ID, slug = SOURCE_COURSE_SLUG, title = "원본 코스")

private fun targetCourse(): Course =
    course(id = TARGET_COURSE_ID, slug = TARGET_COURSE_SLUG, title = "대상 코스")

private fun course(
    id: String,
    slug: String,
    title: String,
): Course =
    Course(
        id = id,
        slug = slug,
        fieldTag = CourseTrack.FL,
        startDate = LocalDate.of(2026, 3, 1),
        endDate = LocalDate.of(2026, 6, 30),
        metadata = CourseMetadata(
            title = title,
            description = "$title 설명",
            phase = CoursePhase.BASIC,
        ),
        status = CourseStatus.PUBLISHED,
        createdAt = Instant.parse("2026-03-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-03-01T00:00:00Z"),
    )
