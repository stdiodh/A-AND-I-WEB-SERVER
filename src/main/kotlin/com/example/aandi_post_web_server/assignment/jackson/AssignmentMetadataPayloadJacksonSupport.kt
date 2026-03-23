package com.example.aandi_post_web_server.assignment.jackson

import com.example.aandi_post_web_server.assignment.dtos.AssignmentCodeTemplatePayload
import com.example.aandi_post_web_server.assignment.dtos.AssignmentMetadataPayload
import com.example.aandi_post_web_server.assignment.dtos.AssignmentProblemDetailPayload
import com.example.aandi_post_web_server.assignment.dtos.AssignmentSubmissionGuidePayload
import com.example.aandi_post_web_server.assignment.dtos.CreateAssignmentLearningGoalRequest
import com.example.aandi_post_web_server.assignment.dtos.CreateAssignmentRequirementRequest
import com.example.aandi_post_web_server.assignment.dtos.CreateAssignmentTestCaseRequest
import com.example.aandi_post_web_server.assignment.enum.AssignmentDifficulty
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.BeanDescription
import com.fasterxml.jackson.databind.DeserializationConfig
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier
import com.fasterxml.jackson.databind.module.SimpleModule
import org.springframework.stereotype.Component
import java.util.IdentityHashMap

@Component
class AssignmentMetadataPayloadTestCasePresenceTracker {
    private val testCasesPresence = IdentityHashMap<AssignmentMetadataPayload, Boolean>()

    fun markTestCasesProvided(payload: AssignmentMetadataPayload, provided: Boolean) {
        synchronized(testCasesPresence) {
            testCasesPresence[payload] = provided
        }
    }

    fun consumeTestCasesProvided(payload: AssignmentMetadataPayload): Boolean? =
        synchronized(testCasesPresence) {
            testCasesPresence.remove(payload)
        }
}

fun assignmentMetadataPayloadModule(
    presenceTracker: AssignmentMetadataPayloadTestCasePresenceTracker,
): Module =
    SimpleModule("assignment-metadata-payload-module").setDeserializerModifier(
        object : BeanDeserializerModifier() {
            override fun modifyDeserializer(
                config: DeserializationConfig,
                beanDesc: BeanDescription,
                deserializer: JsonDeserializer<*>,
            ): JsonDeserializer<*> {
                if (beanDesc.beanClass == AssignmentMetadataPayload::class.java) {
                    return AssignmentMetadataPayloadDeserializer(
                        delegate = deserializer,
                        presenceTracker = presenceTracker,
                    )
                }
                return deserializer
            }
        }
    )

private class AssignmentMetadataPayloadDeserializer(
    @Suppress("UNUSED_PARAMETER")
    private val delegate: JsonDeserializer<*>,
    private val presenceTracker: AssignmentMetadataPayloadTestCasePresenceTracker,
) : JsonDeserializer<AssignmentMetadataPayload>() {

    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): AssignmentMetadataPayload {
        val codec = parser.codec as ObjectMapper
        val root = codec.readTree<JsonNode>(parser)
        validateTestCasesNode(root.get("testCases"), parser)
        val payload = AssignmentMetadataPayload(
            title = codec.readNullable(root.get("title"), String::class.java),
            difficulty = codec.readRequired(root.get("difficulty"), AssignmentDifficulty::class.java, "metadata.difficulty"),
            description = codec.readNullable(root.get("description"), String::class.java),
            requirements = codec.readList(
                root.get("requirements"),
                object : TypeReference<List<CreateAssignmentRequirementRequest>>() {},
            ),
            learningGoals = codec.readList(
                root.get("learningGoals"),
                object : TypeReference<List<CreateAssignmentLearningGoalRequest>>() {},
            ),
            testCases = codec.readList(
                root.get("testCases"),
                object : TypeReference<List<CreateAssignmentTestCaseRequest>>() {},
            ),
            problemDetail = codec.readNullable(root.get("problemDetail"), AssignmentProblemDetailPayload::class.java),
            submissionGuide = codec.readNullable(root.get("submissionGuide"), AssignmentSubmissionGuidePayload::class.java),
            codeTemplates = codec.readList(
                root.get("codeTemplates"),
                object : TypeReference<List<AssignmentCodeTemplatePayload>>() {},
            ),
            attributes = codec.readMap(
                root.get("attributes"),
                object : TypeReference<Map<String, Any?>>() {},
            ),
        )
        presenceTracker.markTestCasesProvided(payload, root.has("testCases"))
        return payload
    }

    private fun validateTestCasesNode(
        testCasesNode: JsonNode?,
        parser: JsonParser,
    ) {
        if (testCasesNode == null) {
            return
        }
        if (!testCasesNode.isArray) {
            throw JsonMappingException.from(parser, "metadata.testCases must be an array")
        }

        testCasesNode.forEachIndexed { index, testCaseNode ->
            if (testCaseNode.isNull) {
                throw JsonMappingException.from(parser, "testCases[$index] must not be null")
            }
            if (!testCaseNode.isObject) {
                throw JsonMappingException.from(parser, "testCases[$index] must be an object")
            }

            validateRequiredField(testCaseNode, index, "seq", parser)
            validateRequiredField(testCaseNode, index, "inputText", parser)
            validateRequiredField(testCaseNode, index, "outputText", parser)
            validateRequiredField(testCaseNode, index, "visibility", parser)
        }
    }

    private fun validateRequiredField(
        testCaseNode: JsonNode,
        index: Int,
        fieldName: String,
        parser: JsonParser,
    ) {
        val fieldNode = testCaseNode.get(fieldName)
        if (fieldNode == null || fieldNode.isNull) {
            throw JsonMappingException.from(parser, "Missing required value: testCases[$index].$fieldName")
        }
    }
}

private fun <T> ObjectMapper.readNullable(node: JsonNode?, clazz: Class<T>): T? {
    if (node == null || node.isNull) {
        return null
    }
    return treeToValue(node, clazz)
}

private fun <T> ObjectMapper.readRequired(node: JsonNode?, clazz: Class<T>, fieldPath: String): T {
    if (node == null || node.isNull) {
        throw JsonMappingException.from(null as JsonParser?, "Missing required value: $fieldPath")
    }
    return treeToValue(node, clazz)
}

private fun <T> ObjectMapper.readList(node: JsonNode?, typeReference: TypeReference<List<T>>): List<T> {
    if (node == null || node.isNull) {
        return emptyList()
    }
    return convertValue(node, typeReference)
}

private fun <K, V> ObjectMapper.readMap(node: JsonNode?, typeReference: TypeReference<Map<K, V>>): Map<K, V> {
    if (node == null || node.isNull) {
        return emptyMap()
    }
    return convertValue(node, typeReference)
}
