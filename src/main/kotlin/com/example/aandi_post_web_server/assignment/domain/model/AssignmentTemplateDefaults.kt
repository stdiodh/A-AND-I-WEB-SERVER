package com.example.aandi_post_web_server.assignment.domain.model

import com.example.aandi_post_web_server.assignment.entity.AssignmentCodeTemplate
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTemplateLanguage

object AssignmentTemplateDefaults {
    private const val PYTHON_DOCSTRING = "\"\"\""

    fun codeTemplates(): List<AssignmentCodeTemplate> = listOf(
        AssignmentCodeTemplate(
            language = AssignmentTemplateLanguage.KOTLIN,
            functionTemplate =
                """
                /*
                [문제]
                > 이해한 방식으로 문제를 다시 정의해요
                [해석]
                > 문제의 요구사항을 분석한 내용을 작성해요
                [풀이]
                > 적용할 풀이를 순서대로 작성해요
                */
                fun solution(): String {
                    var answer = ""
                    return answer
                }
                """.trimIndent(),
        ),
        AssignmentCodeTemplate(
            language = AssignmentTemplateLanguage.DART,
            functionTemplate =
                """
                /*
                [문제]
                > 이해한 방식으로 문제를 다시 정의해요
                [해석]
                > 문제의 요구사항을 분석한 내용을 작성해요
                [풀이]
                > 적용할 풀이를 순서대로 작성해요
                */
                String solution() {
                  var answer = '';
                  return answer;
                }
                """.trimIndent(),
        ),
        AssignmentCodeTemplate(
            language = AssignmentTemplateLanguage.PYTHON,
            functionTemplate =
                listOf(
                    PYTHON_DOCSTRING,
                    "[문제]",
                    "> 이해한 방식으로 문제를 다시 정의해요",
                    "[해석]",
                    "> 문제의 요구사항을 분석한 내용을 작성해요",
                    "[풀이]",
                    "> 적용할 풀이를 순서대로 작성해요",
                    PYTHON_DOCSTRING,
                    "def solution():",
                    "    answer = \"\"",
                    "    return answer",
                ).joinToString("\n"),
        ),
    )
}
