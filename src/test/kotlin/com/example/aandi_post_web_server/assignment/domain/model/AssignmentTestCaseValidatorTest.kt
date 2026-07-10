package com.example.aandi_post_web_server.assignment.domain.model

import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentTestCaseRequest
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseVisibility
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AssignmentTestCaseValidatorTest : StringSpec({
    val validator = AssignmentTestCaseValidator()

    "빈 문자열 입력과 허용된 개행 문자는 통과한다" {
        validator.validate(
            listOf(
                CreateAssignmentTestCaseRequest(
                    seq = 1,
                    inputValues = emptyList(),
                    outputText = "line1\nline2\tend",
                    visibility = AssignmentTestCaseVisibility.PUBLIC,
                ),
                CreateAssignmentTestCaseRequest(
                    seq = 2,
                    inputValues = listOf("hidden\r\ncase"),
                    outputText = "ok",
                    visibility = AssignmentTestCaseVisibility.HIDDEN,
                ),
            )
        )
    }

    "seq 중복은 bad request 메시지로 막는다" {
        val error = shouldThrow<IllegalArgumentException> {
            validator.validate(
                listOf(
                    CreateAssignmentTestCaseRequest(1, listOf("a"), "b", AssignmentTestCaseVisibility.PUBLIC),
                    CreateAssignmentTestCaseRequest(1, listOf("c"), "d", AssignmentTestCaseVisibility.HIDDEN),
                )
            )
        }

        error.message shouldBe "duplicated seq value: 1"
    }

    "전체 EXCLUDED 는 gradable case 예외로 막는다" {
        val error = shouldThrow<IllegalArgumentException> {
            validator.validate(
                listOf(
                    CreateAssignmentTestCaseRequest(1, listOf("a"), "b", AssignmentTestCaseVisibility.EXCLUDED),
                )
            )
        }

        error.message shouldBe "testCases must contain at least one gradable case"
    }

    "설명형 no-input 문장은 금지한다" {
        val error = shouldThrow<IllegalArgumentException> {
            validator.validate(
                listOf(
                    CreateAssignmentTestCaseRequest(
                        seq = 1,
                        inputValues = listOf("입력이 존재하지 않습니다."),
                        outputText = "result",
                        visibility = AssignmentTestCaseVisibility.PUBLIC,
                    ),
                )
            )
        }

        error.message shouldBe "testCases[0].inputValues must use empty array for no-input case"
    }

    "비정상 제어문자는 금지한다" {
        val error = shouldThrow<IllegalArgumentException> {
            validator.validate(
                listOf(
                    CreateAssignmentTestCaseRequest(
                        seq = 1,
                        inputValues = listOf("bad\u0000input"),
                        outputText = "result",
                        visibility = AssignmentTestCaseVisibility.PUBLIC,
                    ),
                )
            )
        }

        error.message shouldBe "testCases[0].inputValues contains unsupported control characters"
    }

    "빈 배열은 허용하지 않는다" {
        val error = shouldThrow<IllegalArgumentException> {
            validator.validate(emptyList())
        }

        error.message shouldBe "testCases must not be empty"
    }
})
