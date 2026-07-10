package com.example.aandi_post_web_server.course.application.service

import com.example.aandi_post_web_server.assignment.api.dto.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.api.dto.CopyAssignmentRequest
import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentRequest
import com.example.aandi_post_web_server.assignment.api.dto.UpdateAssignmentRequest
import com.example.aandi_post_web_server.assignment.application.service.AssignmentCommandService
import com.example.aandi_post_web_server.course.api.dto.CourseEnrollmentResponse
import com.example.aandi_post_web_server.course.api.dto.CourseMetadataResponse
import com.example.aandi_post_web_server.course.api.dto.CourseResponse
import com.example.aandi_post_web_server.course.api.dto.CreateCourseRequest
import com.example.aandi_post_web_server.course.api.dto.EnrollCourseRequest
import com.example.aandi_post_web_server.course.api.dto.UpdateCourseRequest
import com.example.aandi_post_web_server.course.api.dto.UpdateEnrollmentRequest
import com.example.aandi_post_web_server.course.domain.model.CourseId
import com.example.aandi_post_web_server.course.domain.model.CourseSlug
import com.example.aandi_post_web_server.course.domain.model.CourseStatus
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseMetadata
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseEnrollmentRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseWeekRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import java.time.Instant

@Service
class CourseCommandService(
    private val courseRepository: CourseRepository,
    private val courseEnrollmentRepository: CourseEnrollmentRepository,
    private val courseWeekRepository: CourseWeekRepository,
    private val courseEnrollmentCommandService: CourseEnrollmentCommandService,
    private val assignmentCommandService: AssignmentCommandService,
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
                assignmentCommandService.deleteAllByCourseId(courseId.value)
                    .then(deleteCourseRelations(courseId.value))
                    .then(courseRepository.deleteById(courseId.value))
            }
            .then()
    }

    fun enrollMember(courseSlug: String, request: EnrollCourseRequest): Mono<CourseEnrollmentResponse> =
        courseEnrollmentCommandService.enrollMember(courseSlug, request)

    fun updateEnrollmentStatus(
        courseSlug: String,
        userId: String,
        request: UpdateEnrollmentRequest,
    ): Mono<CourseEnrollmentResponse> =
        courseEnrollmentCommandService.updateEnrollmentStatus(courseSlug, userId, request)

    fun deleteEnrollment(courseSlug: String, userId: String): Mono<Void> =
        courseEnrollmentCommandService.deleteEnrollment(courseSlug, userId)

    fun createAssignment(
        courseSlug: String,
        request: CreateAssignmentRequest,
        createdBy: String,
    ): Mono<AssignmentDetailResponse> =
        assignmentCommandService.createAssignment(courseSlug, request, createdBy)

    fun copyAssignment(
        targetCourseSlug: String,
        request: CopyAssignmentRequest,
        createdBy: String,
    ): Mono<AssignmentDetailResponse> =
        assignmentCommandService.copyAssignment(targetCourseSlug, request, createdBy)

    fun updateAssignment(
        courseSlug: String,
        assignmentId: String,
        request: UpdateAssignmentRequest,
    ): Mono<AssignmentDetailResponse> =
        assignmentCommandService.updateAssignment(courseSlug, assignmentId, request)

    fun deleteAssignment(courseSlug: String, assignmentId: String): Mono<Void> =
        assignmentCommandService.deleteAssignment(courseSlug, assignmentId)

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

    private fun deleteCourseRelations(courseId: String): Mono<Void> =
        Mono.whenDelayError(
            courseWeekRepository.deleteAllByCourseId(courseId).then(),
            courseEnrollmentRepository.deleteAllByCourseId(courseId).then(),
        )

    private fun findCourseBySlug(slug: CourseSlug): Mono<Course> =
        courseRepository.findBySlug(slug.value)
            .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "코스를 찾을 수 없습니다: ${slug.value}")))

    private fun parseCourseSlug(raw: String): CourseSlug =
        parseOrBadRequest { CourseSlug.from(raw) }

    private fun parseCourseId(raw: String): CourseId =
        parseOrBadRequest { CourseId.from(raw) }

    private fun <T> parseOrBadRequest(block: () -> T): T =
        runCatching(block).getOrElse { error ->
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, error.message ?: "잘못된 요청입니다.")
        }

    private fun toCourseResponse(course: Course): CourseResponse = CourseResponse(
        id = requireNotNull(course.id),
        slug = course.slug,
        fieldTag = course.fieldTag,
        startDate = course.startDate,
        endDate = course.endDate,
        metadata = CourseMetadataResponse(
            title = course.metadata.title,
            description = course.metadata.description,
            phase = course.metadata.phase,
            attributes = course.metadata.attributes,
        ),
        status = course.status,
        createdAt = course.createdAt,
        updatedAt = course.updatedAt,
    )
}
