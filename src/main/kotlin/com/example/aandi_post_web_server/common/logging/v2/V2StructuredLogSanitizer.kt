package com.example.aandi_post_web_server.common.logging.v2

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

        return sanitizeNode(fieldName = null, node = node, parentHidden = false)
    }

    private fun sanitizeNode(fieldName: String?, node: JsonNode, parentHidden: Boolean): Any? {
        if (node.isNull) {
            return null
        }

        if (fieldName.isNullableSecretField()) {
            return maskedSecret(fieldName!!)
        }
        if (fieldName.isHiddenField()) {
            return "****"
        }
        if (fieldName?.isSensitivePayloadField() == true) {
            return "****"
        }

        if (node.isObject) {
            val hidden = parentHidden || fieldName.isHiddenField() || node.hasHiddenVisibility()
            val sanitized = linkedMapOf<String, Any?>()
            node.fields().forEachRemaining { (childName, childNode) ->
                sanitized[childName] = if (hidden && childName.isHiddenPayloadChild()) {
                    "****"
                } else {
                    sanitizeNode(childName, childNode, hidden)
                }
            }
            return sanitized
        }

        if (node.isArray) {
            return node.map { child -> sanitizeNode(fieldName, child, parentHidden || fieldName.isHiddenField()) }
        }

        val textValue = node.asText()
        return when {
            fieldName == null -> primitiveValue(node)
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
        val normalized = normalizedFieldName()
        return normalized == "password" ||
            normalized.contains("password") ||
            normalized == "accesstoken" ||
            normalized == "refreshtoken" ||
            normalized == "token" ||
            normalized.endsWith("token") ||
            normalized == "authenticate" ||
            normalized == "salt" ||
            normalized == "authorization" ||
            normalized == "authorizations" ||
            normalized.endsWith("authorization") ||
            normalized == "secret" ||
            normalized.endsWith("secret") ||
            normalized == "credential" ||
            normalized == "credentials" ||
            normalized.contains("credential") ||
            normalized == "privatekey" ||
            normalized.endsWith("privatekey") ||
            normalized == "clientsecret" ||
            normalized == "session" ||
            normalized.endsWith("session") ||
            normalized == "cookie" ||
            normalized.endsWith("cookie")
    }

    private fun String.isPartialMaskField(): Boolean {
        val normalized = normalizedFieldName()
        return normalized == "email" ||
            normalized == "phone" ||
            normalized == "loginid" ||
            normalized == "username" ||
            normalized.endsWith("email") ||
            normalized.endsWith("phone") ||
            normalized.endsWith("loginid") ||
            normalized.endsWith("username")
    }

    private fun String?.isNullableSecretField(): Boolean =
        this?.isSecretField() ?: false

    private fun String?.isHiddenField(): Boolean {
        val normalized = this?.normalizedFieldName() ?: return false
        return normalized == "privatetestcases" ||
            normalized == "hiddentestcases" ||
            normalized == "hiddencase" ||
            normalized.contains("privatetestcase") ||
            normalized.contains("hiddentestcase") ||
            normalized.contains("hiddencase")
    }

    private fun String.isSensitivePayloadField(): Boolean {
        val normalized = normalizedFieldName()
        return normalized == "expectedoutput" ||
            normalized.endsWith("expectedoutput") ||
            normalized == "sourcecode" ||
            normalized == "submittedcode" ||
            normalized == "submittedsource" ||
            normalized == "usercode" ||
            normalized == "useranswercode" ||
            normalized == "codecontent"
    }

    private fun String.isHiddenPayloadChild(): Boolean {
        val normalized = normalizedFieldName()
        return normalized == "input" ||
            normalized == "output" ||
            normalized == "expectedoutput" ||
            normalized == "sourcecode" ||
            normalized == "submittedcode" ||
            normalized == "usercode"
    }

    private fun JsonNode.hasHiddenVisibility(): Boolean {
        val visibility = path("visibility").takeIf { !it.isMissingNode && !it.isNull }?.asText()
            ?: path("caseVisibility").takeIf { !it.isMissingNode && !it.isNull }?.asText()
        val normalizedVisibility = visibility?.lowercase().orEmpty()
        if (normalizedVisibility.contains("private") || normalizedVisibility.contains("hidden")) {
            return true
        }

        return path("isPrivate").takeIf { !it.isMissingNode && !it.isNull }?.asBoolean(false) == true ||
            path("private").takeIf { !it.isMissingNode && !it.isNull }?.asBoolean(false) == true ||
            path("hidden").takeIf { !it.isMissingNode && !it.isNull }?.asBoolean(false) == true
    }

    private fun String.normalizedFieldName(): String =
        lowercase().filter { it.isLetterOrDigit() }
}
