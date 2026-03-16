package com.example.aandi_post_web_server.submission.service

import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import com.example.aandi_post_web_server.assignment.repository.AssignmentRepository
import com.example.aandi_post_web_server.course.domain.AssignmentId
import com.example.aandi_post_web_server.course.domain.CourseId
import com.example.aandi_post_web_server.course.domain.CourseSlug
import com.example.aandi_post_web_server.course.domain.UserId
import com.example.aandi_post_web_server.course.enum.EnrollmentStatus
import com.example.aandi_post_web_server.course.repository.CourseEnrollmentRepository
import com.example.aandi_post_web_server.course.repository.CourseRepository
import com.example.aandi_post_web_server.submission.client.JudgeSubmissionCreateRequest
import com.example.aandi_post_web_server.submission.client.JudgeSubmissionOptions
import com.example.aandi_post_web_server.submission.client.OnlineJudgeClient
import com.example.aandi_post_web_server.submission.dtos.AssignmentSubmissionAcceptedResponse
import com.example.aandi_post_web_server.submission.dtos.CreateAssignmentSubmissionRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import java.time.Clock
import java.time.Instant

@Service
class AssignmentSubmissionService(
    private val courseRepository: CourseRepository,
    private val courseEnrollmentRepository: CourseEnrollmentRepository,
    private val assignmentRepository: AssignmentRepository,
    private val onlineJudgeClient: OnlineJudgeClient,
    private val clock: Clock = Clock.systemUTC(),
) {

    fun createSubmission(
        courseSlug: String,
        assignmentId: String,
        userId: String,
        publicCode: String,
        authorizationHeader: String?,
        request: CreateAssignmentSubmissionRequest,
    ): Mono<AssignmentSubmissionAcceptedResponse> {
        val authHeader = requireAuthorizationHeader(authorizationHeader)
        val parsedUserId = parseUserId(userId)
        val parsedSlug = parseCourseSlug(courseSlug)
        val parsedAssignmentId = parseAssignmentId(assignmentId)

        return findAccessibleAssignment(parsedSlug, parsedAssignmentId, parsedUserId)
            .flatMap { assignment ->
                onlineJudgeClient.createSubmission(
                    authorizationHeader = authHeader,
                    request = JudgeSubmissionCreateRequest(
                        publicCode = publicCode.trim(),
                        problemId = requireNotNull(assignment.id),
                        language = request.language,
                        code = request.code.trim(),
                        options = JudgeSubmissionOptions(realtimeFeedback = request.realtimeFeedback),
                    ),
                )
            }
            .map { accepted ->
                AssignmentSubmissionAcceptedResponse(
                    submissionId = accepted.submissionId,
                    streamUrl = accepted.streamUrl,
                )
            }
    }

    private fun findAccessibleAssignment(
        courseSlug: CourseSlug,
        assignmentId: AssignmentId,
        userId: UserId,
    ): Mono<Assignment> {
        return courseRepository.findBySlug(courseSlug.value)
            .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "조회 가능한 코스를 찾을 수 없습니다.")))
            .flatMap { course ->
                val courseId = parseCourseId(requireNotNull(course.id))
                ensureEnrolled(courseId, userId)
                    .then(
                        assignmentRepository.findByIdAndCourseId(assignmentId.value, courseId.value)
                            .switchIfEmpty(
                                Mono.error(
                                    ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "과제를 찾을 수 없습니다: ${assignmentId.value}",
                                    )
                                )
                            )
                    )
            }
            .flatMap { assignment -> ensureVisibleAssignment(assignment, assignmentId) }
    }

    private fun ensureEnrolled(courseId: CourseId, userId: UserId): Mono<Void> {
        return courseEnrollmentRepository.findByCourseIdAndUserId(courseId.value, userId.value)
            .filter { it.status == EnrollmentStatus.ENABLED }
            .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "조회 가능한 코스를 찾을 수 없습니다.")))
            .then()
    }

    private fun ensureVisibleAssignment(
        assignment: Assignment,
        assignmentId: AssignmentId,
    ): Mono<Assignment> {
        val now = Instant.now(clock)
        if (assignment.status != AssignmentStatus.PUBLISHED || now.isBefore(assignment.startAt)) {
            return Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "과제를 찾을 수 없습니다: ${assignmentId.value}"))
        }
        return Mono.just(assignment)
    }

    private fun requireAuthorizationHeader(header: String?): String {
        if (header.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization 헤더가 필요합니다.")
        }
        return header
    }

    private fun parseCourseSlug(raw: String): CourseSlug =
        parseOrBadRequest { CourseSlug.from(raw) }

    private fun parseCourseId(raw: String): CourseId =
        parseOrBadRequest { CourseId.from(raw) }

    private fun parseUserId(raw: String): UserId =
        parseOrBadRequest { UserId.from(raw) }

    private fun parseAssignmentId(raw: String): AssignmentId =
        parseOrBadRequest { AssignmentId.from(raw) }

    private fun <T> parseOrBadRequest(block: () -> T): T =
        runCatching(block).getOrElse { error ->
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, error.message ?: "잘못된 요청입니다.")
        }
}
