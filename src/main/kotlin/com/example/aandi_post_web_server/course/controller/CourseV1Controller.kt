package com.example.aandi_post_web_server.course.controller

import com.example.aandi_post_web_server.assignment.dtos.AssignmentDeliveryResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.dtos.CreateAssignmentRequest
import com.example.aandi_post_web_server.assignment.dtos.PublishAssignmentResponse
import com.example.aandi_post_web_server.assignment.dtos.TriggerDeliveriesResponse
import com.example.aandi_post_web_server.assignment.enum.AssignmentDeliveryStatus
import com.example.aandi_post_web_server.common.openapi.ApiEnvelope
import com.example.aandi_post_web_server.course.dtos.CourseEnrollmentResponse
import com.example.aandi_post_web_server.course.dtos.CourseResponse
import com.example.aandi_post_web_server.course.dtos.CourseWeekResponse
import com.example.aandi_post_web_server.course.dtos.CreateCourseRequest
import com.example.aandi_post_web_server.course.dtos.CreateCourseWeekRequest
import com.example.aandi_post_web_server.course.dtos.EnrollCourseRequest
import com.example.aandi_post_web_server.course.dtos.UpdateCourseRequest
import com.example.aandi_post_web_server.course.dtos.UpdateEnrollmentRequest
import com.example.aandi_post_web_server.course.service.CourseV1Service
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@Tag(name = "코스 관리자 API", description = "관리자 전용 코스/수강/과제 관리 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/v1/admin/courses")
class CourseV1Controller(
    private val courseV1Service: CourseV1Service,
) {

    @Operation(summary = "전체 코스 목록 조회", description = "분야/수강신청 여부와 관계없이 모든 코스를 조회합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [Content(array = ArraySchema(schema = Schema(implementation = CourseResponse::class)))],
            ),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ApiErrorResponse::class))]),
        ],
    )
    @GetMapping
    fun getAdminCourses(): Flux<CourseResponse> = courseV1Service.getAdminCourses()

    @Operation(
        summary = "코스 생성",
        description = "관리자가 코스를 생성합니다. 필수 코어 필드는 slug/fieldTag/startDate/endDate이며, 상세 정보는 metadata로 관리합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "생성 성공", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "409", description = "slug 중복", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
        ],
    )
    @PostMapping
    fun createCourse(
        @Valid @RequestBody request: CreateCourseRequest,
    ): Mono<ApiEnvelope<CourseResponse>> =
        courseV1Service.createCourse(request).map { ApiEnvelope.success(it) }

    @Operation(
        summary = "코스 수정",
        description = "코스 코어 필드(fieldTag/startDate/endDate), metadata, status를 수정합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "수정 성공", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "404", description = "코스를 찾을 수 없음", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
        ],
    )
    @PatchMapping("/{courseSlug}")
    fun updateCourse(
        @Parameter(description = "코스 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @RequestBody request: UpdateCourseRequest,
    ): Mono<ApiEnvelope<CourseResponse>> =
        courseV1Service.updateCourse(courseSlug, request).map { ApiEnvelope.success(it) }

    @Operation(summary = "코스 아카이브", description = "코스를 ARCHIVED 상태로 변경합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "아카이브 성공", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "404", description = "코스를 찾을 수 없음", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
        ],
    )
    @DeleteMapping("/{courseSlug}")
    fun archiveCourse(
        @Parameter(description = "코스 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
    ): Mono<ApiEnvelope<Nothing?>> =
        courseV1Service.archiveCourse(courseSlug).thenReturn(ApiEnvelope.success(null))

    @Operation(summary = "수강생 등록/복구", description = "코스에 수강생을 ENROLLED 상태로 등록합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "등록 성공", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "404", description = "코스를 찾을 수 없음", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
        ],
    )
    @PostMapping("/{courseSlug}/enrollments")
    fun enrollMember(
        @Parameter(description = "코스 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Valid @RequestBody request: EnrollCourseRequest,
    ): Mono<ApiEnvelope<CourseEnrollmentResponse>> =
        courseV1Service.enrollMember(courseSlug, request).map { ApiEnvelope.success(it) }

    @Operation(summary = "수강 상태 변경", description = "ENROLLED/DROPPED/BANNED로 상태를 변경합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "변경 성공", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "400", description = "요청값 오류(예: BANNED + banReason 누락)", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "404", description = "코스 또는 수강생을 찾을 수 없음", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
        ],
    )
    @PatchMapping("/{courseSlug}/enrollments/{userId}")
    fun updateEnrollmentStatus(
        @Parameter(description = "코스 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "유저 ID", example = "user-1")
        @PathVariable userId: String,
        @RequestBody request: UpdateEnrollmentRequest,
    ): Mono<ApiEnvelope<CourseEnrollmentResponse>> =
        courseV1Service.updateEnrollmentStatus(courseSlug, userId, request).map { ApiEnvelope.success(it) }

    @Operation(summary = "수강생 목록 조회", description = "해당 코스의 수강생 목록을 조회합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "404", description = "코스를 찾을 수 없음", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
        ],
    )
    @GetMapping("/{courseSlug}/enrollments")
    fun getEnrollments(
        @Parameter(description = "코스 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
    ): Mono<ApiEnvelope<List<CourseEnrollmentResponse>>> =
        courseV1Service.getEnrollments(courseSlug).collectList().map { ApiEnvelope.success(it) }

    @Operation(summary = "주차 생성/수정", description = "주차를 생성하거나 같은 weekNo가 있으면 갱신합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "처리 성공", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "400", description = "요청값 오류", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "404", description = "코스를 찾을 수 없음", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
        ],
    )
    @PostMapping("/{courseSlug}/weeks")
    fun createWeek(
        @Parameter(description = "코스 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Valid @RequestBody request: CreateCourseWeekRequest,
    ): Mono<ApiEnvelope<CourseWeekResponse>> =
        courseV1Service.createWeek(courseSlug, request).map { ApiEnvelope.success(it) }

    @Operation(summary = "과제 생성", description = "코스 내부에 과제를 생성합니다. 생성 시 courseId/courseSlug가 함께 저장됩니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "생성 성공", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "400", description = "요청값 오류", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "404", description = "코스를 찾을 수 없음", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "409", description = "동일 코스/주차/순번 중복", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
        ],
    )
    @PostMapping("/{courseSlug}/assignments")
    fun createAssignment(
        @Parameter(description = "코스 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Valid @RequestBody request: CreateAssignmentRequest,
        authentication: Authentication,
    ): Mono<ApiEnvelope<AssignmentDetailResponse>> =
        courseV1Service.createAssignment(courseSlug, request, authentication.name).map { ApiEnvelope.success(it) }

    @Operation(summary = "과제 게시", description = "DRAFT 과제를 PUBLISHED 상태로 게시합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "게시 성공", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "404", description = "코스 또는 과제를 찾을 수 없음", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "422", description = "상태 전이 불가(예: ARCHIVED)", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
        ],
    )
    @PostMapping("/{courseSlug}/assignments/{assignmentId}/publish")
    fun publishAssignment(
        @Parameter(description = "코스 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "과제 ID", example = "assignment-1")
        @PathVariable assignmentId: String,
    ): Mono<ApiEnvelope<PublishAssignmentResponse>> =
        courseV1Service.publishAssignment(courseSlug, assignmentId).map { ApiEnvelope.success(it) }

    @Operation(summary = "과제 배포 트리거", description = "ENROLLED 대상에게 과제 배포를 수행합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "배포 처리 성공", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "404", description = "코스 또는 과제를 찾을 수 없음", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "422", description = "PUBLISHED 상태가 아닌 과제", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
        ],
    )
    @PostMapping("/{courseSlug}/assignments/{assignmentId}/deliveries")
    fun triggerDeliveries(
        @Parameter(description = "코스 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "과제 ID", example = "assignment-1")
        @PathVariable assignmentId: String,
    ): Mono<ApiEnvelope<TriggerDeliveriesResponse>> =
        courseV1Service.triggerDeliveries(courseSlug, assignmentId).map { ApiEnvelope.success(it) }

    @Operation(summary = "배포 결과 조회", description = "과제 배포 결과를 상태별로 조회합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "400", description = "잘못된 status 파라미터", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
            ApiResponse(responseCode = "404", description = "코스 또는 과제를 찾을 수 없음", content = [Content(schema = Schema(implementation = ApiEnvelope::class))]),
        ],
    )
    @GetMapping("/{courseSlug}/assignments/{assignmentId}/deliveries")
    fun getDeliveries(
        @Parameter(description = "코스 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "과제 ID", example = "assignment-1")
        @PathVariable assignmentId: String,
        @Parameter(description = "배포 상태 필터", example = "DELIVERED")
        @RequestParam(required = false) status: AssignmentDeliveryStatus?,
    ): Mono<ApiEnvelope<List<AssignmentDeliveryResponse>>> =
        courseV1Service.getDeliveries(courseSlug, assignmentId, status).collectList().map { ApiEnvelope.success(it) }
}
