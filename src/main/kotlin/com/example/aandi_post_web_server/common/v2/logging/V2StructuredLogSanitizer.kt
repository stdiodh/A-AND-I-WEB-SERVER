package com.example.aandi_post_web_server.common.v2.logging

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

class V2StructuredLogSanitizer(
    private val objectMapper: ObjectMapper,
) {

    fun sanitize(value: Any?): Any? {
        if (value == null) {
            return null
        }

        val node = runCatching { objectMapper.valueToTree<JsonNode>(value) }.getOrNull()
            ?: return null

        return sanitizeNode(null, node)
    }

    private fun sanitizeNode(fieldName: String?, node: JsonNode): Any? {
        if (node.isNull) {
            return null
        }

        if (node.isObject) {
            val sanitized = linkedMapOf<String, Any?>()
            node.fields().forEachRemaining { (childName, childNode) ->
                sanitized[childName] = sanitizeNode(childName, childNode)
            }
            return sanitized
        }

        if (node.isArray) {
            return node.map { child -> sanitizeNode(fieldName, child) }
        }

        val textValue = node.asText()
        return when {
            fieldName == null -> primitiveValue(node)
            fieldName.isSecretField() -> maskedSecret(fieldName)
            fieldName.isPartialMaskField() -> partiallyMask(fieldName, textValue)
            else -> primitiveValue(node)
        }
    }

    private fun primitiveValue(node: JsonNode): Any? =
        when {
            node.isTextual -> node.asText()
            node.isIntegralNumber -> node.longValue()
            node.isFloatingPointNumber -> node.doubleValue()
            node.isBoolean -> node.booleanValue()
            else -> node.asText()
        }

    private fun maskedSecret(fieldName: String): Any? =
        when {
            fieldName.equals("authenticate", ignoreCase = true) -> null
            fieldName.equals("salt", ignoreCase = true) -> null
            else -> "****"
        }

    private fun partiallyMask(fieldName: String, value: String): String? {
        if (value.isBlank()) {
            return value
        }

        return when {
            fieldName.equals("email", ignoreCase = true) -> maskEmail(value)
            fieldName.equals("phone", ignoreCase = true) -> maskPhone(value)
            else -> maskIdentifier(value)
        }
    }

    private fun maskIdentifier(value: String): String {
        if (value.length <= 3) {
            return "*".repeat(value.length)
        }
        return value.take(3) + "*".repeat(value.length - 3)
    }

    private fun maskEmail(value: String): String {
        val atIndex = value.indexOf('@')
        if (atIndex <= 0) {
            return maskIdentifier(value)
        }

        val local = value.substring(0, atIndex)
        val domain = value.substring(atIndex)
        return maskIdentifier(local) + domain
    }

    private fun maskPhone(value: String): String {
        if (value.length <= 7) {
            return "*".repeat(value.length)
        }
        return value.take(3) + "*".repeat(value.length - 5) + value.takeLast(2)
    }

    private fun String.isSecretField(): Boolean {
        val normalized = lowercase()
        return normalized == "password" ||
            normalized == "accesstoken" ||
            normalized == "refreshtoken" ||
            normalized == "authenticate" ||
            normalized == "salt" ||
            normalized == "authorization" ||
            normalized == "authorizations" ||
            normalized.endsWith("authorization")
    }

    private fun String.isPartialMaskField(): Boolean {
        val normalized = lowercase()
        return normalized == "email" ||
            normalized == "phone" ||
            normalized == "loginid" ||
            normalized.endsWith("email") ||
            normalized.endsWith("phone") ||
            normalized.endsWith("loginid")
    }
}
