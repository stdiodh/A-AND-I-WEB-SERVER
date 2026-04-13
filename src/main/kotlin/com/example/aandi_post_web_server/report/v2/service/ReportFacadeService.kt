package com.example.aandi_post_web_server.report.v2.service

import com.example.aandi_post_web_server.assignment.dtos.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentSummaryResponse
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import com.example.aandi_post_web_server.course.service.CourseV1Service
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class ReportFacadeService(
    private val courseV1Service: CourseV1Service,
) {
    fun getAssignments(
        courseSlug: String,
        weekNo: Int?,
        status: AssignmentStatus?,
        authentication: Authentication,
    ): Flux<AssignmentSummaryResponse> = courseV1Service.getAssignments(
        courseSlug = courseSlug,
        weekNo = weekNo,
        status = status,
        userId = authentication.name,
    )

    fun getAssignmentsByWeek(
        courseSlug: String,
        weekNo: Int,
        status: AssignmentStatus?,
        authentication: Authentication,
    ): Flux<AssignmentSummaryResponse> = courseV1Service.getAssignmentsByWeek(
        courseSlug = courseSlug,
        weekNo = weekNo,
        status = status,
        userId = authentication.name,
    )

    fun getAssignmentDetail(
        courseSlug: String,
        assignmentId: String,
        authentication: Authentication,
    ): Mono<AssignmentDetailResponse> = courseV1Service.getAssignmentDetail(
        courseSlug = courseSlug,
        assignmentId = assignmentId,
        userId = authentication.name,
    )
}
