package com.example.aandi_post_web_server.assignment.infrastructure.event

import com.example.aandi_post_web_server.assignment.api.dto.AssignmentTestCaseResponse
import com.example.aandi_post_web_server.assignment.application.port.AssignmentProblemSyncPort
import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentTestCaseRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class DirectAssignmentProblemSyncAdapter(
    private val assignmentRepository: AssignmentRepository,
    private val assignmentTestCaseRepository: AssignmentTestCaseRepository,
    private val eventMapper: AssignmentReportTestCaseEventMapper,
    private val eventPublisher: AssignmentReportTestCaseEventPublisher,
) : AssignmentProblemSyncPort {
    private val log = LoggerFactory.getLogger(DirectAssignmentProblemSyncAdapter::class.java)

    override fun publishCreated(assignmentId: String): Mono<Void> =
        loadSnapshot(assignmentId)
            .flatMap { (assignment, testCases) ->
                publish(eventMapper.created(assignment, testCases))
            }

    override fun publishUpdated(assignmentId: String): Mono<Void> =
        loadSnapshot(assignmentId)
            .flatMap { (assignment, testCases) ->
                publish(eventMapper.updated(assignment, testCases))
            }

    override fun publishDeleted(assignmentId: String): Mono<Void> =
        Mono.defer { eventPublisher.publish(eventMapper.deleted(assignmentId)) }

    private fun loadSnapshot(
        assignmentId: String,
    ): Mono<Pair<Assignment, List<AssignmentTestCaseResponse>>> =
        assignmentRepository.findById(assignmentId)
            .switchIfEmpty(
                Mono.error(
                    IllegalStateException("problem sync 대상 assignment snapshot을 찾을 수 없습니다: $assignmentId")
                )
            )
            .zipWith(
                assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(assignmentId)
                    .map { AssignmentTestCaseResponse(it.seq, it.inputValues, it.outputText, it.visibility) }
                    .collectList()
            )
            .map { it.t1 to it.t2 }

    private fun publish(event: AssignmentReportTestCaseEvent): Mono<Void> {
        log.info(
            "Publishing assignment problem sync event. eventType={}, problemId={}, testCaseCount={}, caseIds={}",
            event.eventType,
            event.problemId,
            event.testCases.size,
            event.testCases.map { it.caseId },
        )
        return eventPublisher.publish(event)
    }
}
