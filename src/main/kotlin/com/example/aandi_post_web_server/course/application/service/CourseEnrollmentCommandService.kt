package com.example.aandi_post_web_server.course.application.service

import com.example.aandi_post_web_server.common.security.UserRole
import com.example.aandi_post_web_server.course.api.dto.CourseEnrollmentResponse
import com.example.aandi_post_web_server.course.api.dto.EnrollCourseRequest
import com.example.aandi_post_web_server.course.api.dto.UpdateEnrollmentRequest
import com.example.aandi_post_web_server.course.domain.model.CourseId
import com.example.aandi_post_web_server.course.domain.model.CourseSlug
import com.example.aandi_post_web_server.course.domain.model.CourseTrack
import com.example.aandi_post_web_server.course.domain.model.EnrollmentStatus
import com.example.aandi_post_web_server.course.domain.model.PublicCode
import com.example.aandi_post_web_server.course.domain.model.UserId
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseEnrollment
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseEnrollmentRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseRepository
import com.example.aandi_post_web_server.user.entity.ReportUser
import com.example.aandi_post_web_server.user.infrastructure.repository.ReportUserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import java.time.Instant

@Service
class CourseEnrollmentCommandService(
    private val courseRepository: CourseRepository,
    private val courseEnrollmentRepository: CourseEnrollmentRepository,
    private val reportUserRepository: ReportUserRepository,
) {
    fun enrollMember(courseSlug: String, request: EnrollCourseRequest): Mono<CourseEnrollmentResponse> {
        val slug = parseCourseSlug(courseSlug)
        val publicCode = parsePublicCode(request.publicCode)
        return findCourseBySlug(slug)
            .flatMap { course ->
                val courseId = parseCourseId(requireNotNull(course.id))
                findReportUserByPublicCode(publicCode)
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
                courseEnrollmentRepository.findByCourseIdAndUserId(courseId.value, parsedUserId.value)
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
                        courseEnrollmentRepository.save(updated)
                    }
                    .map { enrollment -> toEnrollmentResponse(course.slug, enrollment) }
            }
    }

    fun deleteEnrollment(courseSlug: String, userId: String): Mono<Void> {
        val slug = parseCourseSlug(courseSlug)
        val parsedUserId = parseUserId(userId)
        return findCourseBySlug(slug)
            .flatMap { course ->
                val courseId = parseCourseId(requireNotNull(course.id))
                courseEnrollmentRepository.findByCourseIdAndUserId(courseId.value, parsedUserId.value)
                    .switchIfEmpty(
                        Mono.error(
                            ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "코스에 등록된 사용자를 찾을 수 없습니다: ${parsedUserId.value}",
                            )
                        )
                    )
                    .flatMap { enrollment -> courseEnrollmentRepository.delete(enrollment) }
            }
            .then()
    }

    private fun findCourseBySlug(slug: CourseSlug): Mono<Course> {
        return courseRepository.findBySlug(slug.value)
            .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "코스를 찾을 수 없습니다: ${slug.value}")))
    }

    private fun findReportUserByPublicCode(publicCode: PublicCode): Mono<ReportUser> =
        reportUserRepository.findByPublicCode(publicCode.value)
            .switchIfEmpty(Mono.defer { reportUserRepository.findByPublicCode(publicCode.legacyValue) })

    private fun validateEnrollmentEligibility(
        course: Course,
        publicCode: PublicCode,
        reportUser: ReportUser,
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

    private fun isPrivilegedUser(reportUser: ReportUser): Boolean {
        val role = UserRole.fromClaim(reportUser.role)
        return role == UserRole.ADMIN || role == UserRole.ORGANIZER
    }

    private fun enrollUser(
        courseId: CourseId,
        courseSlug: String,
        reportUser: ReportUser,
    ): Mono<CourseEnrollmentResponse> {
        return courseEnrollmentRepository.findByCourseIdAndUserId(courseId.value, reportUser.id)
            .flatMap<CourseEnrollmentResponse> { existing ->
                val message = if (existing.status == EnrollmentStatus.BANNED) {
                    "차단된 사용자는 재등록할 수 없습니다: ${reportUser.publicCode}"
                } else {
                    "이미 등록된 사용자입니다. courseId=${courseId.value}, userId=${reportUser.id}"
                }
                Mono.error(
                    ResponseStatusException(
                        HttpStatus.CONFLICT,
                        message,
                    )
                )
            }
            .switchIfEmpty(
                Mono.defer {
                    val now = Instant.now()
                    courseEnrollmentRepository.save(
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
                        .map { enrollment -> toEnrollmentResponse(courseSlug, enrollment) }
                }
            )
    }

    private fun toEnrollmentResponse(courseSlug: String, enrollment: CourseEnrollment): CourseEnrollmentResponse = CourseEnrollmentResponse(
        courseId = enrollment.courseId,
        courseSlug = courseSlug,
        userId = enrollment.userId,
        publicCode = enrollment.publicCode,
        username = enrollment.username,
        status = enrollment.status,
        joinedAt = enrollment.joinedAt,
        bannedAt = enrollment.bannedAt,
        banReason = enrollment.banReason,
        updatedAt = enrollment.updatedAt,
    )

    private fun parseCourseSlug(raw: String): CourseSlug =
        parseOrBadRequest { CourseSlug.from(raw) }

    private fun parseCourseId(raw: String): CourseId =
        parseOrBadRequest { CourseId.from(raw) }

    private fun parseUserId(raw: String): UserId =
        parseOrBadRequest { UserId.from(raw) }

    private fun parsePublicCode(raw: String): PublicCode =
        parseOrBadRequest { PublicCode.from(raw) }

    private fun <T> parseOrBadRequest(block: () -> T): T {
        return runCatching(block).getOrElse { error ->
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, error.message ?: "잘못된 요청입니다.")
        }
    }
}
