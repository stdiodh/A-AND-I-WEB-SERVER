package com.example.aandi_post_web_server.course.service

import com.example.aandi_post_web_server.assignment.domain.AssignmentExampleDrafts
import com.example.aandi_post_web_server.assignment.domain.AssignmentImportService
import com.example.aandi_post_web_server.assignment.domain.toEntity
import com.example.aandi_post_web_server.assignment.domain.source
import com.example.aandi_post_web_server.assignment.domain.withImportedContent
import com.example.aandi_post_web_server.assignment.domain.toResponse
import com.example.aandi_post_web_server.assignment.domain.AssignmentRequirementDrafts
import com.example.aandi_post_web_server.assignment.dtos.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentExampleResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentRequirementResponse
import com.example.aandi_post_web_server.assignment.dtos.CreateAssignmentExampleRequest
import com.example.aandi_post_web_server.assignment.dtos.CreateAssignmentRequest
import com.example.aandi_post_web_server.assignment.dtos.CreateAssignmentRequirementRequest
import com.example.aandi_post_web_server.assignment.dtos.PublishAssignmentResponse
import com.example.aandi_post_web_server.assignment.dtos.TriggerDeliveriesResponse
import com.example.aandi_post_web_server.assignment.dtos.UpdateAssignmentRequest
import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.entity.AssignmentDelivery
import com.example.aandi_post_web_server.assignment.entity.AssignmentExample
import com.example.aandi_post_web_server.assignment.entity.AssignmentRequirement
import com.example.aandi_post_web_server.assignment.enum.AssignmentDeliveryStatus
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import com.example.aandi_post_web_server.assignment.repository.AssignmentDeliveryRepository
import com.example.aandi_post_web_server.assignment.repository.AssignmentExampleRepository
import com.example.aandi_post_web_server.assignment.repository.AssignmentRepository
import com.example.aandi_post_web_server.assignment.repository.AssignmentRequirementRepository
import com.example.aandi_post_web_server.course.domain.AssignmentId
import com.example.aandi_post_web_server.course.domain.CourseId
import com.example.aandi_post_web_server.course.domain.CourseSlug
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
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

