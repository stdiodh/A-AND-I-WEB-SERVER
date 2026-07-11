package com.example.aandi_post_web_server.course.application.service

import com.example.aandi_post_web_server.assignment.application.service.AssignmentQueryService
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentSummaryResponse
import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentPublicationPolicy
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRepository
import com.example.aandi_post_web_server.course.domain.model.AssignmentId
import com.example.aandi_post_web_server.course.domain.model.CourseId
import com.example.aandi_post_web_server.course.domain.model.CourseSlug
import com.example.aandi_post_web_server.course.domain.model.UserId
import com.example.aandi_post_web_server.course.api.dto.CourseEnrollmentResponse
import com.example.aandi_post_web_server.course.api.dto.CourseOutlineAssignmentItemResponse
import com.example.aandi_post_web_server.course.api.dto.CourseOutlineHeaderResponse
import com.example.aandi_post_web_server.course.api.dto.CourseOutlineResponse
import com.example.aandi_post_web_server.course.api.dto.CourseResponse
import com.example.aandi_post_web_server.course.api.dto.CourseWeekResponse
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseEnrollment
import com.example.aandi_post_web_server.course.entity.CourseWeek
import com.example.aandi_post_web_server.course.domain.model.EnrollmentStatus
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseEnrollmentRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseWeekRepository
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
    private val assignmentQueryService: AssignmentQueryService,
    private val clock: Clock = Clock.systemUTC(),
    private val assignmentPublicationPolicy: AssignmentPublicationPolicy = AssignmentPublicationPolicy(clock),
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
    ): Flux<AssignmentSummaryResponse> =
        assignmentQueryService.getAssignmentsByWeek(
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
    ): Flux<AssignmentSummaryResponse> =
        assignmentQueryService.getAssignments(courseSlug, weekNo, status, userId)

    fun getAdminAssignments(
        courseSlug: String,
        weekNo: Int?,
        status: AssignmentStatus?,
    ): Flux<AssignmentSummaryResponse> =
        assignmentQueryService.getAdminAssignments(courseSlug, weekNo, status)

    fun getAssignmentDetail(
        courseSlug: String,
        assignmentId: String,
        userId: String,
    ): Mono<AssignmentDetailResponse> =
        assignmentQueryService.getAssignmentDetail(courseSlug, assignmentId, userId)

    fun getAdminAssignmentDetail(
        courseSlug: String,
        assignmentId: String,
    ): Mono<AssignmentDetailResponse> =
        assignmentQueryService.getAdminAssignmentDetail(courseSlug, assignmentId)

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

    private fun parseCourseSlug(raw: String): CourseSlug =
        parseOrBadRequest { CourseSlug.from(raw) }

    private fun parseCourseId(raw: String): CourseId =
        parseOrBadRequest { CourseId.from(raw) }

    private fun parseUserId(raw: String): UserId =
        parseOrBadRequest { UserId.from(raw) }

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
        effectivePublication(assignment).status == AssignmentStatus.PUBLISHED

    private fun effectivePublication(assignment: Assignment, now: Instant = assignmentPublicationPolicy.now()) =
        assignmentPublicationPolicy.resolve(
            status = assignment.status,
            startAt = assignment.startAt,
            publishedAt = assignment.publishedAt,
            now = now,
        )

}
