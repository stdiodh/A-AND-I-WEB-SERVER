package com.example.aandi_post_web_server.course.application.service

import com.example.aandi_post_web_server.assignment.api.dto.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentSummaryResponse
import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentRequest
import com.example.aandi_post_web_server.assignment.api.dto.UpdateAssignmentRequest
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import com.example.aandi_post_web_server.course.api.dto.CourseEnrollmentResponse
import com.example.aandi_post_web_server.course.api.dto.CourseOutlineResponse
import com.example.aandi_post_web_server.course.api.dto.CourseResponse
import com.example.aandi_post_web_server.course.api.dto.CourseWeekResponse
import com.example.aandi_post_web_server.course.api.dto.CreateCourseRequest
import com.example.aandi_post_web_server.course.api.dto.EnrollCourseRequest
import com.example.aandi_post_web_server.course.api.dto.UpdateCourseRequest
import com.example.aandi_post_web_server.course.api.dto.UpdateEnrollmentRequest
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class CourseV1Service(
    private val courseCommandService: CourseCommandService,
    private val courseQueryService: CourseQueryService,
) {

    fun getAdminCourses(): Flux<CourseResponse> =
        courseQueryService.getAdminCourses()

    fun createCourse(request: CreateCourseRequest): Mono<CourseResponse> =
        courseCommandService.createCourse(request)

    fun updateCourse(courseSlug: String, request: UpdateCourseRequest): Mono<CourseResponse> =
        courseCommandService.updateCourse(courseSlug, request)

    fun deleteCourse(courseSlug: String): Mono<Void> =
        courseCommandService.deleteCourse(courseSlug)

    fun getCourse(courseSlug: String, userId: String): Mono<CourseResponse> =
        courseQueryService.getCourse(courseSlug, userId)

    fun getCourseOutline(courseSlug: String, userId: String): Mono<CourseOutlineResponse> =
        courseQueryService.getCourseOutline(courseSlug, userId)

    fun getCourses(userId: String): Flux<CourseResponse> =
        courseQueryService.getCourses(userId)

    fun enrollMember(courseSlug: String, request: EnrollCourseRequest): Mono<CourseEnrollmentResponse> =
        courseCommandService.enrollMember(courseSlug, request)

    fun updateEnrollmentStatus(
        courseSlug: String,
        userId: String,
        request: UpdateEnrollmentRequest,
    ): Mono<CourseEnrollmentResponse> =
        courseCommandService.updateEnrollmentStatus(courseSlug, userId, request)

    fun deleteEnrollment(courseSlug: String, userId: String): Mono<Void> =
        courseCommandService.deleteEnrollment(courseSlug, userId)

    fun getEnrollments(courseSlug: String): Flux<CourseEnrollmentResponse> =
        courseQueryService.getEnrollments(courseSlug)

    fun getWeeks(courseSlug: String, userId: String): Flux<CourseWeekResponse> =
        courseQueryService.getWeeks(courseSlug, userId)

    fun createAssignment(
        courseSlug: String,
        request: CreateAssignmentRequest,
        createdBy: String,
    ): Mono<AssignmentDetailResponse> =
        courseCommandService.createAssignment(courseSlug, request, createdBy)

    fun updateAssignment(
        courseSlug: String,
        assignmentId: String,
        request: UpdateAssignmentRequest,
    ): Mono<AssignmentDetailResponse> =
        courseCommandService.updateAssignment(courseSlug, assignmentId, request)

    fun deleteAssignment(courseSlug: String, assignmentId: String): Mono<Void> =
        courseCommandService.deleteAssignment(courseSlug, assignmentId)

    fun getAdminAssignments(
        courseSlug: String,
        weekNo: Int?,
        status: AssignmentStatus?,
    ): Flux<AssignmentSummaryResponse> =
        courseQueryService.getAdminAssignments(courseSlug, weekNo, status)

    fun getAdminAssignmentDetail(
        courseSlug: String,
        assignmentId: String,
    ): Mono<AssignmentDetailResponse> =
        courseQueryService.getAdminAssignmentDetail(courseSlug, assignmentId)

    fun getAssignmentsByWeek(
        courseSlug: String,
        weekNo: Int,
        status: AssignmentStatus?,
        userId: String,
    ): Flux<AssignmentSummaryResponse> =
        courseQueryService.getAssignmentsByWeek(courseSlug, weekNo, status, userId)

    fun getAssignments(
        courseSlug: String,
        weekNo: Int?,
        status: AssignmentStatus?,
        userId: String,
    ): Flux<AssignmentSummaryResponse> =
        courseQueryService.getAssignments(courseSlug, weekNo, status, userId)

    fun getAssignmentDetail(
        courseSlug: String,
        assignmentId: String,
        userId: String,
    ): Mono<AssignmentDetailResponse> =
        courseQueryService.getAssignmentDetail(courseSlug, assignmentId, userId)

    fun getAssignmentCourse(assignmentId: String, userId: String): Mono<CourseResponse> =
        courseQueryService.getAssignmentCourse(assignmentId, userId)
}
