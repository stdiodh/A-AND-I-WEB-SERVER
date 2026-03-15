package com.example.aandi_post_web_server.submission.service

import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.repository.AssignmentExampleRepository
import com.example.aandi_post_web_server.assignment.repository.AssignmentRepository
import com.example.aandi_post_web_server.course.domain.AssignmentId
import com.example.aandi_post_web_server.course.domain.CourseId
import com.example.aandi_post_web_server.course.domain.CourseSlug
import com.example.aandi_post_web_server.course.domain.UserId
import com.example.aandi_post_web_server.course.repository.CourseEnrollmentRepository
import com.example.aandi_post_web_server.course.repository.CourseRepository
import com.example.aandi_post_web_server.course.enum.EnrollmentStatus
import com.example.aandi_post_web_server.submission.client.JudgeSubmissionCreateRequest
import com.example.aandi_post_web_server.submission.client.JudgeSubmissionOptions
import com.example.aandi_post_web_server.submission.client.OnlineJudgeClient
import com.example.aandi_post_web_server.submission.dtos.AssignmentSubmissionAcceptedResponse
import com.example.aandi_post_web_server.submission.dtos.AssignmentSubmissionResultResponse
import com.example.aandi_post_web_server.submission.dtos.AssignmentSubmissionTestCaseResultResponse
import com.example.aandi_post_web_server.submission.dtos.CreateAssignmentSubmissionRequest
import com.example.aandi_post_web_server.submission.entity.AssignmentSubmission
import com.example.aandi_post_web_server.submission.enum.AssignmentSubmissionStatus
import com.example.aandi_post_web_server.submission.repository.AssignmentSubmissionRepository
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Clock
import java.time.Instant

