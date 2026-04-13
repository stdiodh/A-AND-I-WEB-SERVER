package com.example.aandi_post_web_server.assignment.submission.event

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class JudgeCompletedEventParser(
    private val objectMapper: ObjectMapper,
) {

    fun parse(messageBody: String): JudgeCompletedEventParseResult =
        runCatching {
            val rootNode = objectMapper.readTree(messageBody)
            val payloadNode = extractPayload(rootNode)
            val eventType = payloadNode["eventType"]?.asText()?.trim().orEmpty()
            when {
                eventType.isBlank() -> JudgeCompletedEventParseResult.Ignored("missing_event_type")
                eventType != JUDGE_COMPLETED_EVENT_TYPE -> JudgeCompletedEventParseResult.Ignored("unsupported_event_type:$eventType")
                else -> parseJudgeCompletedEvent(payloadNode)
            }
        }.getOrElse { ex ->
            JudgeCompletedEventParseResult.Ignored("invalid_json:${ex.javaClass.simpleName}")
        }

    private fun parseJudgeCompletedEvent(payloadNode: JsonNode): JudgeCompletedEventParseResult {
        val payload = runCatching {
            objectMapper.treeToValue(payloadNode, JudgeCompletedPayload::class.java)
        }.getOrElse { ex ->
            return JudgeCompletedEventParseResult.Ignored("invalid_payload:${ex.javaClass.simpleName}")
        }

        val publicCode = payload.publicCode?.trim().orEmpty()
        if (publicCode.isBlank()) {
            return JudgeCompletedEventParseResult.Ignored("missing_public_code")
        }

        val assignmentId = payload.problemId?.trim().orEmpty()
        if (assignmentId.isBlank()) {
            return JudgeCompletedEventParseResult.Ignored("missing_problem_id")
        }

        val score = payload.score ?: return JudgeCompletedEventParseResult.Ignored("missing_score")
        val passedCases = payload.passedCases ?: return JudgeCompletedEventParseResult.Ignored("missing_passed_cases")
        val totalCases = payload.totalCases ?: return JudgeCompletedEventParseResult.Ignored("missing_total_cases")
        val timestamp = payload.timestamp ?: return JudgeCompletedEventParseResult.Ignored("missing_timestamp")

        return JudgeCompletedEventParseResult.Parsed(
            JudgeCompletedEvent(
                assignmentId = assignmentId,
                publicCode = publicCode,
                score = score,
                passedCases = passedCases,
                totalCases = totalCases,
                timestamp = timestamp,
            )
        )
    }

    private fun extractPayload(rootNode: JsonNode): JsonNode {
        if (rootNode.isObject && rootNode.hasNonNull("Message")) {
            val envelope = objectMapper.treeToValue(rootNode, SnsEnvelope::class.java)
            val message = envelope.message?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("SNS Message 필드가 비어 있습니다.")
            return objectMapper.readTree(message)
        }
        return rootNode
    }

    companion object {
        private const val JUDGE_COMPLETED_EVENT_TYPE = "JUDGE_COMPLETED"
    }
}

sealed interface JudgeCompletedEventParseResult {
    data class Parsed(val event: JudgeCompletedEvent) : JudgeCompletedEventParseResult
    data class Ignored(val reason: String) : JudgeCompletedEventParseResult
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class SnsEnvelope(
    @JsonProperty("Message")
    val message: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class JudgeCompletedPayload(
    val eventType: String? = null,
    val publicCode: String? = null,
    val problemId: String? = null,
    val score: Int? = null,
    val passedCases: Int? = null,
    val totalCases: Int? = null,
    val timestamp: Instant? = null,
)
