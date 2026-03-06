package com.example.aandi_post_web_server.course.service

import com.example.aandi_post_web_server.assignment.dtos.AssignmentDeliveryResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentSummaryResponse
import com.example.aandi_post_web_server.assignment.dtos.CreateAssignmentRequest
import com.example.aandi_post_web_server.assignment.dtos.PublishAssignmentResponse
import com.example.aandi_post_web_server.assignment.dtos.TriggerDeliveriesResponse
import com.example.aandi_post_web_server.assignment.enum.AssignmentDeliveryStatus
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import com.example.aandi_post_web_server.course.dtos.CourseEnrollmentResponse
import com.example.aandi_post_web_server.course.dtos.CourseResponse
import com.example.aandi_post_web_server.course.dtos.CourseWeekResponse
import com.example.aandi_post_web_server.course.dtos.CreateCourseRequest
import com.example.aandi_post_web_server.course.dtos.CreateCourseWeekRequest
import com.example.aandi_post_web_server.course.dtos.EnrollCourseRequest
import com.example.aandi_post_web_server.course.dtos.UpdateCourseRequest
import com.example.aandi_post_web_server.course.dtos.UpdateEnrollmentRequest
import com.example.aandi_post_web_server.course.enum.CoursePhase
import com.example.aandi_post_web_server.course.enum.CourseStatus
import com.example.aandi_post_web_server.course.enum.UserTrack
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

    fun archiveCourse(courseSlug: String): Mono<Void> =
        courseCommandService.archiveCourse(courseSlug)

    fun getCourse(courseSlug: String, userId: String): Mono<CourseResponse> =
        courseQueryService.getCourse(courseSlug, userId)

    fun getCourses(
        status: CourseStatus?,
        phase: CoursePhase?,
        track: UserTrack?,
        userId: String,
    ): Flux<CourseResponse> =
        courseQueryService.getCourses(status, phase, track, userId)

    fun enrollMember(courseSlug: String, request: EnrollCourseRequest): Mono<CourseEnrollmentResponse> =
        courseCommandService.enrollMember(courseSlug, request)

    fun updateEnrollmentStatus(
        courseSlug: String,
        userId: String,
        request: UpdateEnrollmentRequest,
    ): Mono<CourseEnrollmentResponse> =
        courseCommandService.updateEnrollmentStatus(courseSlug, userId, request)

    fun getEnrollments(courseSlug: String): Flux<CourseEnrollmentResponse> =
        courseQueryService.getEnrollments(courseSlug)

    fun createWeek(courseSlug: String, request: CreateCourseWeekRequest): Mono<CourseWeekResponse> =
        courseCommandService.createWeek(courseSlug, request)

    fun getWeeks(courseSlug: String, userId: String): Flux<CourseWeekResponse> =
        courseQueryService.getWeeks(courseSlug, userId)

    fun createAssignment(
        courseSlug: String,
        request: CreateAssignmentRequest,
        createdBy: String,
    ): Mono<AssignmentDetailResponse> =
        courseCommandService.createAssignment(courseSlug, request, createdBy)

    fun publishAssignment(courseSlug: String, assignmentId: String): Mono<PublishAssignmentResponse> =
        courseCommandService.publishAssignment(courseSlug, assignmentId)

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

    fun triggerDeliveries(courseSlug: String, assignmentId: String): Mono<TriggerDeliveriesResponse> =
        courseCommandService.triggerDeliveries(courseSlug, assignmentId)

    fun getDeliveries(
        courseSlug: String,
        assignmentId: String,
        status: AssignmentDeliveryStatus?,
    ): Flux<AssignmentDeliveryResponse> =
        courseQueryService.getDeliveries(courseSlug, assignmentId, status)
}