@Service
class AssignmentSubmissionService(
    private val courseRepository: CourseRepository,
    private val courseEnrollmentRepository: CourseEnrollmentRepository,
    private val assignmentRepository: AssignmentRepository,
    private val assignmentExampleRepository: AssignmentExampleRepository,
    private val assignmentSubmissionRepository: AssignmentSubmissionRepository,
    private val onlineJudgeClient: OnlineJudgeClient,
    private val clock: Clock = Clock.systemUTC(),
) {

    fun createSubmission(
        courseSlug: String,
        assignmentId: String,
        userId: String,
        authorizationHeader: String?,
        request: CreateAssignmentSubmissionRequest,
    ): Mono<AssignmentSubmissionAcceptedResponse> {
        val authHeader = requireAuthorizationHeader(authorizationHeader)
        val parsedUserId = parseUserId(userId)
        val parsedSlug = parseCourseSlug(courseSlug)
        val parsedAssignmentId = parseAssignmentId(assignmentId)

        return findAccessibleAssignment(parsedSlug, parsedAssignmentId, parsedUserId)
            .flatMap { assignment ->
                ensureSubmissionWindow(assignment)
                    .then(ensureSubmissionLanguageSupported(assignment, request.language.name))
                    .then(ensureExamplesExist(parsedAssignmentId))
                    .then(
                        onlineJudgeClient.createSubmission(
                            authorizationHeader = authHeader,
                            request = JudgeSubmissionCreateRequest(
                                problemId = parsedAssignmentId.value,
                                language = request.language,
                                code = request.code.trim(),
                                options = JudgeSubmissionOptions(realtimeFeedback = request.realtimeFeedback),
                            ),
                        )
                    )
                    .flatMap { accepted ->
                        val now = Instant.now(clock)
                        assignmentSubmissionRepository.save(
                            AssignmentSubmission(
                                id = accepted.submissionId,
                                assignmentId = parsedAssignmentId.value,
                                courseId = assignment.courseId,
                                courseSlug = assignment.courseSlug.ifBlank { parsedSlug.value },
                                userId = parsedUserId.value,
                                language = request.language,
                                status = AssignmentSubmissionStatus.PENDING,
                                realtimeFeedback = request.realtimeFeedback,
                                createdAt = now,
                                updatedAt = now,
                            )
                        ).map { saved ->
                            AssignmentSubmissionAcceptedResponse(
                                submissionId = requireNotNull(saved.id),
                                assignmentId = saved.assignmentId,
                                status = saved.status,
                                streamUrl = "/v1/courses/${saved.courseSlug}/assignments/${saved.assignmentId}/submissions/${saved.id}/stream",
                                resultUrl = "/v1/courses/${saved.courseSlug}/assignments/${saved.assignmentId}/submissions/${saved.id}",
                                createdAt = saved.createdAt,
                            )
                        }
                    }
            }
    }

    fun getSubmissionResult(
        courseSlug: String,
        assignmentId: String,
        submissionId: String,
        userId: String,
        authorizationHeader: String?,
    ): Mono<AssignmentSubmissionResultResponse> {
        val authHeader = requireAuthorizationHeader(authorizationHeader)
        val parsedUserId = parseUserId(userId)
        val parsedSlug = parseCourseSlug(courseSlug)
        val parsedAssignmentId = parseAssignmentId(assignmentId)

        return findAccessibleAssignment(parsedSlug, parsedAssignmentId, parsedUserId)
            .then(findOwnedSubmission(parsedAssignmentId, submissionId, parsedUserId))
            .flatMap { submission ->
                onlineJudgeClient.getSubmissionResult(authHeader, requireNotNull(submission.id))
                    .flatMap { judgeResult ->
                        val now = Instant.now(clock)
                        assignmentSubmissionRepository.save(
                            submission.copy(
                                status = judgeResult.status,
                                updatedAt = now,
                                completedAt = if (isTerminal(judgeResult.status)) now else submission.completedAt,
                            )
                        ).map { saved ->
                            AssignmentSubmissionResultResponse(
                                submissionId = requireNotNull(saved.id),
                                assignmentId = saved.assignmentId,
                                language = saved.language,
                                status = judgeResult.status,
                                testCases = judgeResult.testCases.map { testCase ->
                                    AssignmentSubmissionTestCaseResultResponse(
                                        caseId = testCase.caseId,
                                        status = testCase.status,
                                        timeMs = testCase.timeMs,
                                        memoryMb = testCase.memoryMb,
                                        output = testCase.output,
                                        error = testCase.error,
                                    )
                                },
                                createdAt = saved.createdAt,
                                completedAt = saved.completedAt,
                            )
                        }
                    }
                    .switchIfEmpty(Mono.just(toPendingResultResponse(submission)))
            }
    }

    fun streamSubmissionResult(
        courseSlug: String,
        assignmentId: String,
        submissionId: String,
        userId: String,
        authorizationHeader: String?,
    ): Flux<ServerSentEvent<String>> {
        val authHeader = requireAuthorizationHeader(authorizationHeader)
        val parsedUserId = parseUserId(userId)
        val parsedSlug = parseCourseSlug(courseSlug)
        val parsedAssignmentId = parseAssignmentId(assignmentId)

        return findAccessibleAssignment(parsedSlug, parsedAssignmentId, parsedUserId)
            .then(findOwnedSubmission(parsedAssignmentId, submissionId, parsedUserId))
            .flatMapMany { submission ->
                onlineJudgeClient.streamSubmissionResult(authHeader, requireNotNull(submission.id))
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
            .flatMap { assignment ->
                if (Instant.now(clock) < assignment.startAt) {
                    Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "과제를 찾을 수 없습니다: ${assignmentId.value}"))
                } else {
                    Mono.just(assignment)
                }
            }
    }

    private fun ensureEnrolled(courseId: CourseId, userId: UserId): Mono<Void> {
        return courseEnrollmentRepository.findByCourseIdAndUserId(courseId.value, userId.value)
            .filter { it.status == EnrollmentStatus.ENABLED }
            .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "조회 가능한 코스를 찾을 수 없습니다.")))
            .then()
    }

    private fun findOwnedSubmission(
        assignmentId: AssignmentId,
        submissionId: String,
        userId: UserId,
    ): Mono<AssignmentSubmission> {
        return assignmentSubmissionRepository.findByIdAndAssignmentIdAndUserId(submissionId, assignmentId.value, userId.value)
            .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "제출을 찾을 수 없습니다: $submissionId")))
    }

    private fun ensureSubmissionWindow(assignment: Assignment): Mono<Void> {
        val now = Instant.now(clock)
        if (now.isAfter(assignment.endAt)) {
            return Mono.error(ResponseStatusException(HttpStatus.CONFLICT, "제출 가능한 시간이 지났습니다."))
        }
        return Mono.empty()
    }

    private fun ensureSubmissionLanguageSupported(
        assignment: Assignment,
        language: String,
    ): Mono<Void> {
        if (assignment.metadata.codeTemplates.isEmpty()) {
            return Mono.empty()
        }
        val supportedLanguages = assignment.metadata.codeTemplates.map { it.language.name }.toSet()
        if (language !in supportedLanguages) {
            return Mono.error(ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 제출 언어입니다: $language"))
        }
        return Mono.empty()
    }

    private fun ensureExamplesExist(assignmentId: AssignmentId): Mono<Void> {
        return assignmentExampleRepository.findAllByAssignmentIdOrderBySeq(assignmentId.value)
            .hasElements()
            .flatMap { hasExamples ->
                if (hasExamples) {
                    Mono.empty()
                } else {
                    Mono.error(ResponseStatusException(HttpStatus.CONFLICT, "채점 가능한 예제 테스트 케이스가 없습니다."))
                }
            }
    }

    private fun toPendingResultResponse(submission: AssignmentSubmission): AssignmentSubmissionResultResponse =
        AssignmentSubmissionResultResponse(
            submissionId = requireNotNull(submission.id),
            assignmentId = submission.assignmentId,
            language = submission.language,
            status = submission.status,
            testCases = emptyList(),
            createdAt = submission.createdAt,
            completedAt = submission.completedAt,
        )

    private fun isTerminal(status: AssignmentSubmissionStatus): Boolean =
        status != AssignmentSubmissionStatus.PENDING && status != AssignmentSubmissionStatus.RUNNING

    private fun parseCourseSlug(raw: String): CourseSlug =
        parseOrBadRequest { CourseSlug.from(raw) }

    private fun parseCourseId(raw: String): CourseId =
        parseOrBadRequest { CourseId.from(raw) }

    private fun parseUserId(raw: String): UserId =
        parseOrBadRequest { UserId.from(raw) }

    private fun parseAssignmentId(raw: String): AssignmentId =
        parseOrBadRequest { AssignmentId.from(raw) }

    private fun requireAuthorizationHeader(raw: String?): String {
        val normalized = raw?.trim()
        if (normalized.isNullOrBlank() || !normalized.startsWith("Bearer ")) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization 헤더가 필요합니다.")
        }
        return normalized
    }

    private fun <T> parseOrBadRequest(block: () -> T): T {
        return runCatching(block).getOrElse { error ->
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, error.message ?: "잘못된 요청입니다.")
        }
    }
}
