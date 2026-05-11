package com.example.aandi_post_web_server.course.api.v2.controller

import com.example.aandi_post_web_server.assignment.api.dto.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentSummaryResponse
import com.example.aandi_post_web_server.assignment.api.dto.CopyAssignmentRequest
import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentRequest
import com.example.aandi_post_web_server.assignment.api.dto.UpdateAssignmentRequest
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import com.example.aandi_post_web_server.common.openapi.V2AssignmentDetailEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.V2AssignmentSummaryListEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.V2CourseEnrollmentEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.V2CourseEnrollmentListEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.V2CourseEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.V2CourseListEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.V2EmptyEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.V2ErrorEnvelopeDoc
import com.example.aandi_post_web_server.common.api.envelope.V2ApiEnvelope
import com.example.aandi_post_web_server.common.api.factory.V2ApiResponseFactory
import com.example.aandi_post_web_server.course.api.dto.CourseEnrollmentResponse
import com.example.aandi_post_web_server.course.api.dto.CourseResponse
import com.example.aandi_post_web_server.course.api.dto.CreateCourseRequest
import com.example.aandi_post_web_server.course.api.dto.EnrollCourseRequest
import com.example.aandi_post_web_server.course.api.dto.UpdateCourseRequest
import com.example.aandi_post_web_server.course.api.dto.UpdateEnrollmentRequest
import com.example.aandi_post_web_server.course.application.service.CourseV1Service
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

