package com.example.aandi_post_web_server.assignment.application.service

import com.example.aandi_post_web_server.assignment.api.dto.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentRequirementResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentSummaryResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentTestCaseResponse
import com.example.aandi_post_web_server.assignment.application.mapper.toDetailResponse
import com.example.aandi_post_web_server.assignment.application.mapper.toResponse
import com.example.aandi_post_web_server.assignment.application.port.AssignmentCourseQueryPort
import com.example.aandi_post_web_server.assignment.application.port.AssignmentCourseReference
import com.example.aandi_post_web_server.assignment.application.port.AssignmentQueryStore
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentPublicationPolicy
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseVisibility
import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.entity.AssignmentRequirement
import com.example.aandi_post_web_server.assignment.entity.AssignmentTestCase
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRequirementRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentTestCaseRepository
import com.example.aandi_post_web_server.course.domain.model.AssignmentId
import com.example.aandi_post_web_server.course.domain.model.CourseId
import com.example.aandi_post_web_server.course.domain.model.CourseSlug
import com.example.aandi_post_web_server.course.domain.model.UserId
import com.example.aandi_post_web_server.course.domain.model.WeekNo
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Clock
import java.time.Instant

@Service
class AssignmentQueryService(
    private val assignmentCourseQueryPort: AssignmentCourseQueryPort,
    private val assignmentQueryStore: AssignmentQueryStore,
    private val assignmentRequirementRepository: AssignmentRequirementRepository,
    private val assignmentTestCaseRepository: AssignmentTestCaseRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val assignmentPublicationPolicy: AssignmentPublicationPolicy = AssignmentPublicationPolicy(clock),
) {

    fun getVisibleOutlineAssignments(courseId: CourseId): Flux<AssignmentOutlineReference> =
        assignmentQueryStore.findAllByCourseId(courseId.value)
            .filter(::isVisibleToUser)
            .sort(compareBy<Assignment> { it.weekNo }.thenBy { it.orderInWeek })
            .map { assignment ->
                AssignmentOutlineReference(
                    assignmentId = requireNotNull(assignment.id),
                    weekNo = assignment.weekNo,
                    orderInWeek = assignment.orderInWeek,
                    title = assignment.metadata.title,
                    difficulty = assignment.metadata.difficulty,
                    startAt = assignment.startAt,
                    endAt = assignment.endAt,
                )
            }

    fun getVisibleAssignmentCourseId(assignmentId: AssignmentId): Mono<String> =
        assignmentQueryStore.findById(assignmentId.value)
            .switchIfEmpty(assignmentNotFound(assignmentId))
            .flatMap { assignment -> ensureVisibleToUser(assignment, assignmentId) }
            .map(Assignment::courseId)

    fun getAssignmentsByWeek(
        courseSlug: String,
        weekNo: Int,
        status: AssignmentStatus?,
        userId: String,
    ): Flux<AssignmentSummaryResponse> =
        getAssignments(
            courseSlug = courseSlug,
            weekNo = weekNo,
            status = status,
            userId = userId,
        )

    fun getAssignments(
        courseSlug: String,
        weekNo: Int?,
        status: AssignmentStatus?,
        userId: String,
    ): Flux<AssignmentSummaryResponse> {
        val slug = parseCourseSlug(courseSlug)
        val parsedWeekNo = weekNo?.let(::parseWeekNo)
        val parsedUserId = parseUserId(userId)
        resolveVisibleAssignmentStatus(status)

        return findAccessibleCourseBySlug(slug, parsedUserId)
            .flatMapMany { course ->
                findAssignmentsByFilter(parseCourseId(requireNotNull(course.id)), parsedWeekNo)
            }
            .filter(::isVisibleToUser)
            .sort(compareBy<Assignment> { it.weekNo }.thenBy { it.orderInWeek })
            .collectList()
            .flatMapMany { assignments -> loadAssignmentSummaries(assignments, includeHidden = false) }
    }

    fun getAdminAssignments(
        courseSlug: String,
        weekNo: Int?,
        status: AssignmentStatus?,
    ): Flux<AssignmentSummaryResponse> {
        val slug = parseCourseSlug(courseSlug)
        val parsedWeekNo = weekNo?.let(::parseWeekNo)
        val now = assignmentPublicationPolicy.now()

        return findCourseBySlug(slug)
            .flatMapMany { course ->
                findAssignmentsByFilter(parseCourseId(requireNotNull(course.id)), parsedWeekNo)
            }
            .filter { assignment -> status == null || effectivePublication(assignment, now).status == status }
            .sort(compareBy<Assignment> { it.weekNo }.thenBy { it.orderInWeek })
            .collectList()
            .flatMapMany { assignments -> loadAssignmentSummaries(assignments, includeHidden = true, now = now) }
    }

    fun getAssignmentDetail(
        courseSlug: String,
        assignmentId: String,
        userId: String,
    ): Mono<AssignmentDetailResponse> {
        val slug = parseCourseSlug(courseSlug)
        val parsedAssignmentId = parseAssignmentId(assignmentId)
        val parsedUserId = parseUserId(userId)

        return findAccessibleCourseBySlug(slug, parsedUserId)
            .flatMap { course ->
                val courseId = parseCourseId(requireNotNull(course.id))
                assignmentQueryStore.findByIdAndCourseId(parsedAssignmentId.value, courseId.value)
                    .switchIfEmpty(assignmentNotFound(parsedAssignmentId))
                    .flatMap { assignment -> ensureVisibleToUser(assignment, parsedAssignmentId) }
                    .flatMap { assignment ->
                        loadAssignmentDetail(
                            courseSlug = course.slug,
                            assignment = assignment,
                            assignmentId = parsedAssignmentId,
                            includeHidden = false,
                        )
                    }
            }
    }

    fun getAdminAssignmentDetail(
        courseSlug: String,
        assignmentId: String,
    ): Mono<AssignmentDetailResponse> {
        val slug = parseCourseSlug(courseSlug)
        val parsedAssignmentId = parseAssignmentId(assignmentId)

        return findCourseBySlug(slug)
            .flatMap { course ->
                val courseId = parseCourseId(requireNotNull(course.id))
                assignmentQueryStore.findByIdAndCourseId(parsedAssignmentId.value, courseId.value)
                    .switchIfEmpty(assignmentNotFound(parsedAssignmentId))
                    .flatMap { assignment ->
                        loadAssignmentDetail(
                            courseSlug = course.slug,
                            assignment = assignment,
                            assignmentId = parsedAssignmentId,
                            includeHidden = true,
                        )
                    }
            }
    }

    private fun findAssignmentsByFilter(
        courseId: CourseId,
        weekNo: WeekNo?,
    ): Flux<Assignment> =
        if (weekNo == null) {
            assignmentQueryStore.findAllByCourseId(courseId.value)
        } else {
            assignmentQueryStore.findAllByCourseIdAndWeekNo(courseId.value, weekNo.value)
        }

    private fun findCourseBySlug(slug: CourseSlug): Mono<AssignmentCourseReference> =
        assignmentCourseQueryPort.findBySlug(slug)
            .switchIfEmpty(
                Mono.error(
                    ResponseStatusException(HttpStatus.NOT_FOUND, "코스를 찾을 수 없습니다: ${slug.value}")
                )
            )

    private fun findAccessibleCourseBySlug(
        slug: CourseSlug,
        userId: UserId,
    ): Mono<AssignmentCourseReference> =
        findCourseBySlug(slug)
            .flatMap { course ->
                val courseId = parseCourseId(requireNotNull(course.id))
                assignmentCourseQueryPort.isEnrollmentEnabled(courseId, userId)
                    .filter { enabled -> enabled }
                    .switchIfEmpty(
                        Mono.error(
                            ResponseStatusException(HttpStatus.NOT_FOUND, "조회 가능한 코스를 찾을 수 없습니다.")
                        )
                    )
                    .thenReturn(course)
            }

    private fun ensureVisibleToUser(
        assignment: Assignment,
        assignmentId: AssignmentId,
    ): Mono<Assignment> =
        if (isVisibleToUser(assignment)) {
            Mono.just(assignment)
        } else {
            assignmentNotFound(assignmentId)
        }

    private fun <T> assignmentNotFound(assignmentId: AssignmentId): Mono<T> =
        Mono.error(
            ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "과제를 찾을 수 없습니다: ${assignmentId.value}",
            )
        )

    private fun loadAssignmentDetail(
        courseSlug: String,
        assignment: Assignment,
        assignmentId: AssignmentId,
        includeHidden: Boolean,
    ): Mono<AssignmentDetailResponse> =
        assignmentRequirementRepository
            .findAllByAssignmentIdOrderBySortOrder(assignmentId.value)
            .map { AssignmentRequirementResponse(it.sortOrder, it.requirementText) }
            .collectList()
            .zipWith(
                assignmentTestCaseRepository
                    .findAllByAssignmentIdOrderBySeq(assignmentId.value)
                    .filter { includeHidden || it.visibility == AssignmentTestCaseVisibility.PUBLIC }
                    .map { AssignmentTestCaseResponse(it.seq, it.inputValues, it.outputText, it.visibility) }
                    .collectList()
            )
            .map { tuple ->
                toAssignmentDetailResponse(
                    courseSlug,
                    assignment,
                    effectiveAssignment(assignment),
                    tuple.t1,
                    tuple.t2,
                )
            }

    private fun loadAssignmentSummaries(
        assignments: List<Assignment>,
        includeHidden: Boolean,
        now: Instant = assignmentPublicationPolicy.now(),
    ): Flux<AssignmentSummaryResponse> {
        if (assignments.isEmpty()) {
            return Flux.empty()
        }

        val assignmentIds = assignments.map { assignment -> requireNotNull(assignment.id) }
        val requirementsByAssignment = assignmentRequirementRepository
            .findAllByAssignmentIdIn(assignmentIds)
            .collectList()
            .map { requirements ->
                requirements
                    .groupBy(AssignmentRequirement::assignmentId)
                    .mapValues { (_, items) -> items.sortedBy(AssignmentRequirement::sortOrder) }
            }
        val testCasesByAssignment = assignmentTestCaseRepository
            .findAllByAssignmentIdIn(assignmentIds)
            .filter { testCase -> includeHidden || testCase.visibility == AssignmentTestCaseVisibility.PUBLIC }
            .collectList()
            .map { testCases ->
                testCases
                    .groupBy(AssignmentTestCase::assignmentId)
                    .mapValues { (_, items) -> items.sortedBy(AssignmentTestCase::seq) }
            }

        return Mono.zip(requirementsByAssignment, testCasesByAssignment)
            .flatMapMany { tuple ->
                Flux.fromIterable(
                    assignments.map { assignment ->
                        val assignmentId = requireNotNull(assignment.id)
                        val requirements = tuple.t1[assignmentId].orEmpty()
                            .map { requirement ->
                                AssignmentRequirementResponse(
                                    requirement.sortOrder,
                                    requirement.requirementText,
                                )
                            }
                        val testCases = tuple.t2[assignmentId].orEmpty()
                            .map { testCase ->
                                AssignmentTestCaseResponse(
                                    testCase.seq,
                                    testCase.inputValues,
                                    testCase.outputText,
                                    testCase.visibility,
                                )
                            }
                        toAssignmentSummaryResponse(
                            assignment,
                            effectiveAssignment(assignment, now),
                            requirements,
                            testCases,
                        )
                    }
                )
            }
    }

    private fun resolveVisibleAssignmentStatus(status: AssignmentStatus?) {
        if (status != null && status != AssignmentStatus.PUBLISHED) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "사용자 조회는 PUBLISHED 상태만 지원합니다.")
        }
    }

    private fun parseCourseSlug(raw: String): CourseSlug =
        parseOrBadRequest { CourseSlug.from(raw) }

    private fun parseCourseId(raw: String): CourseId =
        parseOrBadRequest { CourseId.from(raw) }

    private fun parseUserId(raw: String): UserId =
        parseOrBadRequest { UserId.from(raw) }

    private fun parseWeekNo(raw: Int): WeekNo =
        parseOrBadRequest { WeekNo.from(raw) }

    private fun parseAssignmentId(raw: String): AssignmentId =
        parseOrBadRequest { AssignmentId.from(raw) }

    private fun <T> parseOrBadRequest(block: () -> T): T =
        runCatching(block).getOrElse { error ->
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, error.message ?: "잘못된 요청입니다.")
        }

    private fun isVisibleToUser(assignment: Assignment): Boolean =
        effectivePublication(assignment).status == AssignmentStatus.PUBLISHED

    private fun effectiveAssignment(
        assignment: Assignment,
        now: Instant = assignmentPublicationPolicy.now(),
    ): Assignment {
        val publication = effectivePublication(assignment, now)
        return assignment.copy(
            status = publication.status,
            publishedAt = publication.publishedAt,
        )
    }

    private fun effectivePublication(
        assignment: Assignment,
        now: Instant = assignmentPublicationPolicy.now(),
    ) = assignmentPublicationPolicy.resolve(
        status = assignment.status,
        startAt = assignment.startAt,
        publishedAt = assignment.publishedAt,
        now = now,
    )

    private fun toAssignmentSummaryResponse(
        assignment: Assignment,
        effectiveAssignment: Assignment,
        requirements: List<AssignmentRequirementResponse>,
        testCases: List<AssignmentTestCaseResponse>,
    ): AssignmentSummaryResponse = AssignmentSummaryResponse(
        id = requireNotNull(assignment.id),
        weekNo = assignment.weekNo,
        orderInWeek = assignment.orderInWeek,
        startAt = assignment.startAt,
        endAt = assignment.endAt,
        status = effectiveAssignment.status,
        publishedAt = effectiveAssignment.publishedAt,
        metadata = assignment.metadata.toResponse(requirements, testCases),
    )

    private fun toAssignmentDetailResponse(
        courseSlug: String,
        assignment: Assignment,
        effectiveAssignment: Assignment,
        requirements: List<AssignmentRequirementResponse>,
        testCases: List<AssignmentTestCaseResponse>,
    ): AssignmentDetailResponse = AssignmentDetailResponse(
        id = requireNotNull(assignment.id),
        courseSlug = courseSlug,
        weekNo = assignment.weekNo,
        orderInWeek = assignment.orderInWeek,
        startAt = assignment.startAt,
        endAt = assignment.endAt,
        status = effectiveAssignment.status,
        publishedAt = effectiveAssignment.publishedAt,
        metadata = assignment.metadata.toDetailResponse(requirements, testCases),
    )
}
