package com.example.aandi_post_web_server.course.service

import com.example.aandi_post_web_server.assignment.domain.toDetailResponse
import com.example.aandi_post_web_server.assignment.domain.toResponse
import com.example.aandi_post_web_server.assignment.domain.toSubmissionConfigResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentExampleResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentRequirementResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentSubmissionConfigResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentSummaryResponse
import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import com.example.aandi_post_web_server.assignment.repository.AssignmentExampleRepository
import com.example.aandi_post_web_server.assignment.repository.AssignmentRepository
import com.example.aandi_post_web_server.assignment.repository.AssignmentRequirementRepository
import com.example.aandi_post_web_server.course.domain.AssignmentId
import com.example.aandi_post_web_server.course.domain.CourseId
import com.example.aandi_post_web_server.course.domain.CourseSlug
import com.example.aandi_post_web_server.course.domain.UserId
import com.example.aandi_post_web_server.course.domain.WeekNo
import com.example.aandi_post_web_server.course.dtos.CourseEnrollmentResponse
import com.example.aandi_post_web_server.course.dtos.CourseOutlineAssignmentItemResponse
import com.example.aandi_post_web_server.course.dtos.CourseOutlineHeaderResponse
import com.example.aandi_post_web_server.course.dtos.CourseOutlineResponse
import com.example.aandi_post_web_server.course.dtos.CourseResponse
import com.example.aandi_post_web_server.course.dtos.CourseWeekResponse
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseEnrollment
import com.example.aandi_post_web_server.course.entity.CourseWeek
import com.example.aandi_post_web_server.course.enum.EnrollmentStatus
import com.example.aandi_post_web_server.course.repository.CourseEnrollmentRepository
import com.example.aandi_post_web_server.course.repository.CourseRepository
import com.example.aandi_post_web_server.course.repository.CourseWeekRepository
import org.springframework.http.HttpStatus
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Clock
import java.time.Instant

