package com.example.aandi_post_web_server.assignment.application.service

import com.example.aandi_post_web_server.assignment.application.mapper.toDetailResponse
import com.example.aandi_post_web_server.assignment.application.mapper.toEntity
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseDrafts
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentPublicationPolicy
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentRequirementDrafts
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentRequirementResponse
import com.example.aandi_post_web_server.assignment.api.dto.CopyAssignmentRequest
import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentRequest
import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentRequirementRequest
import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentTestCaseRequest
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentTestCaseResponse
import com.example.aandi_post_web_server.assignment.api.dto.UpdateAssignmentRequest
import com.example.aandi_post_web_server.assignment.application.port.AssignmentCoursePort
import com.example.aandi_post_web_server.assignment.application.port.AssignmentCourseReference
import com.example.aandi_post_web_server.assignment.application.port.AssignmentProblemSyncPort
import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.entity.AssignmentRequirement
import com.example.aandi_post_web_server.assignment.entity.AssignmentTestCase
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentDeliveryRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRequirementRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentTestCaseRepository
import com.example.aandi_post_web_server.course.domain.model.AssignmentId
import com.example.aandi_post_web_server.course.domain.model.CourseId
import com.example.aandi_post_web_server.course.domain.model.CourseSlug
import com.example.aandi_post_web_server.course.domain.model.WeekNo
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

