package com.example.aandi_post_web_server.user.infrastructure.event

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

@Component
class AuthUserEventParser(
    private val objectMapper: ObjectMapper,
) {

    fun parse(messageBody: String): AuthUserEvent {
        val rootNode = objectMapper.readTree(messageBody)
        val payloadNode = extractPayload(rootNode)
        return objectMapper.treeToValue(payloadNode, AuthUserEvent::class.java)
    }

    private fun extractPayload(rootNode: JsonNode): JsonNode {
        if (rootNode.isObject && rootNode.hasNonNull("Message")) {
            val envelope = objectMapper.treeToValue(rootNode, SnsEnvelope::class.java)
            val message = envelope.message?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("SNS 메시지 본문이 비어 있습니다.")
            return objectMapper.readTree(message)
        }
        return rootNode
    }
}
