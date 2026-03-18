package com.example.aandi_post_web_server.course.service

import com.example.aandi_post_web_server.assignment.domain.AssignmentTestCaseDrafts
import com.example.aandi_post_web_server.assignment.domain.toDetailResponse
import com.example.aandi_post_web_server.assignment.domain.toEntity
import com.example.aandi_post_web_server.assignment.domain.toResponse
import com.example.aandi_post_web_server.assignment.domain.AssignmentRequirementDrafts
import com.example.aandi_post_web_server.assignment.dtos.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentRequirementResponse
import com.example.aandi_post_web_server.assignment.dtos.CreateAssignmentRequest
import com.example.aandi_post_web_server.assignment.dtos.CreateAssignmentRequirementRequest
import com.example.aandi_post_web_server.assignment.dtos.CreateAssignmentTestCaseRequest
import com.example.aandi_post_web_server.assignment.dtos.AssignmentTestCaseResponse
import com.example.aandi_post_web_server.assignment.dtos.UpdateAssignmentRequest
import com.example.aandi_post_web_server.assignment.event.AssignmentReportTestCaseEventMapper
import com.example.aandi_post_web_server.assignment.event.AssignmentReportTestCaseEventPublisher
import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.entity.AssignmentRequirement
import com.example.aandi_post_web_server.assignment.entity.AssignmentTestCase
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import com.example.aandi_post_web_server.assignment.repository.AssignmentDeliveryRepository
import com.example.aandi_post_web_server.assignment.repository.AssignmentRepository
import com.example.aandi_post_web_server.assignment.repository.AssignmentRequirementRepository
import com.example.aandi_post_web_server.assignment.repository.AssignmentTestCaseRepository
import com.example.aandi_post_web_server.course.domain.AssignmentId
import com.example.aandi_post_web_server.course.domain.CourseId
import com.example.aandi_post_web_server.course.domain.CourseSlug
import com.example.aandi_post_web_server.course.domain.PublicCode
import com.example.aandi_post_web_server.course.domain.UserId
import com.example.aandi_post_web_server.course.domain.WeekNo
import com.example.aandi_post_web_server.course.dtos.CourseEnrollmentResponse
import com.example.aandi_post_web_server.course.dtos.CourseResponse
import com.example.aandi_post_web_server.course.dtos.CreateCourseRequest
import com.example.aandi_post_web_server.course.dtos.EnrollCourseRequest
import com.example.aandi_post_web_server.course.dtos.UpdateCourseRequest
import com.example.aandi_post_web_server.course.dtos.UpdateEnrollmentRequest
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseEnrollment
import com.example.aandi_post_web_server.course.entity.CourseMetadata
import com.example.aandi_post_web_server.course.entity.CourseWeek
import com.example.aandi_post_web_server.course.enum.CourseStatus
import com.example.aandi_post_web_server.course.enum.EnrollmentStatus
import com.example.aandi_post_web_server.course.repository.CourseEnrollmentRepository
import com.example.aandi_post_web_server.course.repository.CourseRepository
import com.example.aandi_post_web_server.course.repository.CourseWeekRepository
import com.example.aandi_post_web_server.user.entity.ReportUser
import com.example.aandi_post_web_server.user.repository.ReportUserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
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
    private val reportUserRepository: ReportUserRepository,
) {

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
        val slug = parseCourseSlug(courseSlug)
        val publicCode = parsePublicCode(request.publicCode)
        return findCourseBySlug(slug)
            .flatMap { course ->
                val courseId = parseCourseId(requireNotNull(course.id))
                findReportUserByPublicCode(publicCode)
                    .switchIfEmpty(
                        Mono.error(
                            ResponseStatusException(
                                HttpStatus.UNPROCESSABLE_ENTITY,
                                "report 서버에서 publicCode=${publicCode.value} 사용자를 찾을 수 없습니다. auth 이벤트 동기화 여부를 확인해주세요.",
                            )
                        )
                    )
                    .flatMap { reportUser -> enrollUser(courseId, course.slug, reportUser) }
            }
    }

    fun updateEnrollmentStatus(
        courseSlug: String,
        userId: String,
        request: UpdateEnrollmentRequest,
    ): Mono<CourseEnrollmentResponse> {
        val slug = parseCourseSlug(courseSlug)
        val parsedUserId = parseUserId(userId)
        return findCourseBySlug(slug)
            .flatMap { course ->
                val courseId = parseCourseId(requireNotNull(course.id))
                courseEnrollmentRepository.findByCourseIdAndUserId(courseId.value, parsedUserId.value)
                    .switchIfEmpty(
                        Mono.error(
                            ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "코스에 등록된 사용자를 찾을 수 없습니다: ${parsedUserId.value}",
                            )
                        )
                    )
                    .flatMap { enrollment ->
                        val now = Instant.now()
                        val updated = when (request.status) {
                            EnrollmentStatus.ENABLED -> enrollment.copy(
                                status = EnrollmentStatus.ENABLED,
                                bannedAt = null,
                                banReason = null,
                                updatedAt = now,
                            )

                            EnrollmentStatus.BANNED -> {
                                if (request.banReason.isNullOrBlank()) {
                                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "BANNED 상태는 banReason이 필요합니다.")
                                }
                                enrollment.copy(
                                    status = EnrollmentStatus.BANNED,
                                    bannedAt = now,
                                    banReason = request.banReason.trim(),
                                    updatedAt = now,
                                )
                            }
                        }
                        courseEnrollmentRepository.save(updated)
                    }
                    .map { enrollment -> toEnrollmentResponse(course.slug, enrollment) }
            }
    }

    fun deleteEnrollment(courseSlug: String, userId: String): Mono<Void> {
        val slug = parseCourseSlug(courseSlug)
        val parsedUserId = parseUserId(userId)
        return findCourseBySlug(slug)
            .flatMap { course ->
                val courseId = parseCourseId(requireNotNull(course.id))
                courseEnrollmentRepository.findByCourseIdAndUserId(courseId.value, parsedUserId.value)
                    .switchIfEmpty(
                        Mono.error(
                            ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "코스에 등록된 사용자를 찾을 수 없습니다: ${parsedUserId.value}",
                            )
                        )
                    )
                    .flatMap { enrollment -> courseEnrollmentRepository.delete(enrollment) }
            }
            .then()
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
                                                            publishCreatedTestCases(assignment, tuple.t2)
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

    fun updateAssignment(
        courseSlug: String,
        assignmentId: String,
        request: UpdateAssignmentRequest,
    ): Mono<AssignmentDetailResponse> {
        val slug = parseCourseSlug(courseSlug)
        val parsedAssignmentId = parseAssignmentId(assignmentId)
        val parsedWeekNo = request.weekNo?.let { parseWeekNo(it) }

        return resolveUpdateRequest(request)
            .flatMap { resolvedRequest ->
                val requirementDrafts = resolvedRequest.metadata?.let { parseRequirementDrafts(it.requirements) }
                val testCaseDrafts = resolvedRequest.metadata?.let { parseTestCaseDrafts(it.testCases) }

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
            status = effectiveAssignmentStatus(startAt, now),
            createdAt = now,
            updatedAt = now,
            publishedAt = effectivePublishedAt(startAt, null, now),
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
            .map { AssignmentTestCaseResponse(it.seq, it.inputText, it.outputText, it.visibility) }
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
            status = effectiveAssignmentStatus(targetStartAt, now),
            updatedAt = now,
            publishedAt = effectivePublishedAt(targetStartAt, assignment.publishedAt, now),
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
                        publishUpdatedTestCasesIfTestCasesChanged(saved, testCaseDrafts, tuple.t2)
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
                .map { AssignmentTestCaseResponse(it.seq, it.inputText, it.outputText, it.visibility) }
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

    private fun deleteAssignmentCascade(assignmentId: String): Mono<Void> {
        return Mono.whenDelayError(
            assignmentRequirementRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)).then(),
            assignmentTestCaseRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)).then(),
            assignmentDeliveryRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)).then(),
            assignmentRepository.deleteById(assignmentId).then(),
        ).then(publishDeletedTestCases(assignmentId))
    }

    private fun publishCreatedTestCases(
        assignment: Assignment,
        testCases: List<AssignmentTestCaseResponse>,
    ): Mono<Void> =
        assignmentReportTestCaseEventPublisher.publish(
            assignmentReportTestCaseEventMapper.created(assignment, testCases)
        )

    private fun publishUpdatedTestCases(
        assignment: Assignment,
        testCases: List<AssignmentTestCaseResponse>,
    ): Mono<Void> =
        assignmentReportTestCaseEventPublisher.publish(
            assignmentReportTestCaseEventMapper.updated(assignment, testCases)
        )

    private fun publishUpdatedTestCasesIfTestCasesChanged(
        assignment: Assignment,
        testCaseDrafts: AssignmentTestCaseDrafts?,
        testCases: List<AssignmentTestCaseResponse>,
    ): Mono<Void> {
        if (testCaseDrafts == null) {
            return Mono.empty()
        }
        return publishUpdatedTestCases(assignment, testCases)
    }

    private fun publishDeletedTestCases(assignmentId: String): Mono<Void> =
        assignmentReportTestCaseEventPublisher.publish(
            assignmentReportTestCaseEventMapper.deleted(assignmentId)
        )

    private fun effectiveAssignmentStatus(startAt: Instant, now: Instant = Instant.now()): AssignmentStatus {
        if (now >= startAt) {
            return AssignmentStatus.PUBLISHED
        }
        return AssignmentStatus.DRAFT
    }

    private fun effectivePublishedAt(
        startAt: Instant,
        publishedAt: Instant?,
        now: Instant = Instant.now(),
    ): Instant? {
        if (effectiveAssignmentStatus(startAt, now) == AssignmentStatus.PUBLISHED) {
            return publishedAt ?: startAt
        }
        return null
    }

    private fun findCourseBySlug(slug: CourseSlug): Mono<Course> {
        return courseRepository.findBySlug(slug.value)
            .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "코스를 찾을 수 없습니다: ${slug.value}")))
    }

    private fun parseCourseSlug(raw: String): CourseSlug =
        parseOrBadRequest { CourseSlug.from(raw) }

    private fun parseCourseId(raw: String): CourseId =
        parseOrBadRequest { CourseId.from(raw) }

    private fun parseUserId(raw: String): UserId =
        parseOrBadRequest { UserId.from(raw) }

    private fun parsePublicCode(raw: String): PublicCode =
        parseOrBadRequest { PublicCode.from(raw) }

    private fun findReportUserByPublicCode(publicCode: PublicCode): Mono<ReportUser> =
        reportUserRepository.findByPublicCode(publicCode.value)
            .switchIfEmpty(Mono.defer { reportUserRepository.findByPublicCode(publicCode.legacyValue) })

    private fun parseWeekNo(raw: Int): WeekNo =
        parseOrBadRequest { WeekNo.from(raw) }

    private fun parseAssignmentId(raw: String): AssignmentId =
        parseOrBadRequest { AssignmentId.from(raw) }

    private fun parseRequirementDrafts(requests: List<CreateAssignmentRequirementRequest>): AssignmentRequirementDrafts =
        parseOrBadRequest { AssignmentRequirementDrafts.fromRequests(requests) }

    private fun parseTestCaseDrafts(requests: List<CreateAssignmentTestCaseRequest>): AssignmentTestCaseDrafts =
        parseOrBadRequest { AssignmentTestCaseDrafts.fromRequests(requests) }

    private fun resolveCreateRequest(request: CreateAssignmentRequest): Mono<CreateAssignmentRequest> {
        return Mono.just(validateResolvedCreateRequest(request))
    }

    private fun resolveUpdateRequest(request: UpdateAssignmentRequest): Mono<UpdateAssignmentRequest> {
        return Mono.just(validateResolvedUpdateRequest(request))
    }

    private fun validateResolvedCreateRequest(request: CreateAssignmentRequest): CreateAssignmentRequest {
        if (request.metadata.title.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "과제 제목이 필요합니다.")
        }
        if (request.metadata.description.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "과제 설명이 필요합니다.")
        }
        return request
    }

    private fun validateResolvedUpdateRequest(request: UpdateAssignmentRequest): UpdateAssignmentRequest {
        val metadata = request.metadata ?: return request
        if (metadata.title != null && metadata.title.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "과제 제목이 비어 있을 수 없습니다.")
        }
        if (metadata.description != null && metadata.description.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "과제 설명이 비어 있을 수 없습니다.")
        }
        return request
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
        metadata = com.example.aandi_post_web_server.course.dtos.CourseMetadataResponse(
            title = course.metadata.title,
            description = course.metadata.description,
            phase = course.metadata.phase,
            attributes = course.metadata.attributes,
        ),
        status = course.status,
        createdAt = course.createdAt,
        updatedAt = course.updatedAt,
    )

    private fun toEnrollmentResponse(courseSlug: String, enrollment: CourseEnrollment): CourseEnrollmentResponse = CourseEnrollmentResponse(
        courseId = enrollment.courseId,
        courseSlug = courseSlug,
        userId = enrollment.userId,
        publicCode = enrollment.publicCode,
        username = enrollment.username,
        status = enrollment.status,
        joinedAt = enrollment.joinedAt,
        bannedAt = enrollment.bannedAt,
        banReason = enrollment.banReason,
        updatedAt = enrollment.updatedAt,
    )

    private fun enrollUser(
        courseId: CourseId,
        courseSlug: String,
        reportUser: ReportUser,
    ): Mono<CourseEnrollmentResponse> {
        return courseEnrollmentRepository.findByCourseIdAndUserId(courseId.value, reportUser.id)
            .flatMap<CourseEnrollmentResponse> { existing ->
                val message = if (existing.status == EnrollmentStatus.BANNED) {
                    "차단된 사용자는 재등록할 수 없습니다: ${reportUser.publicCode}"
                } else {
                    "이미 등록된 사용자입니다. courseId=${courseId.value}, userId=${reportUser.id}"
                }
                Mono.error(
                    ResponseStatusException(
                        HttpStatus.CONFLICT,
                        message,
                    )
                )
            }
            .switchIfEmpty(
                Mono.defer {
                    val now = Instant.now()
                    courseEnrollmentRepository.save(
                        CourseEnrollment(
                            courseId = courseId.value,
                            userId = reportUser.id,
                            publicCode = reportUser.publicCode,
                            username = reportUser.username,
                            status = EnrollmentStatus.ENABLED,
                            joinedAt = now,
                            updatedAt = now,
                        )
                    )
                        .map { enrollment -> toEnrollmentResponse(courseSlug, enrollment) }
                }
            )
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
        status = effectiveAssignmentStatus(assignment.startAt),
        publishedAt = effectivePublishedAt(assignment.startAt, assignment.publishedAt),
        metadata = assignment.metadata.toDetailResponse(requirements, testCases),
    )
}