@Service
class AssignmentCommandService(
    private val assignmentCoursePort: AssignmentCoursePort,
    private val assignmentRepository: AssignmentRepository,
    private val assignmentRequirementRepository: AssignmentRequirementRepository,
    private val assignmentTestCaseRepository: AssignmentTestCaseRepository,
    private val assignmentDeliveryRepository: AssignmentDeliveryRepository,
    private val assignmentProblemSyncPort: AssignmentProblemSyncPort,
    private val assignmentCopyService: AssignmentCopyService,
    private val assignmentCommandRequestResolver: AssignmentCommandRequestResolver,
    private val assignmentPublicationPolicy: AssignmentPublicationPolicy = AssignmentPublicationPolicy(),
) {
    private val log = LoggerFactory.getLogger(AssignmentCommandService::class.java)

    fun createAssignment(
        courseSlug: String,
        request: CreateAssignmentRequest,
        createdBy: String,
    ): Mono<AssignmentDetailResponse> {
        val slug = parseCourseSlug(courseSlug)
        val weekNo = parseWeekNo(request.weekNo)
        if (request.endAt.isBefore(request.startAt)) {
            return Mono.error(ResponseStatusException(HttpStatus.BAD_REQUEST, "endAt은 startAt보다 빠를 수 없습니다."))
        }
        return assignmentCommandRequestResolver.resolveCreateRequest(request)
            .flatMap { resolvedRequest ->
                val requirementDrafts = parseRequirementDrafts(resolvedRequest.metadata.requirements)
                val testCaseDrafts = parseTestCaseDrafts(resolvedRequest.metadata.testCases)

                findCourseBySlug(slug)
                    .flatMap { course ->
                        val courseId = parseCourseId(requireNotNull(course.id))
                        ensureAssignmentSlotAvailableForCreate(courseId, weekNo.value, resolvedRequest.orderInWeek)
                            .then(
                                Mono.defer {
                                    assignmentCoursePort.ensureWeekExistsOrCreate(
                                        courseId = courseId,
                                        weekNo = weekNo,
                                        startAt = resolvedRequest.startAt,
                                        endAt = resolvedRequest.endAt,
                                    )
                                }
                            )
                            .then(
                                Mono.defer {
                                    assignmentRepository.save(
                                        createAssignmentEntity(
                                            courseId = courseId.value,
                                            courseSlug = course.slug,
                                            createdBy = createdBy,
                                            weekNo = weekNo.value,
                                            orderInWeek = resolvedRequest.orderInWeek,
                                            startAt = resolvedRequest.startAt,
                                            endAt = resolvedRequest.endAt,
                                            metadata = resolvedRequest.metadata.toEntity(),
                                        )
                                    )
                                }
                                    .onErrorMap(DuplicateKeyException::class.java) { duplicateAssignmentSlotConflict() }
                            )
                            .flatMap { assignment ->
                                val assignmentId = parseAssignmentId(requireNotNull(assignment.id))
                                saveRequirements(assignmentId, requirementDrafts)
                                    .flatMap { requirements ->
                                        saveTestCases(assignmentId, testCaseDrafts)
                                            .flatMap { testCases ->
                                                val response = toAssignmentDetailResponse(
                                                    courseSlug = course.slug,
                                                    assignment = assignment,
                                                    requirements = requirements,
                                                    testCases = testCases,
                                                )
                                                logAssignmentCreatedEvents(assignment)
                                                publishProblemSyncOnCreate(assignment)
                                                    .thenReturn(response)
                                            }
                                    }
                            }
                    }
            }
    }

    fun copyAssignment(
        targetCourseSlug: String,
        request: CopyAssignmentRequest,
        createdBy: String,
    ): Mono<AssignmentDetailResponse> =
        assignmentCopyService.copyAssignment(targetCourseSlug, request, createdBy)

    fun updateAssignment(
        courseSlug: String,
        assignmentId: String,
        request: UpdateAssignmentRequest,
    ): Mono<AssignmentDetailResponse> {
        val slug = parseCourseSlug(courseSlug)
        val parsedAssignmentId = parseAssignmentId(assignmentId)
        val parsedWeekNo = request.weekNo?.let { parseWeekNo(it) }
        val replaceTestCases = request.metadata?.let(assignmentCommandRequestResolver::shouldReplaceTestCases) ?: false

        return assignmentCommandRequestResolver.resolveUpdateRequest(request, replaceTestCases)
            .flatMap { resolvedRequest ->
                val requirementDrafts = resolvedRequest.metadata?.let { parseRequirementDrafts(it.requirements) }
                val testCaseDrafts = if (replaceTestCases) {
                    resolvedRequest.metadata?.let { parseTestCaseDrafts(it.testCases) }
                } else {
                    null
                }

                findCourseBySlug(slug)
                    .flatMap { course ->
                        val courseId = parseCourseId(requireNotNull(course.id))
                        assignmentRepository.findByIdAndCourseId(parsedAssignmentId.value, courseId.value)
                            .switchIfEmpty(
                                Mono.error(
                                    ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "과제를 찾을 수 없습니다: ${parsedAssignmentId.value}",
                                    )
                                )
                            )
                            .flatMap { assignment ->
                                updateAssignmentInternal(
                                    course = course,
                                    courseId = courseId,
                                    assignment = assignment,
                                    parsedAssignmentId = parsedAssignmentId,
                                    request = resolvedRequest,
                                    parsedWeekNo = parsedWeekNo,
                                    requirementDrafts = requirementDrafts,
                                    testCaseDrafts = testCaseDrafts,
                                )
                            }
                    }
            }
    }

    fun deleteAssignment(courseSlug: String, assignmentId: String): Mono<Void> {
        val slug = parseCourseSlug(courseSlug)
        val parsedAssignmentId = parseAssignmentId(assignmentId)
        return findCourseBySlug(slug)
            .flatMap { course ->
                val courseId = parseCourseId(requireNotNull(course.id))
                assignmentRepository.findByIdAndCourseId(parsedAssignmentId.value, courseId.value)
                    .switchIfEmpty(
                        Mono.error(
                            ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "과제를 찾을 수 없습니다: ${parsedAssignmentId.value}",
                            )
                        )
                    )
                    .flatMap { assignment ->
                        deleteAssignmentCascade(parsedAssignmentId.value)
                            .doOnSuccess {
                                logAssignmentReportEvent(
                                    AssignmentReportEventType.ASSIGNMENT_DELETED,
                                    assignment,
                                )
                            }
                    }
            }
            .then()
    }

    private fun createAssignmentEntity(
        courseId: String,
        courseSlug: String,
        createdBy: String,
        weekNo: Int,
        orderInWeek: Int,
        startAt: Instant,
        endAt: Instant,
        metadata: com.example.aandi_post_web_server.assignment.entity.AssignmentMetadata,
    ): Assignment {
        val now = assignmentPublicationPolicy.now()
        return Assignment(
            id = UUID.randomUUID().toString(),
            courseId = courseId,
            courseSlug = courseSlug,
            createdBy = createdBy,
            weekNo = weekNo,
            orderInWeek = orderInWeek,
            startAt = startAt,
            endAt = endAt,
            metadata = metadata,
            status = AssignmentStatus.PUBLISHED,
            createdAt = now,
            updatedAt = now,
            publishedAt = assignmentPublicationPolicy.initialPublishedAt(startAt, now),
        )
    }

    private fun saveRequirements(
        assignmentId: AssignmentId,
        drafts: AssignmentRequirementDrafts,
    ): Mono<List<AssignmentRequirementResponse>> {
        if (drafts.isEmpty()) return Mono.just(emptyList())

        return assignmentRequirementRepository.saveAll(
            drafts.toEntities(assignmentId.value, Instant.now())
        )
            .sort(compareBy<AssignmentRequirement> { it.sortOrder })
            .map { AssignmentRequirementResponse(it.sortOrder, it.requirementText) }
            .collectList()
    }

    private fun saveTestCases(
        assignmentId: AssignmentId,
        drafts: AssignmentTestCaseDrafts,
    ): Mono<List<AssignmentTestCaseResponse>> {
        if (drafts.isEmpty()) return Mono.just(emptyList())

        return assignmentTestCaseRepository.saveAll(
            drafts.toEntities(assignmentId.value, Instant.now())
        )
            .sort(compareBy<AssignmentTestCase> { it.seq })
            .map { AssignmentTestCaseResponse(it.seq, it.inputValues, it.outputText, it.visibility) }
            .collectList()
    }

    fun deleteAllByCourseId(courseId: String): Mono<Void> {
        return assignmentRepository.findAllByCourseId(courseId)
            .collectList()
            .flatMap { assignments ->
                val deletableAssignments = assignments.filter { it.id != null }
                val assignmentIds = deletableAssignments.map { requireNotNull(it.id) }
                if (assignmentIds.isEmpty()) {
                    Mono.empty<Void>()
                } else {
                    Flux.concatDelayError(
                        assignmentRequirementRepository.deleteAllByAssignmentIdIn(assignmentIds).then(),
                        assignmentTestCaseRepository.deleteAllByAssignmentIdIn(assignmentIds).then(),
                        assignmentDeliveryRepository.deleteAllByAssignmentIdIn(assignmentIds).then(),
                        assignmentRepository.deleteAllById(assignmentIds).then()
                    ).then(
                        Flux.fromIterable(deletableAssignments)
                            .concatMap { assignment ->
                                val assignmentId = requireNotNull(assignment.id)
                                publishDeletedTestCases(assignmentId)
                                    .doOnSuccess {
                                        logAssignmentReportEvent(
                                            AssignmentReportEventType.ASSIGNMENT_DELETED,
                                            assignment,
                                        )
                                    }
                            }
                            .then()
                    )
                }
            }
    }

    private fun updateAssignmentInternal(
        course: AssignmentCourseReference,
        courseId: CourseId,
        assignment: Assignment,
        parsedAssignmentId: AssignmentId,
        request: UpdateAssignmentRequest,
        parsedWeekNo: WeekNo?,
        requirementDrafts: AssignmentRequirementDrafts?,
        testCaseDrafts: AssignmentTestCaseDrafts?,
    ): Mono<AssignmentDetailResponse> {
        val targetWeekNo = parsedWeekNo?.value ?: assignment.weekNo
        val targetOrderInWeek = request.orderInWeek ?: assignment.orderInWeek
        val targetStartAt = request.startAt ?: assignment.startAt
        val targetEndAt = request.endAt ?: assignment.endAt
        if (targetEndAt.isBefore(targetStartAt)) {
            return Mono.error(ResponseStatusException(HttpStatus.BAD_REQUEST, "endAt은 startAt보다 빠를 수 없습니다."))
        }

        val metadata = request.metadata?.toEntity() ?: assignment.metadata
        val now = assignmentPublicationPolicy.now()

        val candidate = assignment.copy(
            weekNo = targetWeekNo,
            orderInWeek = targetOrderInWeek,
            startAt = targetStartAt,
            endAt = targetEndAt,
            metadata = metadata,
            status = AssignmentStatus.PUBLISHED,
            updatedAt = now,
            publishedAt = assignment.publishedAt ?: assignmentPublicationPolicy.initialPublishedAt(targetStartAt, now),
        )

        val checkDuplicate = ensureAssignmentSlotAvailable(courseId, candidate, parsedAssignmentId)
        val checkWeek = Mono.defer {
            assignmentCoursePort.ensureWeekExistsOrCreate(
                courseId = courseId,
                weekNo = parseWeekNo(targetWeekNo),
                startAt = targetStartAt,
                endAt = targetEndAt,
            )
        }
        return checkDuplicate
            .then(checkWeek)
            .then(
                Mono.defer { assignmentRepository.save(candidate) }
                    .onErrorMap(DuplicateKeyException::class.java) { duplicateAssignmentSlotConflict() }
            )
            .flatMap { saved ->
                loadOrReplaceRequirements(parsedAssignmentId, requirementDrafts)
                    .flatMap { requirements ->
                        loadOrReplaceTestCases(parsedAssignmentId, testCaseDrafts)
                            .flatMap { testCases ->
                                val response = toAssignmentDetailResponse(course.slug, saved, requirements, testCases)
                                logAssignmentUpdatedEvents(previous = assignment, current = saved)
                                publishProblemSyncOnUpdate(saved)
                                    .thenReturn(response)
                            }
                    }
            }
    }

    private fun loadOrReplaceRequirements(
        assignmentId: AssignmentId,
        drafts: AssignmentRequirementDrafts?,
    ): Mono<List<AssignmentRequirementResponse>> {
        if (drafts == null) {
            return assignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder(assignmentId.value)
                .map { AssignmentRequirementResponse(it.sortOrder, it.requirementText) }
                .collectList()
        }
        return assignmentRequirementRepository.deleteAllByAssignmentIdIn(listOf(assignmentId.value))
            .then(saveRequirements(assignmentId, drafts))
    }

    private fun loadOrReplaceTestCases(
        assignmentId: AssignmentId,
        drafts: AssignmentTestCaseDrafts?,
    ): Mono<List<AssignmentTestCaseResponse>> {
        if (drafts == null) {
            return assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(assignmentId.value)
                .map { AssignmentTestCaseResponse(it.seq, it.inputValues, it.outputText, it.visibility) }
                .collectList()
        }
        return assignmentTestCaseRepository.deleteAllByAssignmentIdIn(listOf(assignmentId.value))
            .then(saveTestCases(assignmentId, drafts))
    }

    private fun ensureAssignmentSlotAvailable(
        courseId: CourseId,
        assignment: Assignment,
        assignmentId: AssignmentId,
    ): Mono<Void> {
        return assignmentRepository.findByCourseIdAndWeekNoAndOrderInWeek(
            courseId.value,
            assignment.weekNo,
            assignment.orderInWeek,
        )
            .flatMap { duplicated ->
                if (duplicated.id == assignmentId.value) {
                    return@flatMap Mono.empty<Void>()
                }
                Mono.error(
                    ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "동일 코스/주차/순번 과제가 이미 존재합니다.",
                    )
                )
            }
            .then()
    }

    private fun ensureAssignmentSlotAvailableForCreate(
        courseId: CourseId,
        weekNo: Int,
        orderInWeek: Int,
    ): Mono<Void> {
        return assignmentRepository.findByCourseIdAndWeekNoAndOrderInWeek(courseId.value, weekNo, orderInWeek)
            .flatMap<Assignment> {
                Mono.error(
                    ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "동일 코스/주차/순번 과제가 이미 존재합니다.",
                    )
                )
            }
            .then()
    }

    private fun duplicateAssignmentSlotConflict(): ResponseStatusException =
        ResponseStatusException(
            HttpStatus.CONFLICT,
            "동일 코스/주차/순번 과제가 이미 존재합니다.",
        )

    private fun deleteAssignmentCascade(assignmentId: String): Mono<Void> {
        return Flux.concatDelayError(
            assignmentRequirementRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)).then(),
            assignmentTestCaseRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)).then(),
            assignmentDeliveryRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)).then(),
            assignmentRepository.deleteById(assignmentId).then(),
        ).then(publishDeletedTestCases(assignmentId))
    }

    private fun publishProblemSyncOnCreate(assignment: Assignment): Mono<Void> =
        assignmentProblemSyncPort.publishCreated(requireNotNull(assignment.id))

    private fun publishProblemSyncOnUpdate(
        currentAssignment: Assignment,
    ): Mono<Void> =
        assignmentProblemSyncPort.publishUpdated(requireNotNull(currentAssignment.id))

    private fun publishDeletedTestCases(assignmentId: String): Mono<Void> =
        Mono.defer { assignmentProblemSyncPort.publishDeleted(assignmentId) }

    private fun logAssignmentCreatedEvents(assignment: Assignment) {
        val now = assignmentPublicationPolicy.now()
        val publication = effectivePublication(assignment, now)
        logAssignmentReportEvent(AssignmentReportEventType.ASSIGNMENT_CREATED, assignment, now)
        if (publication.status == AssignmentStatus.PUBLISHED) {
            logAssignmentReportEvent(AssignmentReportEventType.ASSIGNMENT_PUBLISHED, assignment, now)
        }
    }

    private fun logAssignmentUpdatedEvents(previous: Assignment, current: Assignment) {
        val now = assignmentPublicationPolicy.now()
        val previousStatus = effectivePublication(previous, now).status
        val currentStatus = effectivePublication(current, now).status
        logAssignmentReportEvent(AssignmentReportEventType.ASSIGNMENT_UPDATED, current, now)
        when {
            previousStatus != AssignmentStatus.PUBLISHED && currentStatus == AssignmentStatus.PUBLISHED ->
                logAssignmentReportEvent(AssignmentReportEventType.ASSIGNMENT_PUBLISHED, current, now)
            previousStatus == AssignmentStatus.PUBLISHED && currentStatus != AssignmentStatus.PUBLISHED ->
                logAssignmentReportEvent(AssignmentReportEventType.ASSIGNMENT_UNPUBLISHED, current, now)
        }
    }

    private fun logAssignmentReportEvent(
        eventType: AssignmentReportEventType,
        assignment: Assignment,
        now: Instant = assignmentPublicationPolicy.now(),
    ) {
        val publication = effectivePublication(assignment, now)
        val payload = AssignmentReportEventPayload.from(
            eventType = eventType,
            assignment = assignment,
            status = publication.status,
            publishedAt = assignment.publishedAt ?: publication.publishedAt,
        )
        log.info("Report EVENT payload={}", payload)
    }

    private fun findCourseBySlug(slug: CourseSlug): Mono<AssignmentCourseReference> {
        return assignmentCoursePort.findBySlug(slug)
            .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "코스를 찾을 수 없습니다: ${slug.value}")))
    }

    private fun parseCourseSlug(raw: String): CourseSlug =
        parseOrBadRequest { CourseSlug.from(raw) }

    private fun parseCourseId(raw: String): CourseId =
        parseOrBadRequest { CourseId.from(raw) }

    private fun parseWeekNo(raw: Int): WeekNo =
        parseOrBadRequest { WeekNo.from(raw) }

    private fun parseAssignmentId(raw: String): AssignmentId =
        parseOrBadRequest { AssignmentId.from(raw) }

    private fun parseRequirementDrafts(requests: List<CreateAssignmentRequirementRequest>): AssignmentRequirementDrafts =
        parseOrBadRequest { AssignmentRequirementDrafts.fromRequests(requests) }

    private fun parseTestCaseDrafts(requests: List<CreateAssignmentTestCaseRequest>): AssignmentTestCaseDrafts =
        parseOrBadRequest { AssignmentTestCaseDrafts.fromRequests(requests) }

    private fun <T> parseOrBadRequest(block: () -> T): T {
        return runCatching(block).getOrElse { error ->
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, error.message ?: "잘못된 요청입니다.")
        }
    }

    private fun toAssignmentDetailResponse(
        courseSlug: String,
        assignment: Assignment,
        requirements: List<AssignmentRequirementResponse>,
        testCases: List<AssignmentTestCaseResponse>,
    ): AssignmentDetailResponse {
        val publication = effectivePublication(assignment)
        return AssignmentDetailResponse(
            id = requireNotNull(assignment.id),
            courseSlug = courseSlug,
            weekNo = assignment.weekNo,
            orderInWeek = assignment.orderInWeek,
            startAt = assignment.startAt,
            endAt = assignment.endAt,
            status = publication.status,
            publishedAt = publication.publishedAt,
            metadata = assignment.metadata.toDetailResponse(requirements, testCases),
        )
    }

    private fun effectivePublication(assignment: Assignment, now: Instant = assignmentPublicationPolicy.now()) =
        assignmentPublicationPolicy.resolve(
            status = assignment.status,
            startAt = assignment.startAt,
            publishedAt = assignment.publishedAt,
            now = now,
        )

}
