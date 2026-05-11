package com.example.aandi_post_web_server.assignment.application.service

import com.example.aandi_post_web_server.assignment.api.dto.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentRequirementResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentTestCaseResponse
import com.example.aandi_post_web_server.assignment.api.dto.CopyAssignmentRequest
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import com.example.aandi_post_web_server.assignment.domain.model.toDetailResponse
import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.entity.AssignmentRequirement
import com.example.aandi_post_web_server.assignment.entity.AssignmentTestCase
import com.example.aandi_post_web_server.assignment.infrastructure.event.AssignmentReportTestCaseEvent
import com.example.aandi_post_web_server.assignment.infrastructure.event.AssignmentReportTestCaseEventMapper
import com.example.aandi_post_web_server.assignment.infrastructure.event.AssignmentReportTestCaseEventPublisher
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentDeliveryRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRequirementRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentTestCaseRepository
import com.example.aandi_post_web_server.course.domain.model.AssignmentId
import com.example.aandi_post_web_server.course.domain.model.CourseId
import com.example.aandi_post_web_server.course.domain.model.CourseSlug
import com.example.aandi_post_web_server.course.domain.model.WeekNo
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseWeek
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseWeekRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

@Service
class AssignmentCopyService(
    private val courseRepository: CourseRepository,
    private val courseWeekRepository: CourseWeekRepository,
    private val assignmentRepository: AssignmentRepository,
    private val assignmentRequirementRepository: AssignmentRequirementRepository,
    private val assignmentTestCaseRepository: AssignmentTestCaseRepository,
    private val assignmentDeliveryRepository: AssignmentDeliveryRepository,
    private val assignmentReportTestCaseEventMapper: AssignmentReportTestCaseEventMapper,
    private val assignmentReportTestCaseEventPublisher: AssignmentReportTestCaseEventPublisher,
    private val assignmentCopyFingerprintCalculator: AssignmentCopyFingerprintCalculator,
) {
    private val log = LoggerFactory.getLogger(AssignmentCopyService::class.java)

    fun copyAssignment(
        targetCourseSlug: String,
        request: CopyAssignmentRequest,
        createdBy: String,
    ): Mono<AssignmentDetailResponse> {
        if (request.sourceAssignmentId.isBlank()) {
            return Mono.error(ResponseStatusException(HttpStatus.BAD_REQUEST, "sourceAssignmentId는 필수입니다."))
        }

        val slug = parseCourseSlug(targetCourseSlug)
        val sourceAssignmentId = parseSourceAssignmentId(request.sourceAssignmentId)
        return findCourseBySlug(slug)
            .flatMap { targetCourse ->
                val targetCourseId = parseCourseId(requireNotNull(targetCourse.id))
                assignmentRepository.findById(sourceAssignmentId.value)
                    .switchIfEmpty(
                        Mono.error(
                            ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "원본 과제를 찾을 수 없습니다: ${sourceAssignmentId.value}",
                            )
                        )
                    )
                    .flatMap { sourceAssignment ->
                        Mono.zip(
                            resolveAssignmentCourseSlug(sourceAssignment),
                            assignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder(sourceAssignmentId.value).collectList(),
                            assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(sourceAssignmentId.value).collectList(),
                        )
                            .flatMap { tuple ->
                                copyAssignmentInternal(
                                    targetCourse = targetCourse,
                                    targetCourseId = targetCourseId,
                                    sourceAssignment = sourceAssignment,
                                    sourceCourseSlug = tuple.t1,
                                    sourceRequirements = tuple.t2,
                                    sourceTestCases = tuple.t3,
                                    request = request,
                                    createdBy = createdBy,
                                )
                            }
                    }
            }
    }

    private fun copyAssignmentInternal(
        targetCourse: Course,
        targetCourseId: CourseId,
        sourceAssignment: Assignment,
        sourceCourseSlug: String,
        sourceRequirements: List<AssignmentRequirement>,
        sourceTestCases: List<AssignmentTestCase>,
        request: CopyAssignmentRequest,
        createdBy: String,
    ): Mono<AssignmentDetailResponse> {
        val targetWeekNo = parseTargetWeekNo(request.targetWeekNo ?: sourceAssignment.weekNo)
        val targetOrderInWeek = parseTargetOrderInWeek(request.targetOrderInWeek ?: sourceAssignment.orderInWeek)
        val targetStartAt = request.targetStartAt ?: sourceAssignment.startAt
        val targetEndAt = request.targetEndAt ?: sourceAssignment.endAt
        if (targetEndAt.isBefore(targetStartAt)) {
            return Mono.error(ResponseStatusException(HttpStatus.BAD_REQUEST, "과제 종료 시간은 시작 시간보다 빠를 수 없습니다."))
        }

        val sourceAssignmentId = requireNotNull(sourceAssignment.id)
        val originAssignmentId = sourceAssignment.originAssignmentId ?: sourceAssignmentId
        val originCourseSlug = sourceAssignment.originCourseSlug ?: sourceCourseSlug
        val copyFingerprint = assignmentCopyFingerprintCalculator.calculate(sourceAssignment, sourceRequirements, sourceTestCases)
        val copiedAssignment = createCopiedAssignmentEntity(
            targetCourseId = targetCourseId.value,
            targetCourseSlug = targetCourse.slug,
            createdBy = createdBy,
            sourceAssignment = sourceAssignment,
            weekNo = targetWeekNo.value,
            orderInWeek = targetOrderInWeek,
            startAt = targetStartAt,
            endAt = targetEndAt,
            originAssignmentId = originAssignmentId,
            originCourseSlug = originCourseSlug,
            copyFingerprint = copyFingerprint,
        )

        return ensureNoOriginAssignmentDuplicate(targetCourseId, originAssignmentId, sourceAssignmentId, targetCourse.slug)
            .then(ensureNoCopyFingerprintDuplicate(targetCourseId, copyFingerprint, sourceAssignmentId, targetCourse.slug))
            .then(ensureAssignmentSlotAvailableForCreate(targetCourseId, targetWeekNo.value, targetOrderInWeek))
            .then(
                Mono.defer {
                    ensureWeekExistsOrCreate(
                        courseId = targetCourseId,
                        weekNo = targetWeekNo,
                        startAt = targetStartAt,
                        endAt = targetEndAt,
                    )
                }
            )
            .then(Mono.defer { assignmentRepository.save(copiedAssignment) })
            .onErrorMap(DuplicateKeyException::class.java) { duplicateAssignmentCopyConflict() }
            .flatMap { saved ->
                val savedAssignmentId = parseAssignmentId(requireNotNull(saved.id))
                Mono.zip(
                    copyRequirements(savedAssignmentId, sourceRequirements),
                    copyTestCases(savedAssignmentId, sourceTestCases),
                )
                    .onErrorResume { error ->
                        cleanupCopiedAssignment(savedAssignmentId.value)
                            .then(Mono.error(mapDuplicateAssignmentCopyConflict(error)))
                    }
                    .flatMap { tuple ->
                        val response = toAssignmentDetailResponse(
                            courseSlug = targetCourse.slug,
                            assignment = saved,
                            requirements = tuple.t1,
                            testCases = tuple.t2,
                        )
                        publishProblemSyncOnCreate(saved)
                            .thenReturn(response)
                    }
            }
    }

    private fun createCopiedAssignmentEntity(
        targetCourseId: String,
        targetCourseSlug: String,
        createdBy: String,
        sourceAssignment: Assignment,
        weekNo: Int,
        orderInWeek: Int,
        startAt: Instant,
        endAt: Instant,
        originAssignmentId: String,
        originCourseSlug: String,
        copyFingerprint: String,
    ): Assignment {
        val now = Instant.now()
        return Assignment(
            id = UUID.randomUUID().toString(),
            courseId = targetCourseId,
            courseSlug = targetCourseSlug,
            createdBy = createdBy,
            weekNo = weekNo,
            orderInWeek = orderInWeek,
            startAt = startAt,
            endAt = endAt,
            metadata = sourceAssignment.metadata.copy(
                learningGoals = sourceAssignment.metadata.learningGoals.toList(),
                codeTemplates = sourceAssignment.metadata.codeTemplates.map { it.copy() },
            ),
            status = AssignmentStatus.DRAFT,
            createdAt = now,
            updatedAt = now,
            publishedAt = null,
            originAssignmentId = originAssignmentId,
            originCourseSlug = originCourseSlug,
            copyFingerprint = copyFingerprint,
        )
    }

    private fun copyRequirements(
        assignmentId: AssignmentId,
        sourceRequirements: List<AssignmentRequirement>,
    ): Mono<List<AssignmentRequirementResponse>> {
        val sorted = sourceRequirements.sortedWith(compareBy<AssignmentRequirement> { it.sortOrder }.thenBy { it.requirementText })
        if (sorted.isEmpty()) return Mono.just(emptyList())

        val now = Instant.now()
        return assignmentRequirementRepository.saveAll(
            sorted.map {
                AssignmentRequirement(
                    assignmentId = assignmentId.value,
                    sortOrder = it.sortOrder,
                    requirementText = it.requirementText,
                    createdAt = now,
                )
            }
        )
            .sort(compareBy<AssignmentRequirement> { it.sortOrder })
            .map { AssignmentRequirementResponse(it.sortOrder, it.requirementText) }
            .collectList()
    }

    private fun copyTestCases(
        assignmentId: AssignmentId,
        sourceTestCases: List<AssignmentTestCase>,
    ): Mono<List<AssignmentTestCaseResponse>> {
        val sorted = sourceTestCases.sortedWith(
            compareBy<AssignmentTestCase> { it.seq }
                .thenBy { it.inputValues.joinToString("\u001F") }
                .thenBy { it.outputText }
                .thenBy { it.visibility.name }
        )
        if (sorted.isEmpty()) return Mono.just(emptyList())

        val now = Instant.now()
        return assignmentTestCaseRepository.saveAll(
            sorted.map {
                AssignmentTestCase(
                    assignmentId = assignmentId.value,
                    seq = it.seq,
                    inputValues = it.inputValues.toList(),
                    outputText = it.outputText,
                    visibility = it.visibility,
                    description = it.description,
                    createdAt = now,
                )
            }
        )
            .sort(compareBy<AssignmentTestCase> { it.seq })
            .map { AssignmentTestCaseResponse(it.seq, it.inputValues, it.outputText, it.visibility) }
            .collectList()
    }

    private fun ensureNoOriginAssignmentDuplicate(
        targetCourseId: CourseId,
        originAssignmentId: String,
        sourceAssignmentId: String,
        targetCourseSlug: String,
    ): Mono<Void> {
        return assignmentRepository.findByCourseIdAndOriginAssignmentId(targetCourseId.value, originAssignmentId)
            .switchIfEmpty(assignmentRepository.findByIdAndCourseId(originAssignmentId, targetCourseId.value))
            .flatMap<Assignment> { existing ->
                Mono.error(
                    ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "이미 대상 코스에 동일한 원본 과제가 존재합니다.",
                    ).also {
                        log.info(
                            "Duplicate assignment copy by origin. existingAssignmentId={}, sourceAssignmentId={}, targetCourseSlug={}",
                            existing.id,
                            sourceAssignmentId,
                            targetCourseSlug,
                        )
                    }
                )
            }
            .then()
    }

    private fun ensureNoCopyFingerprintDuplicate(
        targetCourseId: CourseId,
        copyFingerprint: String,
        sourceAssignmentId: String,
        targetCourseSlug: String,
    ): Mono<Void> {
        return assignmentRepository.findByCourseIdAndCopyFingerprint(targetCourseId.value, copyFingerprint)
            .flatMap<Assignment> { existing ->
                Mono.error(
                    ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "이미 대상 코스에 동일한 내용의 과제가 존재합니다.",
                    ).also {
                        log.info(
                            "Duplicate assignment copy by fingerprint. existingAssignmentId={}, sourceAssignmentId={}, targetCourseSlug={}",
                            existing.id,
                            sourceAssignmentId,
                            targetCourseSlug,
                        )
                    }
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

    private fun ensureWeekExistsOrCreate(
        courseId: CourseId,
        weekNo: WeekNo,
        startAt: Instant,
        endAt: Instant,
    ): Mono<Void> {
        return courseWeekRepository.findByCourseIdAndWeekNo(courseId.value, weekNo.value)
            .switchIfEmpty(
                Mono.defer {
                    courseWeekRepository.save(
                        CourseWeek(
                            courseId = courseId.value,
                            weekNo = weekNo.value,
                            title = "${weekNo.value}주차",
                            startDate = startAt.atZone(java.time.ZoneId.of("Asia/Seoul")).toLocalDate(),
                            endDate = endAt.atZone(java.time.ZoneId.of("Asia/Seoul")).toLocalDate(),
                            createdAt = Instant.now(),
                            updatedAt = Instant.now(),
                        )
                    )
                }
            )
            .then()
    }

    private fun cleanupCopiedAssignment(assignmentId: String): Mono<Void> =
        deleteCopiedAssignmentDocuments(assignmentId)
            .onErrorResume { cleanupError ->
                log.warn("Failed to cleanup copied assignment after copy failure. assignmentId={}", assignmentId, cleanupError)
                Mono.empty()
            }

    private fun deleteCopiedAssignmentDocuments(assignmentId: String): Mono<Void> =
        Mono.whenDelayError(
            assignmentRequirementRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)).then(),
            assignmentTestCaseRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)).then(),
            assignmentDeliveryRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)).then(),
            assignmentRepository.deleteById(assignmentId).then(),
        ).then()

    private fun resolveAssignmentCourseSlug(assignment: Assignment): Mono<String> {
        val fallbackSlug = assignment.courseSlug.takeIf { it.isNotBlank() }
        return courseRepository.findById(assignment.courseId)
            .map { it.slug }
            .switchIfEmpty(
                fallbackSlug?.let { Mono.just(it) }
                    ?: Mono.error(
                        ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "원본 과제의 코스를 찾을 수 없습니다: ${assignment.courseId}",
                        )
                    )
            )
    }

    private fun publishProblemSyncOnCreate(assignment: Assignment): Mono<Void> {
        val assignmentId = requireNotNull(assignment.id)
        return loadAssignmentProblemSyncSnapshot(assignmentId)
            .flatMap { (snapshotAssignment, testCases) ->
                publishProblemSyncEvent(assignmentReportTestCaseEventMapper.created(snapshotAssignment, testCases))
            }
    }

    private fun loadAssignmentProblemSyncSnapshot(
        assignmentId: String,
    ): Mono<Pair<Assignment, List<AssignmentTestCaseResponse>>> =
        assignmentRepository.findById(assignmentId)
            .switchIfEmpty(
                Mono.error(
                    IllegalStateException("problem sync 대상 assignment snapshot을 찾을 수 없습니다: $assignmentId")
                )
            )
            .zipWith(
                assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(assignmentId)
                    .map { AssignmentTestCaseResponse(it.seq, it.inputValues, it.outputText, it.visibility) }
                    .collectList()
            )
            .map { it.t1 to it.t2 }

    private fun publishProblemSyncEvent(event: AssignmentReportTestCaseEvent): Mono<Void> {
        log.info(
            "Publishing assignment problem sync event. eventType={}, problemId={}, testCaseCount={}, caseIds={}",
            event.eventType,
            event.problemId,
            event.testCases.size,
            event.testCases.map { it.caseId },
        )
        return assignmentReportTestCaseEventPublisher.publish(event)
    }

    private fun toAssignmentDetailResponse(
        courseSlug: String,
        assignment: Assignment,
        requirements: List<AssignmentRequirementResponse>,
        testCases: List<AssignmentTestCaseResponse>,
    ): AssignmentDetailResponse = AssignmentDetailResponse(
        id = requireNotNull(assignment.id),
        courseSlug = courseSlug,
        weekNo = assignment.weekNo,
        orderInWeek = assignment.orderInWeek,
        startAt = assignment.startAt,
        endAt = assignment.endAt,
        status = effectiveResponseStatus(assignment),
        publishedAt = effectiveResponsePublishedAt(assignment),
        metadata = assignment.metadata.toDetailResponse(requirements, testCases),
    )

    private fun effectiveResponseStatus(
        assignment: Assignment,
        now: Instant = Instant.now(),
    ): AssignmentStatus {
        if (assignment.status != AssignmentStatus.PUBLISHED) {
            return assignment.status
        }
        return if (now >= assignment.startAt) AssignmentStatus.PUBLISHED else AssignmentStatus.DRAFT
    }

    private fun effectiveResponsePublishedAt(
        assignment: Assignment,
        now: Instant = Instant.now(),
    ): Instant? =
        if (effectiveResponseStatus(assignment, now) == AssignmentStatus.PUBLISHED) assignment.publishedAt else null

    private fun findCourseBySlug(slug: CourseSlug): Mono<Course> {
        return courseRepository.findBySlug(slug.value)
            .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "코스를 찾을 수 없습니다: ${slug.value}")))
    }

    private fun parseCourseSlug(raw: String): CourseSlug =
        parseOrBadRequest { CourseSlug.from(raw) }

    private fun parseCourseId(raw: String): CourseId =
        parseOrBadRequest { CourseId.from(raw) }

    private fun parseAssignmentId(raw: String): AssignmentId =
        parseOrBadRequest { AssignmentId.from(raw) }

    private fun parseTargetWeekNo(raw: Int): WeekNo =
        parseOrBadRequest {
            require(raw > 0) { "targetWeekNo는 1 이상이어야 합니다." }
            WeekNo.from(raw)
        }

    private fun parseTargetOrderInWeek(raw: Int): Int =
        parseOrBadRequest {
            require(raw > 0) { "targetOrderInWeek는 1 이상이어야 합니다." }
            raw
        }

    private fun parseSourceAssignmentId(raw: String): AssignmentId =
        parseOrBadRequest {
            val normalized = raw.trim()
            require(normalized.isNotBlank()) { "sourceAssignmentId는 필수입니다." }
            require(runCatching { UUID.fromString(normalized) }.isSuccess) { "sourceAssignmentId는 UUID 형식이어야 합니다." }
            AssignmentId.from(normalized)
        }

    private fun mapDuplicateAssignmentCopyConflict(error: Throwable): Throwable {
        if (error is DuplicateKeyException) {
            return duplicateAssignmentCopyConflict()
        }
        return error
    }

    private fun duplicateAssignmentCopyConflict(): ResponseStatusException =
        ResponseStatusException(
            HttpStatus.CONFLICT,
            "이미 대상 코스에 동일한 원본 또는 동일한 내용의 과제가 존재합니다.",
        )

    private fun <T> parseOrBadRequest(block: () -> T): T {
        return runCatching(block).getOrElse { error ->
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, error.message ?: "잘못된 요청입니다.")
        }
    }
}