@Tag(
    name = "코스 관리자 v2 API",
    description = "v1 관리자 코스/수강/과제 관리 기능을 v2 경로로 확장한 API입니다. A&I v2 공통 헤더(`deviceOS`, `Authenticate`, `timestamp`, `salt`)와 공통 응답 계약을 사용하며 ADMIN 권한이 필요합니다.",
)
@SecurityRequirement(name = "v2Authenticate")
@RestController
@RequestMapping("/v2/admin/courses")
class CourseAdminV2Controller(
    private val courseV1Service: CourseV1Service,
) {

    @Operation(
        summary = "전체 코스 목록 조회",
        description = "v1 `GET /v1/admin/courses` 대응 API입니다. 관리자 화면에서 전체 코스 목록을 조회할 때 사용합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = V2CourseListEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
        ],
    )
    @GetMapping
    fun getAdminCourses(): Mono<V2ApiEnvelope<List<CourseResponse>>> =
        courseV1Service.getAdminCourses().collectList().map(V2ApiResponseFactory::success)

    @Operation(
        summary = "코스 생성",
        description = "v1 `POST /v1/admin/courses` 대응 API입니다. slug, 기간, metadata를 포함한 새 코스를 생성합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "생성 성공", content = [Content(schema = Schema(implementation = V2CourseEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "409", description = "slug 중복", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
        ],
    )
    @PostMapping
    fun createCourse(
        @Valid @RequestBody request: CreateCourseRequest,
    ): Mono<V2ApiEnvelope<CourseResponse>> =
        courseV1Service.createCourse(request).map(V2ApiResponseFactory::success)

    @Operation(
        summary = "코스 수정",
        description = "v1 `PATCH /v1/admin/courses/{courseSlug}` 대응 API입니다. 코스 기본 정보와 상태를 수정합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "수정 성공", content = [Content(schema = Schema(implementation = V2CourseEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스를 찾을 수 없음", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
        ],
    )
    @PatchMapping("/{courseSlug}")
    fun updateCourse(
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @RequestBody request: UpdateCourseRequest,
    ): Mono<V2ApiEnvelope<CourseResponse>> =
        courseV1Service.updateCourse(courseSlug, request).map(V2ApiResponseFactory::success)

    @Operation(
        summary = "코스 삭제",
        description = "v1 `DELETE /v1/admin/courses/{courseSlug}` 대응 API입니다. 코스와 연결된 주차, 수강, 과제 데이터를 함께 삭제합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "삭제 성공", content = [Content(schema = Schema(implementation = V2EmptyEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스를 찾을 수 없음", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
        ],
    )
    @DeleteMapping("/{courseSlug}")
    fun deleteCourse(
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
    ): Mono<V2ApiEnvelope<Nothing?>> =
        courseV1Service.deleteCourse(courseSlug).thenReturn(V2ApiResponseFactory.success(null))

    @Operation(
        summary = "수강생 등록",
        description = "v1 `POST /v1/admin/courses/{courseSlug}/enrollments` 대응 API입니다. report 서버에 동기화된 publicCode 사용자를 코스에 등록합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "등록 성공", content = [Content(schema = Schema(implementation = V2CourseEnrollmentEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스를 찾을 수 없음", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "422", description = "report 서버에 동기화된 사용자를 찾을 수 없음", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
        ],
    )
    @PostMapping("/{courseSlug}/enrollments")
    fun enrollMember(
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Valid @RequestBody request: EnrollCourseRequest,
    ): Mono<V2ApiEnvelope<CourseEnrollmentResponse>> =
        courseV1Service.enrollMember(courseSlug, request).map(V2ApiResponseFactory::success)

    @Operation(
        summary = "수강 상태 변경",
        description = "v1 `PATCH /v1/admin/courses/{courseSlug}/enrollments/{userId}` 대응 API입니다. ENABLED 또는 BANNED 상태로 변경합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "변경 성공", content = [Content(schema = Schema(implementation = V2CourseEnrollmentEnvelopeDoc::class))]),
            ApiResponse(responseCode = "400", description = "요청값 오류(예: BANNED + banReason 누락)", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스 또는 수강생을 찾을 수 없음", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
        ],
    )
    @PatchMapping("/{courseSlug}/enrollments/{userId}")
    fun updateEnrollmentStatus(
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "사용자 UUID", example = "user-1")
        @PathVariable userId: String,
        @RequestBody request: UpdateEnrollmentRequest,
    ): Mono<V2ApiEnvelope<CourseEnrollmentResponse>> =
        courseV1Service.updateEnrollmentStatus(courseSlug, userId, request).map(V2ApiResponseFactory::success)

    @Operation(
        summary = "수강생 삭제",
        description = "v1 `DELETE /v1/admin/courses/{courseSlug}/enrollments/{userId}` 대응 API입니다. 코스 수강생을 목록에서 제거합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "삭제 성공", content = [Content(schema = Schema(implementation = V2EmptyEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스 또는 수강생을 찾을 수 없음", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
        ],
    )
    @DeleteMapping("/{courseSlug}/enrollments/{userId}")
    fun deleteEnrollment(
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "사용자 UUID", example = "user-1")
        @PathVariable userId: String,
    ): Mono<V2ApiEnvelope<Nothing?>> =
        courseV1Service.deleteEnrollment(courseSlug, userId).thenReturn(V2ApiResponseFactory.success(null))

    @Operation(
        summary = "수강생 목록 조회",
        description = "v1 `GET /v1/admin/courses/{courseSlug}/enrollments` 대응 API입니다. 코스에 등록된 수강생 목록을 조회합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = V2CourseEnrollmentListEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스를 찾을 수 없음", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
        ],
    )
    @GetMapping("/{courseSlug}/enrollments")
    fun getEnrollments(
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
    ): Mono<V2ApiEnvelope<List<CourseEnrollmentResponse>>> =
        courseV1Service.getEnrollments(courseSlug).collectList().map(V2ApiResponseFactory::success)

    @Operation(
        summary = "과제 목록 조회",
        description = "v1 `GET /v1/admin/courses/{courseSlug}/assignments` 대응 API입니다. weekNo와 status를 선택적으로 사용해 관리 대상 과제를 조회합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = V2AssignmentSummaryListEnvelopeDoc::class))]),
            ApiResponse(responseCode = "400", description = "잘못된 weekNo/status", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스를 찾을 수 없음", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
        ],
    )
    @GetMapping("/{courseSlug}/assignments")
    fun getAdminAssignments(
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "주차 번호(옵션)", example = "1")
        @RequestParam(required = false) weekNo: Int?,
        @Parameter(description = "과제 상태(옵션)", example = "DRAFT")
        @RequestParam(required = false) status: AssignmentStatus?,
    ): Mono<V2ApiEnvelope<List<AssignmentSummaryResponse>>> =
        courseV1Service.getAdminAssignments(courseSlug, weekNo, status)
            .collectList()
            .map(V2ApiResponseFactory::success)

    @Operation(
        summary = "과제 상세 조회",
        description = "v1 `GET /v1/admin/courses/{courseSlug}/assignments/{assignmentId}` 대응 API입니다. assignmentId는 과제 UUID를 사용합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = V2AssignmentDetailEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스 또는 과제를 찾을 수 없음", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
        ],
    )
    @GetMapping("/{courseSlug}/assignments/{assignmentId}")
    fun getAdminAssignmentDetail(
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "과제 UUID", example = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111")
        @PathVariable assignmentId: String,
    ): Mono<V2ApiEnvelope<AssignmentDetailResponse>> =
        courseV1Service.getAdminAssignmentDetail(courseSlug, assignmentId).map(V2ApiResponseFactory::success)

    @Operation(
        summary = "과제 생성",
        description = "v1 `POST /v1/admin/courses/{courseSlug}/assignments` 대응 API입니다. 과제 본문 저장과 OJ problem sync 이벤트 발행까지 동일하게 수행합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "생성 성공", content = [Content(schema = Schema(implementation = V2AssignmentDetailEnvelopeDoc::class))]),
            ApiResponse(responseCode = "400", description = "요청값 오류", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스 또는 주차를 찾을 수 없음", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "409", description = "동일 코스/주차/순번 중복", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
        ],
    )
    @PostMapping("/{courseSlug}/assignments")
    fun createAssignment(
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Valid @RequestBody request: CreateAssignmentRequest,
        authentication: Authentication,
    ): Mono<V2ApiEnvelope<AssignmentDetailResponse>> =
        courseV1Service.createAssignment(courseSlug, request, authentication.name).map(V2ApiResponseFactory::success)

    @Operation(
        summary = "과제 복사",
        description = "원본 과제를 대상 코스에 복사합니다. 동일 원본 과제가 이미 대상 코스에 있으면 409를 반환합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "복사 성공", content = [Content(schema = Schema(implementation = V2AssignmentDetailEnvelopeDoc::class))]),
            ApiResponse(responseCode = "400", description = "요청값 오류", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "대상 코스 또는 원본 과제를 찾을 수 없음", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "409", description = "동일 원본/동일 내용/동일 슬롯 중복", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
        ],
    )
    @PostMapping("/{targetCourseSlug}/assignments/copy")
    fun copyAssignment(
        @Parameter(description = "복사 대상 코스를 구분하는 슬러그", example = "target-course")
        @PathVariable targetCourseSlug: String,
        @Valid @RequestBody request: CopyAssignmentRequest,
        authentication: Authentication,
    ): Mono<V2ApiEnvelope<AssignmentDetailResponse>> =
        courseV1Service.copyAssignment(targetCourseSlug, request, authentication.name).map(V2ApiResponseFactory::success)

    @Operation(
        summary = "과제 수정",
        description = "v1 `PATCH /v1/admin/courses/{courseSlug}/assignments/{assignmentId}` 대응 API입니다. metadata 전체 교체 규칙과 OJ problem sync 이벤트 발행 규칙을 동일하게 유지합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "수정 성공", content = [Content(schema = Schema(implementation = V2AssignmentDetailEnvelopeDoc::class))]),
            ApiResponse(responseCode = "400", description = "요청값 오류", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스 또는 과제를 찾을 수 없음", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "409", description = "동일 코스/주차/순번 중복", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
        ],
    )
    @PatchMapping("/{courseSlug}/assignments/{assignmentId}")
    fun updateAssignment(
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "과제 UUID", example = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111")
        @PathVariable assignmentId: String,
        @Valid @RequestBody request: UpdateAssignmentRequest,
    ): Mono<V2ApiEnvelope<AssignmentDetailResponse>> =
        courseV1Service.updateAssignment(courseSlug, assignmentId, request).map(V2ApiResponseFactory::success)

    @Operation(
        summary = "과제 삭제",
        description = "v1 `DELETE /v1/admin/courses/{courseSlug}/assignments/{assignmentId}` 대응 API입니다. 삭제와 함께 관련 problem sync 삭제 이벤트를 동일하게 수행합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "삭제 성공", content = [Content(schema = Schema(implementation = V2EmptyEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스 또는 과제를 찾을 수 없음", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
        ],
    )
    @DeleteMapping("/{courseSlug}/assignments/{assignmentId}")
    fun deleteAssignment(
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "과제 UUID", example = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111")
        @PathVariable assignmentId: String,
    ): Mono<V2ApiEnvelope<Nothing?>> =
        courseV1Service.deleteAssignment(courseSlug, assignmentId).thenReturn(V2ApiResponseFactory.success(null))
}
