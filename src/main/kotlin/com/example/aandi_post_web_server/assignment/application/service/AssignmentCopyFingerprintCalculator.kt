package com.example.aandi_post_web_server.assignment.application.service

import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.entity.AssignmentRequirement
import com.example.aandi_post_web_server.assignment.entity.AssignmentTestCase
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import org.springframework.stereotype.Component
import java.security.MessageDigest

@Component
class AssignmentCopyFingerprintCalculator {
    private val objectMapper = JsonMapper.builder()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
        .build()

    fun calculate(
        sourceAssignment: Assignment,
        sourceRequirements: List<AssignmentRequirement>,
        sourceTestCases: List<AssignmentTestCase>,
    ): String {
        val metadata = sourceAssignment.metadata
        val payload = linkedMapOf<String, Any?>(
            "title" to metadata.title,
            "difficulty" to metadata.difficulty.name,
            "description" to metadata.description,
            "timeLimitMinutes" to metadata.timeLimitMinutes,
            "learningGoals" to metadata.learningGoals.map { it },
            "codeTemplates" to metadata.codeTemplates
                .sortedWith(compareBy({ it.language.name }, { it.functionTemplate }))
                .map {
                    linkedMapOf(
                        "language" to it.language.name,
                        "functionTemplate" to it.functionTemplate,
                    )
                },
            "requirements" to sourceRequirements
                .sortedWith(compareBy<AssignmentRequirement> { it.sortOrder }.thenBy { it.requirementText })
                .map {
                    linkedMapOf(
                        "sortOrder" to it.sortOrder,
                        "requirementText" to it.requirementText,
                    )
                },
            "testCases" to sourceTestCases
                .sortedWith(
                    compareBy<AssignmentTestCase> { it.seq }
                        .thenBy { it.inputValues.joinToString("\u001F") }
                        .thenBy { it.outputText }
                        .thenBy { it.visibility.name }
                )
                .map {
                    linkedMapOf(
                        "seq" to it.seq,
                        "inputValues" to it.inputValues,
                        "outputText" to it.outputText,
                        "visibility" to it.visibility.name,
                    )
                },
        )
        val json = objectMapper.writeValueAsString(payload)
        val digest = MessageDigest.getInstance("SHA-256").digest(json.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
