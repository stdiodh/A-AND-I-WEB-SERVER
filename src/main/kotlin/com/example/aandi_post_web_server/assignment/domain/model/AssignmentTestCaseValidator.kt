package com.example.aandi_post_web_server.assignment.domain.model

import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentTestCaseRequest
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseVisibility
import org.springframework.stereotype.Component

@Component
class AssignmentTestCaseValidator {

    fun validate(testCases: List<CreateAssignmentTestCaseRequest>) {
        require(testCases.isNotEmpty()) { "testCases must not be empty" }

        val seenSeq = mutableSetOf<Int>()
        var hasGradableCase = false

        testCases.forEachIndexed { index, testCase ->
            require(testCase.seq > 0) { "testCases[$index].seq must be greater than 0" }
            require(seenSeq.add(testCase.seq)) { "duplicated seq value: ${testCase.seq}" }
            require(!containsUnsupportedControlCharacters(testCase.inputValues)) {
                "testCases[$index].inputValues contains unsupported control characters"
            }
            require(!containsUnsupportedControlCharacters(testCase.outputText)) {
                "testCases[$index].outputText contains unsupported control characters"
            }
            require(!isNoInputDescription(testCase.inputValues)) {
                "testCases[$index].inputValues must use empty array for no-input case"
            }

            if (testCase.visibility != AssignmentTestCaseVisibility.EXCLUDED) {
                hasGradableCase = true
            }
        }

        require(hasGradableCase) { "testCases must contain at least one gradable case" }
    }

    private fun containsUnsupportedControlCharacters(values: List<String>): Boolean =
        values.any { value ->
            value.any { ch ->
                val code = ch.code
                (code in 0x00..0x08) || code == 0x0B || code == 0x0C || (code in 0x0E..0x1F) || code == 0x7F
            }
        }

    private fun containsUnsupportedControlCharacters(value: String): Boolean =
        value.any { ch ->
            val code = ch.code
            (code in 0x00..0x08) || code == 0x0B || code == 0x0C || (code in 0x0E..0x1F) || code == 0x7F
        }

    private fun isNoInputDescription(values: List<String>): Boolean {
        if (values.isEmpty()) {
            return false
        }
        if (values.size != 1) {
            return false
        }
        val value = values.single()
        return forbiddenNoInputPatterns.any { it.matches(value) }
    }

    private companion object {
        val forbiddenNoInputPatterns = listOf(
            Regex("^\\s*입력이\\s*없습니다\\.?\\s*$"),
            Regex("^\\s*입력이\\s*존재하지\\s*않습니다\\.?\\s*$"),
            Regex("^\\s*입력이\\s*없다\\.?\\s*$"),
            Regex("^\\s*no\\s+input\\.?\\s*$", setOf(RegexOption.IGNORE_CASE)),
            Regex("^\\s*input\\s+does\\s+not\\s+exist\\.?\\s*$", setOf(RegexOption.IGNORE_CASE)),
            Regex("^\\s*there\\s+is\\s+no\\s+input\\.?\\s*$", setOf(RegexOption.IGNORE_CASE)),
        )
    }
}