@Service
class CourseCommandService(
    private val courseRepository: CourseRepository,
    private val courseEnrollmentRepository: CourseEnrollmentRepository,
    private val courseWeekRepository: CourseWeekRepository,
    private val assignmentRepository: AssignmentRepository,
    private val assignmentRequirementRepository: AssignmentRequirementRepository,
    private val assignmentExampleRepository: AssignmentExampleRepository,
    private val assignmentDeliveryRepository: AssignmentDeliveryRepository,
    private val assignmentImportService: AssignmentImportService,
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
        val userId = parseUserId(request.userId)
        return findCourseBySlug(slug)
            .flatMap { course ->
                val courseId = parseCourseId(requireNotNull(course.id))
                courseEnrollmentRepository.findByCourseIdAndUserId(courseId.value, userId.value)
                    .flatMap { existing ->
                        val now = Instant.now()
                        val updated = existing.copy(
                            status = EnrollmentStatus.ENROLLED,
                            droppedAt = null,
                            bannedAt = null,
                            banReason = null,
                            updatedAt = now,
                        )
                        courseEnrollmentRepository.save(updated)
                    }
                    .switchIfEmpty(
                        courseEnrollmentRepository.save(
                            CourseEnrollment(
                                courseId = courseId.value,
                                userId = userId.value,
                                status = EnrollmentStatus.ENROLLED,
                                joinedAt = Instant.now(),
                                updatedAt = Instant.now(),
                            )
                        )
                    )
                    .map(::toEnrollmentResponse)
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
                            EnrollmentStatus.ENROLLED -> enrollment.copy(
                                status = EnrollmentStatus.ENROLLED,
                                droppedAt = null,
                                bannedAt = null,
                                banReason = null,
                                updatedAt = now,
                            )

                            EnrollmentStatus.DROPPED -> enrollment.copy(
                                status = EnrollmentStatus.DROPPED,
                                droppedAt = now,
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
                                    droppedAt = null,
                                    banReason = request.banReason.trim(),
                                    updatedAt = now,
                                )
                            }
                        }
                        courseEnrollmentRepository.save(updated)
                    }
                    .map(::toEnrollmentResponse)
            }
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
                val requirementDrafts = parseRequirementDrafts(resolvedRequest.requirements)
                val exampleDrafts = parseExampleDrafts(resolvedRequest.examples)

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
                                                    Assignment(
                                                        courseId = courseId.value,
                                                        courseSlug = course.slug,
                                                        createdBy = createdBy,
                                                        weekNo = weekNo.value,
                                                        orderInWeek = resolvedRequest.orderInWeek,
                                                        startAt = resolvedRequest.startAt,
                                                        endAt = resolvedRequest.endAt,
                                                        metadata = resolvedRequest.metadata.toEntity(),
                                                        status = AssignmentStatus.DRAFT,
                                                        createdAt = Instant.now(),
                                                        updatedAt = Instant.now(),
                                                    )
                                                ).flatMap { assignment ->
                                                    val assignmentId = parseAssignmentId(requireNotNull(assignment.id))
                                                    val requirementsMono = saveRequirements(assignmentId, requirementDrafts)
                                                    val examplesMono = saveExamples(assignmentId, exampleDrafts)
                                                    Mono.zip(requirementsMono, examplesMono)
                                                        .map { tuple ->
                                                            toAssignmentDetailResponse(
                                                                courseSlug = course.slug,
                                                                assignment = assignment,
                                                                requirements = tuple.t1,
                                                                examples = tuple.t2,
                                                            )
                                                        }
                                                }
                                            }
                                        )
                                }
                            )
                    }
            }
    }

    fun publishAssignment(courseSlug: String, assignmentId: String): Mono<PublishAssignmentResponse> {
        val slug = parseCourseSlug(courseSlug)
        val parsedAssignmentId = parseAssignmentId(assignmentId)
        return findCourseBySlug(slug)
            .flatMap { course ->
                assignmentRepository.findByIdAndCourseId(parsedAssignmentId.value, requireNotNull(course.id))
                    .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "과제를 찾을 수 없습니다: ${parsedAssignmentId.value}")))
                    .flatMap { assignment -> publishAssignmentOrFail(assignment, course.slug) }
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
                val requirementDrafts = resolvedRequest.requirements?.let { parseRequirementDrafts(it) }
                val exampleDrafts = resolvedRequest.examples?.let { parseExampleDrafts(it) }

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
                                    exampleDrafts = exampleDrafts,
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

    fun triggerDeliveries(courseSlug: String, assignmentId: String): Mono<TriggerDeliveriesResponse> {
        val slug = parseCourseSlug(courseSlug)
        val parsedAssignmentId = parseAssignmentId(assignmentId)
        return findCourseBySlug(slug)
            .flatMap { course ->
                val courseId = parseCourseId(requireNotNull(course.id))
                assignmentRepository.findByIdAndCourseId(parsedAssignmentId.value, courseId.value)
                    .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "과제를 찾을 수 없습니다: ${parsedAssignmentId.value}")))
                    .flatMap { assignment ->
                        triggerDeliveriesForAssignment(
                            assignment = assignment,
                            assignmentId = parsedAssignmentId,
                            courseId = courseId,
                            courseSlug = course.slug,
                        )
                    }
            }
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

    private fun publishAssignmentOrFail(
        assignment: Assignment,
        courseSlug: String,
    ): Mono<PublishAssignmentResponse> {
        if (assignment.status == AssignmentStatus.PUBLISHED) {
            return Mono.just(
                PublishAssignmentResponse(
                    assignmentId = requireNotNull(assignment.id),
                    courseSlug = courseSlug,
                    status = assignment.status,
                    publishedAt = assignment.publishedAt,
                )
            )
        }
        val published = assignment.copy(
            status = AssignmentStatus.PUBLISHED,
            publishedAt = assignment.publishedAt ?: Instant.now(),
            updatedAt = Instant.now(),
        )
        return assignmentRepository.save(published).map {
            PublishAssignmentResponse(
                assignmentId = requireNotNull(it.id),
                courseSlug = courseSlug,
                status = it.status,
                publishedAt = it.publishedAt,
            )
        }
    }

    private fun triggerDeliveriesForAssignment(
        assignment: Assignment,
        assignmentId: AssignmentId,
        courseId: CourseId,
        courseSlug: String,
    ): Mono<TriggerDeliveriesResponse> {
        if (assignment.status != AssignmentStatus.PUBLISHED) {
            return Mono.error(
                ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "PUBLISHED 상태 과제만 배포할 수 있습니다.")
            )
        }
        return loadEnrolledMembers(courseId)
            .collectList()
            .flatMap { enrollments ->
                if (enrollments.isEmpty()) {
                    return@flatMap Mono.just(emptyDeliveryResponse(assignmentId, courseSlug))
                }
                deliverToMembers(assignmentId, enrollments)
                    .map { deliveries ->
                        createDeliveryResponse(assignmentId, courseSlug, enrollments.size, deliveries)
                    }
            }
    }

    private fun loadEnrolledMembers(courseId: CourseId): Flux<CourseEnrollment> =
        courseEnrollmentRepository.findAllByCourseIdAndStatus(courseId.value, EnrollmentStatus.ENROLLED)

    private fun deliverToMembers(
        assignmentId: AssignmentId,
        enrollments: List<CourseEnrollment>,
    ): Mono<List<AssignmentDelivery>> {
        return Flux.fromIterable(enrollments)
            .flatMap { enrollment -> upsertDelivered(assignmentId, parseUserId(enrollment.userId)) }
            .collectList()
    }

    private fun emptyDeliveryResponse(
        assignmentId: AssignmentId,
        courseSlug: String,
    ): TriggerDeliveriesResponse = TriggerDeliveriesResponse(
        assignmentId = assignmentId.value,
        courseSlug = courseSlug,
        targetCount = 0,
        deliveredCount = 0,
        failedCount = 0,
    )

    private fun createDeliveryResponse(
        assignmentId: AssignmentId,
        courseSlug: String,
        targetCount: Int,
        deliveries: List<AssignmentDelivery>,
    ): TriggerDeliveriesResponse = TriggerDeliveriesResponse(
        assignmentId = assignmentId.value,
        courseSlug = courseSlug,
        targetCount = targetCount,
        deliveredCount = deliveries.count { it.status == AssignmentDeliveryStatus.DELIVERED },
        failedCount = deliveries.count { it.status == AssignmentDeliveryStatus.FAILED },
    )

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

    private fun saveExamples(
        assignmentId: AssignmentId,
        drafts: AssignmentExampleDrafts,
    ): Mono<List<AssignmentExampleResponse>> {
        if (drafts.isEmpty()) return Mono.just(emptyList())

        return assignmentExampleRepository.saveAll(
            drafts.toEntities(assignmentId.value, Instant.now())
        )
            .sort(compareBy<AssignmentExample> { it.seq })
            .map { AssignmentExampleResponse(it.seq, it.inputText, it.outputText, it.description) }
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
                        assignmentExampleRepository.deleteAllByAssignmentIdIn(assignmentIds).then(),
                        assignmentDeliveryRepository.deleteAllByAssignmentIdIn(assignmentIds).then(),
                        assignmentRepository.deleteAllById(assignmentIds).then()
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

    private fun upsertDelivered(assignmentId: AssignmentId, userId: UserId): Mono<AssignmentDelivery> {
        val now = Instant.now()
        return assignmentDeliveryRepository.findByAssignmentIdAndUserId(assignmentId.value, userId.value)
            .flatMap { existing ->
                assignmentDeliveryRepository.save(
                    existing.copy(
                        status = AssignmentDeliveryStatus.DELIVERED,
                        deliveredAt = now,
                        failureReason = null,
                    )
                )
            }
            .switchIfEmpty(
                assignmentDeliveryRepository.save(
                    AssignmentDelivery(
                        assignmentId = assignmentId.value,
                        userId = userId.value,
                        status = AssignmentDeliveryStatus.DELIVERED,
                        deliveredAt = now,
                        createdAt = now,
                    )
                )
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
        exampleDrafts: AssignmentExampleDrafts?,
    ): Mono<AssignmentDetailResponse> {
        val targetWeekNo = parsedWeekNo?.value ?: assignment.weekNo
        val targetOrderInWeek = request.orderInWeek ?: assignment.orderInWeek
        val targetStartAt = request.startAt ?: assignment.startAt
        val targetEndAt = request.endAt ?: assignment.endAt
        if (targetEndAt.isBefore(targetStartAt)) {
            return Mono.error(ResponseStatusException(HttpStatus.BAD_REQUEST, "endAt은 startAt보다 빠를 수 없습니다."))
        }

        val metadata = request.metadata?.toEntity() ?: assignment.metadata

        val candidate = assignment.copy(
            weekNo = targetWeekNo,
            orderInWeek = targetOrderInWeek,
            startAt = targetStartAt,
            endAt = targetEndAt,
            metadata = metadata,
            updatedAt = Instant.now(),
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
                    .zipWith(loadOrReplaceExamples(parsedAssignmentId, exampleDrafts))
                    .map { tuple ->
                        toAssignmentDetailResponse(course.slug, saved, tuple.t1, tuple.t2)
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

    private fun loadOrReplaceExamples(
        assignmentId: AssignmentId,
        drafts: AssignmentExampleDrafts?,
    ): Mono<List<AssignmentExampleResponse>> {
        if (drafts == null) {
            return assignmentExampleRepository.findAllByAssignmentIdOrderBySeq(assignmentId.value)
                .map { AssignmentExampleResponse(it.seq, it.inputText, it.outputText, it.description) }
                .collectList()
        }
        return assignmentExampleRepository.deleteAllByAssignmentIdIn(listOf(assignmentId.value))
            .then(saveExamples(assignmentId, drafts))
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
            assignmentExampleRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)).then(),
            assignmentDeliveryRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)).then(),
            assignmentRepository.deleteById(assignmentId).then(),
        )
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

    private fun parseWeekNo(raw: Int): WeekNo =
        parseOrBadRequest { WeekNo.from(raw) }

    private fun parseAssignmentId(raw: String): AssignmentId =
        parseOrBadRequest { AssignmentId.from(raw) }

    private fun parseRequirementDrafts(requests: List<CreateAssignmentRequirementRequest>): AssignmentRequirementDrafts =
        parseOrBadRequest { AssignmentRequirementDrafts.fromRequests(requests) }

    private fun parseExampleDrafts(requests: List<CreateAssignmentExampleRequest>): AssignmentExampleDrafts =
        parseOrBadRequest { AssignmentExampleDrafts.fromRequests(requests) }

    private fun resolveCreateRequest(request: CreateAssignmentRequest): Mono<CreateAssignmentRequest> {
        val source = request.metadata.source() ?: return Mono.just(validateResolvedCreateRequest(request))
        return assignmentImportService.import(source)
            .map { imported ->
                val resolvedExamples = if (request.examples.isEmpty()) imported.examples else request.examples
                validateResolvedCreateRequest(
                    request.copy(
                        metadata = request.metadata.withImportedContent(imported),
                        examples = resolvedExamples,
                    )
                )
            }
    }

    private fun resolveUpdateRequest(request: UpdateAssignmentRequest): Mono<UpdateAssignmentRequest> {
        val metadata = request.metadata ?: return Mono.just(request)
        val source = metadata.source() ?: return Mono.just(validateResolvedUpdateRequest(request))
        return assignmentImportService.import(source)
            .map { imported ->
                val resolvedExamples = if (request.examples.isNullOrEmpty()) imported.examples else request.examples
                validateResolvedUpdateRequest(
                    request.copy(
                        metadata = metadata.withImportedContent(imported),
                        examples = resolvedExamples,
                    )
                )
            }
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

    private fun toEnrollmentResponse(enrollment: CourseEnrollment): CourseEnrollmentResponse = CourseEnrollmentResponse(
        id = requireNotNull(enrollment.id),
        userId = enrollment.userId,
        status = enrollment.status,
        joinedAt = enrollment.joinedAt,
        droppedAt = enrollment.droppedAt,
        bannedAt = enrollment.bannedAt,
        banReason = enrollment.banReason,
        updatedAt = enrollment.updatedAt,
    )

    private fun toAssignmentDetailResponse(
        courseSlug: String,
        assignment: Assignment,
        requirements: List<AssignmentRequirementResponse>,
        examples: List<AssignmentExampleResponse>,
    ): AssignmentDetailResponse = AssignmentDetailResponse(
        id = requireNotNull(assignment.id),
        courseSlug = courseSlug,
        weekNo = assignment.weekNo,
        orderInWeek = assignment.orderInWeek,
        startAt = assignment.startAt,
        endAt = assignment.endAt,
        status = assignment.status,
        publishedAt = assignment.publishedAt,
        metadata = assignment.metadata.toResponse(),
        requirements = requirements,
        examples = examples,
    )
}
