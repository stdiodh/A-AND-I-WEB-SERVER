package com.example.aandi_post_web_server.course.controller

import com.example.aandi_post_web_server.assignment.dtos.AssignmentDeliveryResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentSummaryResponse
import com.example.aandi_post_web_server.assignment.dtos.CreateAssignmentRequest
import com.example.aandi_post_web_server.assignment.dtos.PublishAssignmentResponse
import com.example.aandi_post_web_server.assignment.dtos.TriggerDeliveriesResponse
import com.example.aandi_post_web_server.assignment.dtos.UpdateAssignmentRequest
import com.example.aandi_post_web_server.assignment.enum.AssignmentDeliveryStatus
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import com.example.aandi_post_web_server.common.openapi.ApiEnvelope
import com.example.aandi_post_web_server.common.openapi.AssignmentDeliveryListEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.AssignmentDetailEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.AssignmentSummaryListEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.CourseEnrollmentEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.CourseEnrollmentListEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.CourseEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.CourseListEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.EmptyEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.ErrorEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.PublishAssignmentEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.TriggerDeliveriesEnvelopeDoc
import com.example.aandi_post_web_server.course.dtos.CourseEnrollmentResponse
import com.example.aandi_post_web_server.course.dtos.CourseResponse
import com.example.aandi_post_web_server.course.dtos.CreateCourseRequest
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
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = CourseListEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
        ],
    )
    @GetMapping
    fun getAdminCourses(): Mono<ApiEnvelope<List<CourseResponse>>> =
        courseV1Service.getAdminCourses().collectList().map { ApiEnvelope.success(it) }

    @Operation(
        summary = "코스 생성",
        description = "관리자가 코스를 생성합니다. 필수 코어 필드는 slug/fieldTag/startDate/endDate이며, 상세 정보는 metadata로 관리합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "생성 성공", content = [Content(schema = Schema(implementation = CourseEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "409", description = "slug 중복", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
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
            ApiResponse(responseCode = "200", description = "수정 성공", content = [Content(schema = Schema(implementation = CourseEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스를 찾을 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
        ],
    )
    @PatchMapping("/{courseSlug}")
    fun updateCourse(
        @Parameter(description = "코스 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @RequestBody request: UpdateCourseRequest,
    ): Mono<ApiEnvelope<CourseResponse>> =
        courseV1Service.updateCourse(courseSlug, request).map { ApiEnvelope.success(it) }

    @Operation(summary = "코스 삭제(하드 삭제)", description = "코스와 연관 데이터(주차/수강/과제/배포/요구사항/예시)를 모두 삭제합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "삭제 성공", content = [Content(schema = Schema(implementation = EmptyEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스를 찾을 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
        ],
    )
    @DeleteMapping("/{courseSlug}")
    fun deleteCourse(
        @Parameter(description = "코스 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
    ): Mono<ApiEnvelope<Nothing?>> =
        courseV1Service.deleteCourse(courseSlug).thenReturn(ApiEnvelope.success(null))

    @Operation(summary = "수강생 등록/복구", description = "코스에 수강생을 ENROLLED 상태로 등록합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "등록 성공", content = [Content(schema = Schema(implementation = CourseEnrollmentEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스를 찾을 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
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
            ApiResponse(responseCode = "200", description = "변경 성공", content = [Content(schema = Schema(implementation = CourseEnrollmentEnvelopeDoc::class))]),
            ApiResponse(responseCode = "400", description = "요청값 오류(예: BANNED + banReason 누락)", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스 또는 수강생을 찾을 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
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
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = CourseEnrollmentListEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스를 찾을 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
        ],
    )
    @GetMapping("/{courseSlug}/enrollments")
    fun getEnrollments(
        @Parameter(description = "코스 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
    ): Mono<ApiEnvelope<List<CourseEnrollmentResponse>>> =
        courseV1Service.getEnrollments(courseSlug).collectList().map { ApiEnvelope.success(it) }

    @Operation(summary = "관리자 과제 목록 조회", description = "관리자 화면용 코스 과제 목록을 조회합니다. weekNo/status 필터를 지원합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = AssignmentSummaryListEnvelopeDoc::class))]),
            ApiResponse(responseCode = "400", description = "잘못된 weekNo/status", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스를 찾을 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
        ],
    )
    @GetMapping("/{courseSlug}/assignments")
    fun getAdminAssignments(
        @Parameter(description = "코스 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "주차 번호(옵션)", example = "1")
        @RequestParam(required = false) weekNo: Int?,
        @Parameter(description = "과제 상태(옵션)", example = "DRAFT")
        @RequestParam(required = false) status: AssignmentStatus?,
    ): Mono<ApiEnvelope<List<AssignmentSummaryResponse>>> =
        courseV1Service.getAdminAssignments(courseSlug, weekNo, status).collectList().map { ApiEnvelope.success(it) }

    @Operation(summary = "관리자 과제 상세 조회", description = "관리자 화면용 과제 상세를 조회합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = AssignmentDetailEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스 또는 과제를 찾을 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
        ],
    )
    @GetMapping("/{courseSlug}/assignments/{assignmentId}")
    fun getAdminAssignmentDetail(
        @Parameter(description = "코스 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "과제 ID", example = "assignment-1")
        @PathVariable assignmentId: String,
    ): Mono<ApiEnvelope<AssignmentDetailResponse>> =
        courseV1Service.getAdminAssignmentDetail(courseSlug, assignmentId).map { ApiEnvelope.success(it) }

    @Operation(summary = "과제 생성", description = "코스 내부에 과제를 생성합니다. 생성 시 courseId/courseSlug가 함께 저장됩니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "생성 성공", content = [Content(schema = Schema(implementation = AssignmentDetailEnvelopeDoc::class))]),
            ApiResponse(responseCode = "400", description = "요청값 오류", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스 또는 주차를 찾을 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "409", description = "동일 코스/주차/순번 중복", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
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

    @Operation(summary = "과제 수정", description = "코스 내부 과제를 수정합니다. requirements/examples가 전달되면 전체 교체됩니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "수정 성공", content = [Content(schema = Schema(implementation = AssignmentDetailEnvelopeDoc::class))]),
            ApiResponse(responseCode = "400", description = "요청값 오류", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스 또는 과제를 찾을 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "409", description = "동일 코스/주차/순번 중복", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
        ],
    )
    @PatchMapping("/{courseSlug}/assignments/{assignmentId}")
    fun updateAssignment(
        @Parameter(description = "코스 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "과제 ID", example = "assignment-1")
        @PathVariable assignmentId: String,
        @Valid @RequestBody request: UpdateAssignmentRequest,
    ): Mono<ApiEnvelope<AssignmentDetailResponse>> =
        courseV1Service.updateAssignment(courseSlug, assignmentId, request).map { ApiEnvelope.success(it) }

    @Operation(summary = "과제 삭제(하드 삭제)", description = "과제와 연관 데이터(요구사항/예시/배포)를 모두 삭제합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "삭제 성공", content = [Content(schema = Schema(implementation = EmptyEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스 또는 과제를 찾을 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
        ],
    )
    @DeleteMapping("/{courseSlug}/assignments/{assignmentId}")
    fun deleteAssignment(
        @Parameter(description = "코스 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "과제 ID", example = "assignment-1")
        @PathVariable assignmentId: String,
    ): Mono<ApiEnvelope<Nothing?>> =
        courseV1Service.deleteAssignment(courseSlug, assignmentId).thenReturn(ApiEnvelope.success(null))

    @Operation(summary = "과제 게시", description = "DRAFT 과제를 PUBLISHED 상태로 게시합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "게시 성공", content = [Content(schema = Schema(implementation = PublishAssignmentEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스 또는 과제를 찾을 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
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
            ApiResponse(responseCode = "200", description = "배포 처리 성공", content = [Content(schema = Schema(implementation = TriggerDeliveriesEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스 또는 과제를 찾을 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "422", description = "PUBLISHED 상태가 아닌 과제", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
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
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = AssignmentDeliveryListEnvelopeDoc::class))]),
            ApiResponse(responseCode = "400", description = "잘못된 status 파라미터", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스 또는 과제를 찾을 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
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
