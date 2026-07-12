package com.example.aandi_post_web_server.course.application.service

import com.example.aandi_post_web_server.common.security.UserRole
import com.example.aandi_post_web_server.course.api.dto.CourseEnrollmentResponse
import com.example.aandi_post_web_server.course.api.dto.EnrollCourseRequest
import com.example.aandi_post_web_server.course.api.dto.UpdateEnrollmentRequest
import com.example.aandi_post_web_server.course.application.mapper.toResponse
import com.example.aandi_post_web_server.course.application.port.CourseEnrollmentStore
import com.example.aandi_post_web_server.course.application.port.CourseEnrollmentUserQueryPort
import com.example.aandi_post_web_server.course.application.port.CourseEnrollmentUserReference
import com.example.aandi_post_web_server.course.domain.model.CourseId
import com.example.aandi_post_web_server.course.domain.model.CourseSlug
import com.example.aandi_post_web_server.course.domain.model.CourseTrack
import com.example.aandi_post_web_server.course.domain.model.EnrollmentStatus
import com.example.aandi_post_web_server.course.domain.model.PublicCode
import com.example.aandi_post_web_server.course.domain.model.UserId
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseEnrollment
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import java.time.Instant

@Service
class CourseEnrollmentCommandService(
    private val courseRepository: CourseRepository,
    private val courseEnrollmentStore: CourseEnrollmentStore,
    private val userQueryPort: CourseEnrollmentUserQueryPort,
) {
    fun enrollMember(courseSlug: String, request: EnrollCourseRequest): Mono<CourseEnrollmentResponse> {
        val slug = parseCourseSlug(courseSlug)
        val publicCode = parsePublicCode(request.publicCode)
        return findCourseBySlug(slug)
            .flatMap { course ->
                val courseId = parseCourseId(requireNotNull(course.id))
                findEnrollmentUserByPublicCode(publicCode)
                    .switchIfEmpty(
                        Mono.error(
                            ResponseStatusException(
                                HttpStatus.UNPROCESSABLE_ENTITY,
                                "report 서버에서 publicCode=${publicCode.value} 사용자를 찾을 수 없습니다. auth 이벤트 동기화 여부를 확인해주세요.",
                            )
                        )
                    )
                    .flatMap { reportUser ->
                        validateEnrollmentEligibility(course, publicCode, reportUser)
                            .then(Mono.defer { enrollUser(courseId, course.slug, reportUser) })
                    }
            }
    }

    fun updateEnrollmentStatus(
        courseSlug: String,
        userId: String,
        request: UpdateEnrollmentRequest,
    ): Mono<CourseEnrollmentResponse> {
        val slug = parseCourseSlug(courseSlug)
        val parsedUserId = parseUserId(userId)
        return findCourseBySlug(slug)
            .flatMap { course ->
                val courseId = parseCourseId(requireNotNull(course.id))
                courseEnrollmentStore.findByCourseIdAndUserId(courseId.value, parsedUserId.value)
                    .switchIfEmpty(
                        Mono.error(
                            ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "코스에 등록된 사용자를 찾을 수 없습니다: ${parsedUserId.value}",
                            )
                        )
                    )
                    .flatMap { enrollment ->
                        val now = Instant.now()
                        val updated = when (request.status) {
                            EnrollmentStatus.ENABLED -> enrollment.copy(
                                status = EnrollmentStatus.ENABLED,
                                bannedAt = null,
                                banReason = null,
                                updatedAt = now,
                            )

                            EnrollmentStatus.BANNED -> {
                                if (request.banReason.isNullOrBlank()) {
                                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "BANNED 상태는 banReason이 필요합니다.")
                                }
                                enrollment.copy(
                                    status = EnrollmentStatus.BANNED,
                                    bannedAt = now,
                                    banReason = request.banReason.trim(),
                                    updatedAt = now,
                                )
                            }
                        }
                        courseEnrollmentStore.save(updated)
                    }
                    .map { enrollment -> enrollment.toResponse(course.slug) }
            }
    }

    fun deleteEnrollment(courseSlug: String, userId: String): Mono<Void> {
        val slug = parseCourseSlug(courseSlug)
        val parsedUserId = parseUserId(userId)
        return findCourseBySlug(slug)
            .flatMap { course ->
                val courseId = parseCourseId(requireNotNull(course.id))
                courseEnrollmentStore.findByCourseIdAndUserId(courseId.value, parsedUserId.value)
                    .switchIfEmpty(
                        Mono.error(
                            ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "코스에 등록된 사용자를 찾을 수 없습니다: ${parsedUserId.value}",
                            )
                        )
                    )
                    .flatMap { enrollment -> courseEnrollmentStore.delete(enrollment) }
            }
            .then()
    }

    private fun findCourseBySlug(slug: CourseSlug): Mono<Course> {
        return courseRepository.findBySlug(slug.value)
            .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "코스를 찾을 수 없습니다: ${slug.value}")))
    }

    private fun findEnrollmentUserByPublicCode(publicCode: PublicCode): Mono<CourseEnrollmentUserReference> =
        userQueryPort.findByPublicCode(publicCode.value)
            .switchIfEmpty(Mono.defer { userQueryPort.findByPublicCode(publicCode.legacyValue) })

    private fun validateEnrollmentEligibility(
        course: Course,
        publicCode: PublicCode,
        reportUser: CourseEnrollmentUserReference,
    ): Mono<Void> {
        if (isPrivilegedUser(reportUser)) {
            return Mono.empty()
        }
        if (course.fieldTag == CourseTrack.NO) {
            return Mono.empty()
        }
        if (publicCode.track == course.fieldTag) {
            return Mono.empty()
        }
        return Mono.error(
            ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "${course.fieldTag.name} 코스에는 ${course.fieldTag.name} 트랙 사용자만 등록할 수 있습니다.",
            )
        )
    }

    private fun isPrivilegedUser(reportUser: CourseEnrollmentUserReference): Boolean {
        val role = UserRole.fromClaim(reportUser.role)
        return role == UserRole.ADMIN || role == UserRole.ORGANIZER
    }

    private fun enrollUser(
        courseId: CourseId,
        courseSlug: String,
        reportUser: CourseEnrollmentUserReference,
    ): Mono<CourseEnrollmentResponse> {
        return courseEnrollmentStore.findByCourseIdAndUserId(courseId.value, reportUser.id)
            .flatMap<CourseEnrollmentResponse> { existing ->
                val conflict = if (existing.status == EnrollmentStatus.BANNED) {
                    ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "차단된 사용자는 재등록할 수 없습니다: ${reportUser.publicCode}",
                    )
                } else {
                    duplicateEnrollmentConflict(courseId.value, reportUser.id)
                }
                Mono.error(conflict)
            }
            .switchIfEmpty(
                Mono.defer {
                    val now = Instant.now()
                    courseEnrollmentStore.save(
                        CourseEnrollment(
                            courseId = courseId.value,
                            userId = reportUser.id,
                            publicCode = reportUser.publicCode,
                            username = reportUser.username,
                            status = EnrollmentStatus.ENABLED,
                            joinedAt = now,
                            updatedAt = now,
                        )
                    )
                        .onErrorMap(DuplicateKeyException::class.java) { error ->
                            duplicateEnrollmentConflict(courseId.value, reportUser.id, error)
                        }
                        .map { enrollment -> enrollment.toResponse(courseSlug) }
                }
            )
    }

    private fun parseCourseSlug(raw: String): CourseSlug =
        parseOrBadRequest { CourseSlug.from(raw) }

    private fun parseCourseId(raw: String): CourseId =
        parseOrBadRequest { CourseId.from(raw) }

    private fun parseUserId(raw: String): UserId =
        parseOrBadRequest { UserId.from(raw) }

    private fun parsePublicCode(raw: String): PublicCode =
        parseOrBadRequest { PublicCode.from(raw) }

    private fun duplicateEnrollmentConflict(
        courseId: String,
        userId: String,
        cause: Throwable? = null,
    ): ResponseStatusException =
        ResponseStatusException(
            HttpStatus.CONFLICT,
            "이미 등록된 사용자입니다. courseId=$courseId, userId=$userId",
            cause,
        )

    private fun <T> parseOrBadRequest(block: () -> T): T {
        return runCatching(block).getOrElse { error ->
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, error.message ?: "잘못된 요청입니다.")
        }
    }
}
