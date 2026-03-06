package com.example.aandi_post_web_server.course.controller

import com.example.aandi_post_web_server.assignment.dtos.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentSummaryResponse
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import com.example.aandi_post_web_server.common.openapi.ApiEnvelope
import com.example.aandi_post_web_server.course.dtos.CourseResponse
import com.example.aandi_post_web_server.course.dtos.CourseWeekResponse
import com.example.aandi_post_web_server.course.enum.CoursePhase
import com.example.aandi_post_web_server.course.enum.CourseStatus
import com.example.aandi_post_web_server.course.enum.UserTrack
import com.example.aandi_post_web_server.course.service.CourseV1Service
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@Tag(name = "코스 조회 API", description = "트랙/과정/코스/과제 조회 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/v1/courses")
class CourseQueryV1Controller(
    private val courseV1Service: CourseV1Service,
) {

    @Operation(
        summary = "코스 목록 조회",
        description = "track(FL/SP/NO), status, phase 조건으로 코스 목록을 조회합니다. track=NO면 빈 목록을 반환합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "400", description = "잘못된 enum 파라미터(track/status/phase)", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
        ],
    )
    @GetMapping
    fun getCourses(
        @Parameter(description = "코스 상태", example = "ACTIVE")
        @RequestParam(required = false) status: CourseStatus?,
        @Parameter(description = "과정 단계", example = "BASIC")
        @RequestParam(required = false) phase: CoursePhase?,
        @Parameter(description = "유저 트랙", example = "FL")
        @RequestParam(required = false) track: UserTrack?,
        authentication: Authentication,
    ): Mono<ApiEnvelope<List<CourseResponse>>> {
        return courseV1Service
            .getCourses(status, phase, track, authentication.name)
            .collectList()
            .map { ApiEnvelope.success(it) }
    }

    @Operation(summary = "코스 상세 조회", description = "courseSlug로 단일 코스를 조회합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "404", description = "코스를 찾을 수 없음", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
        ],
    )
    @GetMapping("/{courseSlug}")
    fun getCourse(
        @Parameter(description = "코스 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        authentication: Authentication,
    ): Mono<ApiEnvelope<CourseResponse>> {
        return courseV1Service.getCourse(courseSlug, authentication.name).map { ApiEnvelope.success(it) }
    }

    @Operation(summary = "코스 주차 목록 조회", description = "해당 코스의 주차 목록을 조회합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "404", description = "코스를 찾을 수 없음", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
        ],
    )
    @GetMapping("/{courseSlug}/weeks")
    fun getWeeks(
        @Parameter(description = "코스 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        authentication: Authentication,
    ): Mono<ApiEnvelope<List<CourseWeekResponse>>> {
        return courseV1Service
            .getWeeks(courseSlug, authentication.name)
            .collectList()
            .map { ApiEnvelope.success(it) }
    }

    @Operation(summary = "주차별 과제 목록 조회", description = "특정 주차의 과제 목록을 조회합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "400", description = "잘못된 weekNo/status", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "404", description = "코스를 찾을 수 없음", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
        ],
    )
    @GetMapping("/{courseSlug}/weeks/{weekNo}/assignments")
    fun getAssignmentsByWeek(
        @Parameter(description = "코스 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "주차 번호(1 이상)", example = "1")
        @PathVariable weekNo: Int,
        @Parameter(description = "과제 상태", example = "PUBLISHED")
        @RequestParam(required = false) status: AssignmentStatus?,
        authentication: Authentication,
    ): Mono<ApiEnvelope<List<AssignmentSummaryResponse>>> {
        return courseV1Service
            .getAssignmentsByWeek(
                courseSlug = courseSlug,
                weekNo = weekNo,
                status = status,
                userId = authentication.name,
            )
            .collectList()
            .map { ApiEnvelope.success(it) }
    }

    @Operation(summary = "코스 과제 목록 조회", description = "코스 전체 과제를 조회하며 week(또는 weekNo)/status 필터를 지원합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "400", description = "잘못된 week/weekNo/status", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "404", description = "코스를 찾을 수 없음", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
        ],
    )
    @GetMapping("/{courseSlug}/assignments")
    fun getAssignments(
        @Parameter(description = "코스 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "주차 번호(레거시 파라미터, 옵션)", example = "1")
        @RequestParam(name = "week", required = false) week: Int?,
        @Parameter(description = "주차 번호(신규 파라미터, 옵션)", example = "1")
        @RequestParam(name = "weekNo", required = false) weekNo: Int?,
        @Parameter(description = "과제 상태(옵션)", example = "PUBLISHED")
        @RequestParam(required = false) status: AssignmentStatus?,
        authentication: Authentication,
    ): Mono<ApiEnvelope<List<AssignmentSummaryResponse>>> {
        val resolvedWeek = week ?: weekNo
        return courseV1Service
            .getAssignments(
                courseSlug = courseSlug,
                weekNo = resolvedWeek,
                status = status,
                userId = authentication.name,
            )
            .collectList()
            .map { ApiEnvelope.success(it) }
    }

    @Operation(summary = "과제 상세 조회", description = "courseSlug와 assignmentId로 과제 상세 정보를 조회합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "404", description = "코스 또는 과제를 찾을 수 없음", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
        ],
    )
    @GetMapping("/{courseSlug}/assignments/{assignmentId}")
    fun getAssignmentDetail(
        @Parameter(description = "코스 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "과제 ID", example = "assignment-1")
        @PathVariable assignmentId: String,
        authentication: Authentication,
    ): Mono<ApiEnvelope<AssignmentDetailResponse>> {
        return courseV1Service.getAssignmentDetail(
            courseSlug = courseSlug,
            assignmentId = assignmentId,
            userId = authentication.name,
        ).map { ApiEnvelope.success(it) }
    }

    @Operation(summary = "과제 ID로 코스 조회", description = "assignmentId로 과제가 속한 코스를 조회합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "404", description = "과제 또는 코스를 찾을 수 없음", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
        ],
    )
    @GetMapping("/assignments/{assignmentId}/course")
    fun getAssignmentCourse(
        @Parameter(description = "과제 ID", example = "assignment-1")
        @PathVariable assignmentId: String,
        authentication: Authentication,
    ): Mono<ApiEnvelope<CourseResponse>> {
        return courseV1Service.getAssignmentCourse(assignmentId, authentication.name).map { ApiEnvelope.success(it) }
    }
}
