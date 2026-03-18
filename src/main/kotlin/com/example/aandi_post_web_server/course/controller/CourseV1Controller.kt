package com.example.aandi_post_web_server.course.controller

import com.example.aandi_post_web_server.assignment.dtos.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentSummaryResponse
import com.example.aandi_post_web_server.assignment.dtos.CreateAssignmentRequest
import com.example.aandi_post_web_server.assignment.dtos.UpdateAssignmentRequest
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import com.example.aandi_post_web_server.common.openapi.ApiEnvelope
import com.example.aandi_post_web_server.common.openapi.AssignmentDetailEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.AssignmentSummaryListEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.CourseEnrollmentEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.CourseEnrollmentListEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.CourseEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.CourseListEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.EmptyEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.ErrorEnvelopeDoc
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

    @Operation(summary = "전체 코스 목록 조회", description = "관리자가 전체 코스 목록을 보여줄 때 사용하는 API입니다.")
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
        description = "새 코스를 만듭니다. 코스를 구분하는 slug와 기간 정보, 화면에 보여줄 metadata를 함께 저장합니다.",
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
        description = "코스의 기본 정보와 상태를 수정합니다.",
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
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @RequestBody request: UpdateCourseRequest,
    ): Mono<ApiEnvelope<CourseResponse>> =
        courseV1Service.updateCourse(courseSlug, request).map { ApiEnvelope.success(it) }

    @Operation(summary = "코스 삭제", description = "코스와 연결된 주차, 수강, 과제 데이터를 함께 삭제합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "삭제 성공", content = [Content(schema = Schema(implementation = EmptyEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스를 찾을 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
        ],
    )
    @DeleteMapping("/{courseSlug}")
    fun deleteCourse(
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
    ): Mono<ApiEnvelope<Nothing?>> =
        courseV1Service.deleteCourse(courseSlug).thenReturn(ApiEnvelope.success(null))

    @Operation(summary = "수강생 등록", description = "publicCode로 report 서버에 동기화된 사용자를 찾아 코스 수강생으로 등록합니다. publicCode는 #이 없으면 자동으로 붙여 정규화하며, 등록 상태는 ENABLED로 시작합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "등록 성공", content = [Content(schema = Schema(implementation = CourseEnrollmentEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스를 찾을 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "422", description = "report 서버에 동기화된 사용자를 찾을 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
        ],
    )
    @PostMapping("/{courseSlug}/enrollments")
    fun enrollMember(
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Valid @RequestBody request: EnrollCourseRequest,
    ): Mono<ApiEnvelope<CourseEnrollmentResponse>> =
        courseV1Service.enrollMember(courseSlug, request).map { ApiEnvelope.success(it) }

    @Operation(summary = "수강 상태 변경", description = "수강 상태를 ENABLED 또는 BANNED로 변경합니다. BANNED가 되면 사용자 API에서는 404로 숨겨집니다.")
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
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "사용자 UUID", example = "user-1")
        @PathVariable userId: String,
        @RequestBody request: UpdateEnrollmentRequest,
    ): Mono<ApiEnvelope<CourseEnrollmentResponse>> =
        courseV1Service.updateEnrollmentStatus(courseSlug, userId, request).map { ApiEnvelope.success(it) }

    @Operation(summary = "수강생 삭제", description = "코스 수강생을 목록에서 제거합니다. 삭제된 사용자는 사용자 API에서 더 이상 이 코스를 조회할 수 없습니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "삭제 성공", content = [Content(schema = Schema(implementation = EmptyEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스 또는 수강생을 찾을 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
        ],
    )
    @DeleteMapping("/{courseSlug}/enrollments/{userId}")
    fun deleteEnrollment(
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "사용자 UUID", example = "user-1")
        @PathVariable userId: String,
    ): Mono<ApiEnvelope<Nothing?>> =
        courseV1Service.deleteEnrollment(courseSlug, userId).thenReturn(ApiEnvelope.success(null))

    @Operation(summary = "수강생 목록 조회", description = "코스에 등록된 수강생 목록을 보여줍니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = CourseEnrollmentListEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스를 찾을 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
        ],
    )
    @GetMapping("/{courseSlug}/enrollments")
    fun getEnrollments(
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
    ): Mono<ApiEnvelope<List<CourseEnrollmentResponse>>> =
        courseV1Service.getEnrollments(courseSlug).collectList().map { ApiEnvelope.success(it) }

    @Operation(summary = "과제 목록 조회", description = "코스의 과제 목록을 보여줍니다. weekNo와 status는 필요할 때만 선택해서 사용할 수 있습니다.")
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
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "주차 번호(옵션)", example = "1")
        @RequestParam(required = false) weekNo: Int?,
        @Parameter(description = "과제 상태(옵션)", example = "DRAFT")
        @RequestParam(required = false) status: AssignmentStatus?,
    ): Mono<ApiEnvelope<List<AssignmentSummaryResponse>>> =
        courseV1Service.getAdminAssignments(courseSlug, weekNo, status).collectList().map { ApiEnvelope.success(it) }

    @Operation(summary = "과제 상세 조회", description = "과제의 상세 정보를 보여줍니다. assignmentId는 과제 UUID를 사용합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = AssignmentDetailEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스 또는 과제를 찾을 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
        ],
    )
    @GetMapping("/{courseSlug}/assignments/{assignmentId}")
    fun getAdminAssignmentDetail(
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "과제 UUID", example = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111")
        @PathVariable assignmentId: String,
    ): Mono<ApiEnvelope<AssignmentDetailResponse>> =
        courseV1Service.getAdminAssignmentDetail(courseSlug, assignmentId).map { ApiEnvelope.success(it) }

    @Operation(summary = "과제 생성", description = "코스 안에 새 과제를 만듭니다. 과제 ID는 UUID로 자동 생성되며, 공개 테스트케이스 전체 배열 snapshot 이 OJ problem sync 이벤트로 함께 발행됩니다.")
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
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Valid @RequestBody request: CreateAssignmentRequest,
        authentication: Authentication,
    ): Mono<ApiEnvelope<AssignmentDetailResponse>> =
        courseV1Service.createAssignment(courseSlug, request, authentication.name).map { ApiEnvelope.success(it) }

    @Operation(summary = "과제 수정", description = "과제 정보를 수정합니다. requirements와 testCases를 보내면 기존 값이 전체 교체되며, testCases 변경 후 공개 테스트케이스의 최종 전체 배열 snapshot 을 다시 OJ problem sync 이벤트로 발행합니다.")
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
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "과제 UUID", example = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111")
        @PathVariable assignmentId: String,
        @Valid @RequestBody request: UpdateAssignmentRequest,
    ): Mono<ApiEnvelope<AssignmentDetailResponse>> =
        courseV1Service.updateAssignment(courseSlug, assignmentId, request).map { ApiEnvelope.success(it) }

    @Operation(summary = "과제 삭제", description = "과제와 연결된 요구사항, 학습 목표, 테스트케이스 데이터를 함께 삭제합니다. 삭제 시에는 해당 assignment UUID에 대해 PROBLEM_DELETED 와 testCases: [] problem sync 이벤트를 발행합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "삭제 성공", content = [Content(schema = Schema(implementation = EmptyEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 아님", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스 또는 과제를 찾을 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
        ],
    )
    @DeleteMapping("/{courseSlug}/assignments/{assignmentId}")
    fun deleteAssignment(
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "과제 UUID", example = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111")
        @PathVariable assignmentId: String,
    ): Mono<ApiEnvelope<Nothing?>> =
        courseV1Service.deleteAssignment(courseSlug, assignmentId).thenReturn(ApiEnvelope.success(null))
}
