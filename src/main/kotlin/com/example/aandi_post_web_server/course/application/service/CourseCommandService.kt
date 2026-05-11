package com.example.aandi_post_web_server.course.application.service

import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseValidator
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseDrafts
import com.example.aandi_post_web_server.assignment.domain.model.toDetailResponse
import com.example.aandi_post_web_server.assignment.domain.model.toEntity
import com.example.aandi_post_web_server.assignment.domain.model.toResponse
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentRequirementDrafts
import com.example.aandi_post_web_server.assignment.infrastructure.jackson.AssignmentMetadataPayloadTestCasePresenceTracker
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentRequirementResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentMetadataPayload
import com.example.aandi_post_web_server.assignment.api.dto.CopyAssignmentRequest
import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentRequest
import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentRequirementRequest
import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentTestCaseRequest
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentTestCaseResponse
import com.example.aandi_post_web_server.assignment.api.dto.UpdateAssignmentRequest
import com.example.aandi_post_web_server.assignment.infrastructure.event.AssignmentReportTestCaseEvent
import com.example.aandi_post_web_server.assignment.infrastructure.event.AssignmentReportTestCaseEventMapper
import com.example.aandi_post_web_server.assignment.infrastructure.event.AssignmentReportTestCaseEventPublisher
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
import com.example.aandi_post_web_server.course.api.dto.CourseEnrollmentResponse
import com.example.aandi_post_web_server.course.api.dto.CourseResponse
import com.example.aandi_post_web_server.course.api.dto.CreateCourseRequest
import com.example.aandi_post_web_server.course.api.dto.EnrollCourseRequest
import com.example.aandi_post_web_server.course.api.dto.UpdateCourseRequest
import com.example.aandi_post_web_server.course.api.dto.UpdateEnrollmentRequest
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseMetadata
import com.example.aandi_post_web_server.course.entity.CourseWeek
import com.example.aandi_post_web_server.course.domain.model.CourseStatus
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseEnrollmentRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseWeekRepository
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@Service
class CourseCommandService(
    private val courseRepository: CourseRepository,
    private val courseEnrollmentRepository: CourseEnrollmentRepository,
    private val courseWeekRepository: CourseWeekRepository,
    private val assignmentRepository: AssignmentRepository,
    private val assignmentRequirementRepository: AssignmentRequirementRepository,
    private val assignmentTestCaseRepository: AssignmentTestCaseRepository,
    private val assignmentDeliveryRepository: AssignmentDeliveryRepository,
    private val assignmentReportTestCaseEventMapper: AssignmentReportTestCaseEventMapper,
    private val assignmentReportTestCaseEventPublisher: AssignmentReportTestCaseEventPublisher,
    private val courseEnrollmentCommandService: CourseEnrollmentCommandService,
    private val assignmentTestCaseValidator: AssignmentTestCaseValidator,
    private val assignmentMetadataPayloadTestCasePresenceTracker: AssignmentMetadataPayloadTestCasePresenceTracker,
) {
    private val log = LoggerFactory.getLogger(CourseCommandService::class.java)
    private val copyFingerprintObjectMapper = JsonMapper.builder()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
        .build()

    fun createCourse(request: CreateCourseRequest): Mono<CourseResponse> {
        val slug = parseCourseSlug(request.slug)
        return courseRepository.existsBySlug(slug.value)
            .flatMap { exists ->
                if (exists) {
                    return@flatMap Mono.error(ResponseStatusException(HttpStatus.CONFLICT, "이미 존재하는 코스 slug입니다: ${slug.value}"))
                }
                createCourseEntity(request, slug)
            }
    }

    fun updateCourse(courseSlug: String, request: UpdateCourseRequest): Mono<CourseResponse> {
        val slug = parseCourseSlug(courseSlug)
        return findCourseBySlug(slug)
            .flatMap { course ->
                val updatedStartDate = request.startDate ?: course.startDate
                val updatedEndDate = request.endDate ?: course.endDate
                if (updatedEndDate.isBefore(updatedStartDate)) {
                    return@flatMap Mono.error(ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate는 startDate보다 빠를 수 없습니다."))
                }
                val updated = course.copy(
                    fieldTag = request.fieldTag ?: course.fieldTag,
                    startDate = updatedStartDate,
                    endDate = updatedEndDate,
                    metadata = request.metadata?.let {
                        CourseMetadata(
                            title = it.title.trim(),
                            description = it.description?.trim(),
                            phase = it.phase,
                            attributes = it.attributes,
                        )
                    } ?: course.metadata,
                    status = request.status ?: course.status,
                    updatedAt = Instant.now(),
                )
                courseRepository.save(updated).map(::toCourseResponse)
            }
    }

    fun deleteCourse(courseSlug: String): Mono<Void> {
        val slug = parseCourseSlug(courseSlug)
        return findCourseBySlug(slug)
            .flatMap { course ->
                val courseId = parseCourseId(requireNotNull(course.id))
                deleteAssignmentsByCourse(courseId.value)
                    .then(deleteCourseRelations(courseId.value))
                    .then(courseRepository.deleteById(courseId.value))
            }
            .then()
    }

    fun enrollMember(courseSlug: String, request: EnrollCourseRequest): Mono<CourseEnrollmentResponse> {
        return courseEnrollmentCommandService.enrollMember(courseSlug, request)
    }

    fun updateEnrollmentStatus(
        courseSlug: String,
        userId: String,
        request: UpdateEnrollmentRequest,
    ): Mono<CourseEnrollmentResponse> {
        return courseEnrollmentCommandService.updateEnrollmentStatus(courseSlug, userId, request)
    }

    fun deleteEnrollment(courseSlug: String, userId: String): Mono<Void> {
        return courseEnrollmentCommandService.deleteEnrollment(courseSlug, userId)
    }

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
        return resolveCreateRequest(request)
            .flatMap { resolvedRequest ->
                val requirementDrafts = parseRequirementDrafts(resolvedRequest.metadata.requirements)
                val testCaseDrafts = parseTestCaseDrafts(resolvedRequest.metadata.testCases)

                findCourseBySlug(slug)
                    .flatMap { course ->
                        val courseId = parseCourseId(requireNotNull(course.id))
                        ensureWeekExistsOrCreate(
                            courseId = courseId,
                            weekNo = weekNo,
                            startAt = resolvedRequest.startAt,
                            endAt = resolvedRequest.endAt,
                        )
                            .then(
                                Mono.defer {
                                    assignmentRepository.findByCourseIdAndWeekNoAndOrderInWeek(courseId.value, weekNo.value, resolvedRequest.orderInWeek)
                                        .flatMap<AssignmentDetailResponse> {
                                            Mono.error(
                                                ResponseStatusException(
                                                    HttpStatus.CONFLICT,
                                                    "동일 코스/주차/순번 과제가 이미 존재합니다.",
                                                )
                                            )
                                        }
                                        .switchIfEmpty(
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
                                                ).flatMap { assignment ->
                                                    val assignmentId = parseAssignmentId(requireNotNull(assignment.id))
                                                    val requirementsMono = saveRequirements(assignmentId, requirementDrafts)
                                                    val testCasesMono = saveTestCases(assignmentId, testCaseDrafts)
                                                    Mono.zip(requirementsMono, testCasesMono)
                                                        .flatMap { tuple ->
                                                            val response = toAssignmentDetailResponse(
                                                                courseSlug = course.slug,
                                                                assignment = assignment,
                                                                requirements = tuple.t1,
                                                                testCases = tuple.t2,
                                                            )
                                                            publishProblemSyncOnCreate(assignment)
                                                                .thenReturn(response)
                                                        }
                                                }
                                            }
                                        )
                                }
                            )
                    }
            }
    }

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

    fun updateAssignment(
        courseSlug: String,
        assignmentId: String,
        request: UpdateAssignmentRequest,
    ): Mono<AssignmentDetailResponse> {
        val slug = parseCourseSlug(courseSlug)
        val parsedAssignmentId = parseAssignmentId(assignmentId)
        val parsedWeekNo = request.weekNo?.let { parseWeekNo(it) }
        val replaceTestCases = request.metadata?.let(::shouldReplaceTestCases) ?: false

        return resolveUpdateRequest(request, replaceTestCases)
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
                    .then(deleteAssignmentCascade(parsedAssignmentId.value))
            }
            .then()
    }

    private fun createCourseEntity(request: CreateCourseRequest, slug: CourseSlug): Mono<CourseResponse> {
        if (request.endDate.isBefore(request.startDate)) {
            return Mono.error(ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate는 startDate보다 빠를 수 없습니다."))
        }
        val now = Instant.now()
        val course = Course(
            slug = slug.value,
            fieldTag = request.fieldTag,
            startDate = request.startDate,
            endDate = request.endDate,
            metadata = CourseMetadata(
                title = request.metadata.title.trim(),
                description = request.metadata.description?.trim(),
                phase = request.metadata.phase,
                attributes = request.metadata.attributes,
            ),
            status = CourseStatus.PUBLISHED,
            createdAt = now,
            updatedAt = now,
        )
        return courseRepository.save(course).map(::toCourseResponse)
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
        val now = Instant.now()
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
            publishedAt = initialPublishedAt(startAt, now),
        )
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
        val copyFingerprint = buildAssignmentCopyFingerprint(sourceAssignment, sourceRequirements, sourceTestCases)
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
                ensureWeekExistsOrCreate(
                    courseId = targetCourseId,
                    weekNo = targetWeekNo,
                    startAt = targetStartAt,
                    endAt = targetEndAt,
                )
            )
            .then(assignmentRepository.save(copiedAssignment))
            .onErrorMap(DuplicateKeyException::class.java) { duplicateAssignmentCopyConflict() }
            .flatMap { saved ->
                val savedAssignmentId = parseAssignmentId(requireNotNull(saved.id))
                Mono.zip(
                    copyRequirements(savedAssignmentId, sourceRequirements),
                    copyTestCases(savedAssignmentId, sourceTestCases),
                )
                    .onErrorResume { error ->
                        deleteCopiedAssignmentDocuments(savedAssignmentId.value)
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

    private fun ensureWeekExists(courseId: CourseId, weekNo: WeekNo): Mono<Void> {
        return courseWeekRepository.findByCourseIdAndWeekNo(courseId.value, weekNo.value)
            .switchIfEmpty(
                Mono.error(
                    ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "주차를 찾을 수 없습니다: ${weekNo.value}",
                    )
                )
            )
            .then()
    }

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

    private fun deleteAssignmentsByCourse(courseId: String): Mono<Void> {
        return assignmentRepository.findAllByCourseId(courseId)
            .map { it.id }
            .filter { it != null }
            .map { it!! }
            .collectList()
            .flatMap { assignmentIds ->
                if (assignmentIds.isEmpty()) {
                    Mono.empty<Void>()
                } else {
                    Mono.whenDelayError(
                        assignmentRequirementRepository.deleteAllByAssignmentIdIn(assignmentIds).then(),
                        assignmentTestCaseRepository.deleteAllByAssignmentIdIn(assignmentIds).then(),
                        assignmentDeliveryRepository.deleteAllByAssignmentIdIn(assignmentIds).then(),
                        assignmentRepository.deleteAllById(assignmentIds).then()
                    ).then(
                        Flux.fromIterable(assignmentIds)
                            .concatMap(::publishDeletedTestCases)
                            .then()
                    )
                }
            }
    }

    private fun deleteCourseRelations(courseId: String): Mono<Void> {
        return Mono.whenDelayError(
            courseWeekRepository.deleteAllByCourseId(courseId).then(),
            courseEnrollmentRepository.deleteAllByCourseId(courseId).then()
        )
    }

    private fun updateAssignmentInternal(
        course: Course,
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
        val now = Instant.now()

        val candidate = assignment.copy(
            weekNo = targetWeekNo,
            orderInWeek = targetOrderInWeek,
            startAt = targetStartAt,
            endAt = targetEndAt,
            metadata = metadata,
            status = AssignmentStatus.PUBLISHED,
            updatedAt = now,
            publishedAt = assignment.publishedAt ?: targetStartAt,
        )

        val checkDuplicate = ensureAssignmentSlotAvailable(courseId, candidate, parsedAssignmentId)
        val checkWeek = ensureWeekExistsOrCreate(
            courseId = courseId,
            weekNo = parseWeekNo(targetWeekNo),
            startAt = targetStartAt,
            endAt = targetEndAt,
        )
        return checkWeek
            .then(checkDuplicate)
            .then(assignmentRepository.save(candidate))
            .flatMap { saved ->
                loadOrReplaceRequirements(parsedAssignmentId, requirementDrafts)
                    .zipWith(loadOrReplaceTestCases(parsedAssignmentId, testCaseDrafts))
                    .flatMap { tuple ->
                        val response = toAssignmentDetailResponse(course.slug, saved, tuple.t1, tuple.t2)
                        publishProblemSyncOnUpdate(saved)
                            .thenReturn(response)
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

    private fun deleteCopiedAssignmentDocuments(assignmentId: String): Mono<Void> =
        Mono.whenDelayError(
            assignmentRequirementRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)).then(),
            assignmentTestCaseRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)).then(),
            assignmentDeliveryRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)).then(),
            assignmentRepository.deleteById(assignmentId).then(),
        ).then()

    private fun deleteAssignmentCascade(assignmentId: String): Mono<Void> {
        return Mono.whenDelayError(
            assignmentRequirementRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)).then(),
            assignmentTestCaseRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)).then(),
            assignmentDeliveryRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)).then(),
            assignmentRepository.deleteById(assignmentId).then(),
        ).then(publishDeletedTestCases(assignmentId))
    }

    private fun publishProblemSyncOnCreate(assignment: Assignment): Mono<Void> {
        val assignmentId = requireNotNull(assignment.id)
        return loadAssignmentProblemSyncSnapshot(assignmentId)
            .flatMap { (snapshotAssignment, testCases) ->
                publishProblemSyncEvent(assignmentReportTestCaseEventMapper.created(snapshotAssignment, testCases))
            }
    }

    private fun publishProblemSyncOnUpdate(
        currentAssignment: Assignment,
    ): Mono<Void> {
        val assignmentId = requireNotNull(currentAssignment.id)
        return loadAssignmentProblemSyncSnapshot(assignmentId)
            .flatMap { (assignment, testCases) ->
                publishProblemSyncEvent(assignmentReportTestCaseEventMapper.updated(assignment, testCases))
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

    private fun publishDeletedTestCases(assignmentId: String): Mono<Void> =
        assignmentReportTestCaseEventPublisher.publish(
            assignmentReportTestCaseEventMapper.deleted(assignmentId)
        )

    private fun initialPublishedAt(startAt: Instant, now: Instant = Instant.now()): Instant =
        if (now >= startAt) now else startAt

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

    private fun parseWeekNo(raw: Int): WeekNo =
        parseOrBadRequest { WeekNo.from(raw) }

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

    private fun parseAssignmentId(raw: String): AssignmentId =
        parseOrBadRequest { AssignmentId.from(raw) }

    private fun parseSourceAssignmentId(raw: String): AssignmentId =
        parseOrBadRequest {
            val normalized = raw.trim()
            require(normalized.isNotBlank()) { "sourceAssignmentId는 필수입니다." }
            require(runCatching { UUID.fromString(normalized) }.isSuccess) { "sourceAssignmentId는 UUID 형식이어야 합니다." }
            AssignmentId.from(normalized)
        }

    private fun parseRequirementDrafts(requests: List<CreateAssignmentRequirementRequest>): AssignmentRequirementDrafts =
        parseOrBadRequest { AssignmentRequirementDrafts.fromRequests(requests) }

    private fun parseTestCaseDrafts(requests: List<CreateAssignmentTestCaseRequest>): AssignmentTestCaseDrafts =
        parseOrBadRequest { AssignmentTestCaseDrafts.fromRequests(requests) }

    private fun resolveCreateRequest(request: CreateAssignmentRequest): Mono<CreateAssignmentRequest> {
        return Mono.just(validateResolvedCreateRequest(request))
    }

    private fun resolveUpdateRequest(
        request: UpdateAssignmentRequest,
        replaceTestCases: Boolean,
    ): Mono<UpdateAssignmentRequest> {
        return Mono.just(validateResolvedUpdateRequest(request, replaceTestCases))
    }

    private fun validateResolvedCreateRequest(request: CreateAssignmentRequest): CreateAssignmentRequest {
        assignmentMetadataPayloadTestCasePresenceTracker.consumeTestCasesProvided(request.metadata)
        if (request.metadata.title.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "과제 제목이 필요합니다.")
        }
        if (request.metadata.description.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "과제 설명이 필요합니다.")
        }
        validateTestCases(request.metadata.testCases)
        return request
    }

    private fun validateResolvedUpdateRequest(
        request: UpdateAssignmentRequest,
        replaceTestCases: Boolean,
    ): UpdateAssignmentRequest {
        val metadata = request.metadata ?: return request
        if (metadata.title != null && metadata.title.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "과제 제목이 비어 있을 수 없습니다.")
        }
        if (metadata.description != null && metadata.description.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "과제 설명이 비어 있을 수 없습니다.")
        }
        if (replaceTestCases) {
            validateTestCases(metadata.testCases)
        }
        return request
    }

    private fun validateTestCases(requests: List<CreateAssignmentTestCaseRequest>) {
        parseOrBadRequest {
            assignmentTestCaseValidator.validate(requests)
        }
    }

    private fun shouldReplaceTestCases(metadata: AssignmentMetadataPayload): Boolean {
        val tracked = assignmentMetadataPayloadTestCasePresenceTracker.consumeTestCasesProvided(metadata)
        return tracked ?: metadata.testCases.isNotEmpty()
    }

    private fun <T> parseOrBadRequest(block: () -> T): T {
        return runCatching(block).getOrElse { error ->
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, error.message ?: "잘못된 요청입니다.")
        }
    }

    private fun toCourseResponse(course: Course): CourseResponse = CourseResponse(
        id = requireNotNull(course.id),
        slug = course.slug,
        fieldTag = course.fieldTag,
        startDate = course.startDate,
        endDate = course.endDate,
        metadata = com.example.aandi_post_web_server.course.api.dto.CourseMetadataResponse(
            title = course.metadata.title,
            description = course.metadata.description,
            phase = course.metadata.phase,
            attributes = course.metadata.attributes,
        ),
        status = course.status,
        createdAt = course.createdAt,
        updatedAt = course.updatedAt,
    )

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

    private fun buildAssignmentCopyFingerprint(
        assignment: Assignment,
        requirements: List<AssignmentRequirement>,
        testCases: List<AssignmentTestCase>,
    ): String {
        val metadata = assignment.metadata
        val payload = linkedMapOf<String, Any?>(
            "title" to metadata.title,
            "difficulty" to metadata.difficulty.name,
            "description" to metadata.description,
            "timeLimitMinutes" to metadata.timeLimitMinutes,
            "learningGoals" to metadata.learningGoals.map { it },
            "codeTemplates" to metadata.codeTemplates
                .sortedWith(compareBy({ it.language.name }, { it.functionTemplate }))
                .map {
                    linkedMapOf(
                        "language" to it.language.name,
                        "functionTemplate" to it.functionTemplate,
                    )
                },
            "requirements" to requirements
                .sortedWith(compareBy<AssignmentRequirement> { it.sortOrder }.thenBy { it.requirementText })
                .map {
                    linkedMapOf(
                        "sortOrder" to it.sortOrder,
                        "requirementText" to it.requirementText,
                    )
                },
            "testCases" to testCases
                .sortedWith(
                    compareBy<AssignmentTestCase> { it.seq }
                        .thenBy { it.inputValues.joinToString("\u001F") }
                        .thenBy { it.outputText }
                        .thenBy { it.visibility.name }
                )
                .map {
                    linkedMapOf(
                        "seq" to it.seq,
                        "inputValues" to it.inputValues,
                        "outputText" to it.outputText,
                        "visibility" to it.visibility.name,
                    )
                },
        )
        val json = copyFingerprintObjectMapper.writeValueAsString(payload)
        val digest = MessageDigest.getInstance("SHA-256").digest(json.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