@Service
class CourseQueryService(
    private val courseRepository: CourseRepository,
    private val courseEnrollmentRepository: CourseEnrollmentRepository,
    private val courseWeekRepository: CourseWeekRepository,
    private val assignmentRepository: AssignmentRepository,
    private val assignmentRequirementRepository: AssignmentRequirementRepository,
    private val assignmentExampleRepository: AssignmentExampleRepository,
    private val clock: Clock = Clock.systemUTC(),
) {

    fun getAdminCourses(): Flux<CourseResponse> =
        courseRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
            .map(::toCourseResponse)

    fun getCourse(courseSlug: String, userId: String): Mono<CourseResponse> {
        val slug = parseCourseSlug(courseSlug)
        val parsedUserId = parseUserId(userId)
        return findAccessibleCourseBySlug(slug, parsedUserId).map(::toCourseResponse)
    }

    fun getCourseOutline(courseSlug: String, userId: String): Mono<CourseOutlineResponse> {
        val slug = parseCourseSlug(courseSlug)
        val parsedUserId = parseUserId(userId)

        return findAccessibleCourseBySlug(slug, parsedUserId)
            .flatMap { course ->
                val courseId = parseCourseId(requireNotNull(course.id))
                assignmentRepository.findAllByCourseId(courseId.value)
                    .filter { assignment -> isVisibleToUser(assignment) }
                    .sort(compareBy<Assignment> { it.weekNo }.thenBy { it.orderInWeek })
                    .collectList()
                    .map { assignments -> toCourseOutlineResponse(course, assignments) }
            }
    }

    fun getCourses(userId: String): Flux<CourseResponse> {
        val parsedUserId = parseUserId(userId)
        return loadEnrolledCourses(parsedUserId).map(::toCourseResponse)
    }

    fun getEnrollments(courseSlug: String): Flux<CourseEnrollmentResponse> {
        val slug = parseCourseSlug(courseSlug)
        return findCourseBySlug(slug)
            .flatMapMany { course ->
                val courseId = parseCourseId(requireNotNull(course.id))
                courseEnrollmentRepository.findAllByCourseId(courseId.value)
                    .map { enrollment -> toEnrollmentResponse(course.slug, enrollment) }
            }
            .sort(compareByDescending<CourseEnrollmentResponse> { it.updatedAt })
    }

    fun getWeeks(courseSlug: String, userId: String): Flux<CourseWeekResponse> {
        val slug = parseCourseSlug(courseSlug)
        val parsedUserId = parseUserId(userId)
        return findAccessibleCourseBySlug(slug, parsedUserId)
            .flatMapMany { course ->
                val courseId = parseCourseId(requireNotNull(course.id))
                courseWeekRepository.findAllByCourseId(courseId.value)
            }
            .sort(compareBy<CourseWeek> { it.weekNo })
            .map(::toWeekResponse)
    }

    fun getAssignmentsByWeek(
        courseSlug: String,
        weekNo: Int,
        status: AssignmentStatus?,
        userId: String,
    ): Flux<AssignmentSummaryResponse> {
        return getAssignments(
            courseSlug = courseSlug,
            weekNo = weekNo,
            status = status,
            userId = userId,
        )
    }

    fun getAssignments(
        courseSlug: String,
        weekNo: Int?,
        status: AssignmentStatus?,
        userId: String,
    ): Flux<AssignmentSummaryResponse> {
        val slug = parseCourseSlug(courseSlug)
        val parsedWeekNo = weekNo?.let { parseWeekNo(it) }
        val parsedUserId = parseUserId(userId)
        resolveVisibleAssignmentStatus(status)

        return findAccessibleCourseBySlug(slug, parsedUserId)
            .flatMapMany { course ->
                val courseId = parseCourseId(requireNotNull(course.id))
                findAssignmentsByFilter(courseId, parsedWeekNo)
            }
            .filter { assignment -> isVisibleToUser(assignment) }
            .sort(compareBy<Assignment> { it.weekNo }.thenBy { it.orderInWeek })
            .flatMapSequential { assignment -> loadAssignmentSummary(assignment) }
    }

    fun getAdminAssignments(
        courseSlug: String,
        weekNo: Int?,
        status: AssignmentStatus?,
    ): Flux<AssignmentSummaryResponse> {
        val slug = parseCourseSlug(courseSlug)
        val parsedWeekNo = weekNo?.let { parseWeekNo(it) }
        return findCourseBySlug(slug)
            .flatMapMany { course ->
                val courseId = parseCourseId(requireNotNull(course.id))
                findAdminAssignmentsByFilter(courseId, parsedWeekNo)
            }
            .filter { assignment -> status == null || effectiveAssignmentStatus(assignment) == status }
            .sort(compareBy<Assignment> { it.weekNo }.thenBy { it.orderInWeek })
            .flatMapSequential { assignment -> loadAssignmentSummary(assignment) }
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
                assignmentRepository.findByIdAndCourseId(parsedAssignmentId.value, courseId.value)
                    .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "과제를 찾을 수 없습니다: ${parsedAssignmentId.value}")))
                    .flatMap { assignment -> ensureVisibleToUser(assignment, parsedAssignmentId) }
                    .flatMap { assignment -> loadAssignmentDetail(course.slug, assignment, parsedAssignmentId) }
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
                assignmentRepository.findByIdAndCourseId(parsedAssignmentId.value, courseId.value)
                    .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "과제를 찾을 수 없습니다: ${parsedAssignmentId.value}")))
                    .flatMap { assignment -> loadAssignmentDetail(course.slug, assignment, parsedAssignmentId) }
            }
    }

    fun getAssignmentSubmissionConfig(
        courseSlug: String,
        assignmentId: String,
        userId: String,
    ): Mono<AssignmentSubmissionConfigResponse> {
        val slug = parseCourseSlug(courseSlug)
        val parsedAssignmentId = parseAssignmentId(assignmentId)
        val parsedUserId = parseUserId(userId)

        return findAccessibleCourseBySlug(slug, parsedUserId)
            .flatMap { course ->
                val courseId = parseCourseId(requireNotNull(course.id))
                assignmentRepository.findByIdAndCourseId(parsedAssignmentId.value, courseId.value)
                    .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "과제를 찾을 수 없습니다: ${parsedAssignmentId.value}")))
                    .flatMap { assignment -> ensureVisibleToUser(assignment, parsedAssignmentId) }
                    .map { assignment ->
                        assignment.metadata.toSubmissionConfigResponse(
                            assignmentId = requireNotNull(assignment.id),
                            courseSlug = course.slug,
                        )
                    }
            }
    }

    fun getAdminAssignmentSubmissionConfig(
        courseSlug: String,
        assignmentId: String,
    ): Mono<AssignmentSubmissionConfigResponse> {
        val slug = parseCourseSlug(courseSlug)
        val parsedAssignmentId = parseAssignmentId(assignmentId)
        return findCourseBySlug(slug)
            .flatMap { course ->
                val courseId = parseCourseId(requireNotNull(course.id))
                assignmentRepository.findByIdAndCourseId(parsedAssignmentId.value, courseId.value)
                    .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "과제를 찾을 수 없습니다: ${parsedAssignmentId.value}")))
                    .map { assignment ->
                        assignment.metadata.toSubmissionConfigResponse(
                            assignmentId = requireNotNull(assignment.id),
                            courseSlug = course.slug,
                        )
                    }
            }
    }

    fun getAssignmentCourse(assignmentId: String, userId: String): Mono<CourseResponse> {
        val parsedAssignmentId = parseAssignmentId(assignmentId)
        val parsedUserId = parseUserId(userId)
        return assignmentRepository.findById(parsedAssignmentId.value)
            .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "과제를 찾을 수 없습니다: ${parsedAssignmentId.value}")))
            .flatMap { assignment -> ensureVisibleToUser(assignment, parsedAssignmentId) }
            .flatMap { assignment ->
                courseRepository.findById(assignment.courseId)
                    .switchIfEmpty(
                        Mono.error(
                            ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "코스를 찾을 수 없습니다: ${assignment.courseId}",
                            )
                        )
                    ).flatMap { course ->
                        val courseId = parseCourseId(requireNotNull(course.id))
                        ensureEnrolled(courseId, parsedUserId).thenReturn(course)
                    }
            }
            .map(::toCourseResponse)
    }

    private fun findAssignmentsByFilter(
        courseId: CourseId,
        weekNo: WeekNo?,
    ): Flux<Assignment> {
        if (weekNo != null) {
            return assignmentRepository.findAllByCourseIdAndWeekNo(courseId.value, weekNo.value)
        }
        return assignmentRepository.findAllByCourseId(courseId.value)
    }

    private fun findAdminAssignmentsByFilter(
        courseId: CourseId,
        weekNo: WeekNo?,
    ): Flux<Assignment> {
        if (weekNo != null) {
            return assignmentRepository.findAllByCourseIdAndWeekNo(courseId.value, weekNo.value)
        }
        return assignmentRepository.findAllByCourseId(courseId.value)
    }

    private fun loadEnrolledCourses(userId: UserId): Flux<Course> {
        return courseEnrollmentRepository.findAllByUserIdAndStatus(userId.value, EnrollmentStatus.ENABLED)
            .map { it.courseId }
            .distinct()
            .collectList()
            .flatMapMany { enrolledCourseIds ->
                if (enrolledCourseIds.isEmpty()) {
                    return@flatMapMany Flux.empty()
                }
                courseRepository.findAllById(enrolledCourseIds)
            }
    }

    private fun findCourseBySlug(slug: CourseSlug): Mono<Course> {
        return courseRepository.findBySlug(slug.value)
            .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "코스를 찾을 수 없습니다: ${slug.value}")))
    }

    private fun findAccessibleCourseBySlug(slug: CourseSlug, userId: UserId): Mono<Course> {
        return findCourseBySlug(slug)
            .flatMap { course ->
                val courseId = parseCourseId(requireNotNull(course.id))
                ensureEnrolled(courseId, userId).thenReturn(course)
            }
    }

    private fun ensureEnrolled(courseId: CourseId, userId: UserId): Mono<CourseEnrollment> {
        return courseEnrollmentRepository.findByCourseIdAndUserId(courseId.value, userId.value)
            .filter { enrollment -> enrollment.status == EnrollmentStatus.ENABLED }
            .switchIfEmpty(
                Mono.error(
                    ResponseStatusException(HttpStatus.NOT_FOUND, "조회 가능한 코스를 찾을 수 없습니다.")
                )
            )
    }

    private fun ensureVisibleToUser(assignment: Assignment, assignmentId: AssignmentId): Mono<Assignment> {
        if (!isVisibleToUser(assignment)) {
            return Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "과제를 찾을 수 없습니다: ${assignmentId.value}"))
        }
        return Mono.just(assignment)
    }

    private fun loadAssignmentDetail(
        courseSlug: String,
        assignment: Assignment,
        assignmentId: AssignmentId,
    ): Mono<AssignmentDetailResponse> =
        assignmentRequirementRepository
            .findAllByAssignmentIdOrderBySortOrder(assignmentId.value)
            .map { AssignmentRequirementResponse(it.sortOrder, it.requirementText) }
            .collectList()
            .zipWith(
                assignmentExampleRepository
                    .findAllByAssignmentIdOrderBySeq(assignmentId.value)
                    .map { AssignmentExampleResponse(it.seq, it.inputText, it.outputText) }
                    .collectList()
            )
            .map { tuple ->
                toAssignmentDetailResponse(courseSlug, assignment, effectiveAssignment(assignment), tuple.t1, tuple.t2)
            }

    private fun loadAssignmentSummary(assignment: Assignment): Mono<AssignmentSummaryResponse> {
        val assignmentId = requireNotNull(assignment.id)
        return assignmentRequirementRepository
            .findAllByAssignmentIdOrderBySortOrder(assignmentId)
            .map { AssignmentRequirementResponse(it.sortOrder, it.requirementText) }
            .collectList()
            .zipWith(
                assignmentExampleRepository
                    .findAllByAssignmentIdOrderBySeq(assignmentId)
                    .map { AssignmentExampleResponse(it.seq, it.inputText, it.outputText) }
                    .collectList()
            )
            .map { tuple ->
                toAssignmentSummaryResponse(assignment, effectiveAssignment(assignment), tuple.t1, tuple.t2)
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

    private fun toWeekResponse(week: CourseWeek): CourseWeekResponse = CourseWeekResponse(
        id = requireNotNull(week.id),
        weekNo = week.weekNo,
        title = week.title,
        startDate = week.startDate,
        endDate = week.endDate,
        createdAt = week.createdAt,
        updatedAt = week.updatedAt,
    )

    private fun toCourseOutlineResponse(course: Course, assignments: List<Assignment>): CourseOutlineResponse {
        val now = Instant.now(clock)
        val assignmentItems = assignments
            .sortedWith(compareBy<Assignment> { it.weekNo }.thenBy { it.orderInWeek })
            .map { assignment ->
                CourseOutlineAssignmentItemResponse(
                    assignmentId = requireNotNull(assignment.id),
                    weekNo = assignment.weekNo,
                    orderInWeek = assignment.orderInWeek,
                    title = assignment.metadata.title,
                    difficulty = assignment.metadata.difficulty,
                    startAt = assignment.startAt,
                    endAt = assignment.endAt,
                    checked = isChecked(now, assignment),
                )
            }

        return CourseOutlineResponse(
            course = CourseOutlineHeaderResponse(
                id = requireNotNull(course.id),
                slug = course.slug,
                fieldTag = course.fieldTag,
                title = course.metadata.title,
                description = course.metadata.description,
                phase = course.metadata.phase,
            ),
            totalAssignments = assignments.size,
            assignments = assignmentItems,
        )
    }

    private fun isChecked(now: Instant, assignment: Assignment): Boolean = now.isAfter(assignment.endAt)

    private fun isVisibleToUser(assignment: Assignment): Boolean =
        effectiveAssignmentStatus(assignment) == AssignmentStatus.PUBLISHED

    private fun effectiveAssignmentStatus(assignment: Assignment, now: Instant = Instant.now(clock)): AssignmentStatus {
        if (now >= assignment.startAt) {
            return AssignmentStatus.PUBLISHED
        }
        return AssignmentStatus.DRAFT
    }

    private fun effectiveAssignment(assignment: Assignment, now: Instant = Instant.now(clock)): Assignment =
        assignment.copy(
            status = effectiveAssignmentStatus(assignment, now),
            publishedAt = if (now >= assignment.startAt) assignment.publishedAt ?: assignment.startAt else null,
        )

    private fun toAssignmentSummaryResponse(
        assignment: Assignment,
        effectiveAssignment: Assignment,
        requirements: List<AssignmentRequirementResponse>,
        examples: List<AssignmentExampleResponse>,
    ): AssignmentSummaryResponse = AssignmentSummaryResponse(
        id = requireNotNull(assignment.id),
        weekNo = assignment.weekNo,
        orderInWeek = assignment.orderInWeek,
        startAt = assignment.startAt,
        endAt = assignment.endAt,
        status = effectiveAssignment.status,
        metadata = assignment.metadata.toResponse(requirements, examples),
    )

    private fun toAssignmentDetailResponse(
        courseSlug: String,
        assignment: Assignment,
        effectiveAssignment: Assignment,
        requirements: List<AssignmentRequirementResponse>,
        examples: List<AssignmentExampleResponse>,
    ): AssignmentDetailResponse = AssignmentDetailResponse(
        id = requireNotNull(assignment.id),
        courseSlug = courseSlug,
        weekNo = assignment.weekNo,
        orderInWeek = assignment.orderInWeek,
        startAt = assignment.startAt,
        endAt = assignment.endAt,
        status = effectiveAssignment.status,
        publishedAt = effectiveAssignment.publishedAt,
        metadata = assignment.metadata.toDetailResponse(requirements, examples),
    )
}
