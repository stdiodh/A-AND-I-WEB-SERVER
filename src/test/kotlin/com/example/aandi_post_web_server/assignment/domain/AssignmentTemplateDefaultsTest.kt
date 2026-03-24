package com.example.aandi_post_web_server.assignment.domain

import com.example.aandi_post_web_server.assignment.enum.AssignmentTemplateLanguage
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class AssignmentTemplateDefaultsTest : StringSpec({
    "기본 코드 템플릿은 kotlin dart python 을 모두 제공한다" {
        val templates = AssignmentTemplateDefaults.codeTemplates()

        templates.map { it.language }.shouldContainExactly(
            AssignmentTemplateLanguage.KOTLIN,
            AssignmentTemplateLanguage.DART,
            AssignmentTemplateLanguage.PYTHON,
        )
        templates.last().functionTemplate shouldBe
            """
            ${"\"\"\""}
            [문제]
            > 이해한 방식으로 문제를 다시 정의해요
            [해석]
            > 문제의 요구사항을 분석한 내용을 작성해요
            [풀이]
            > 적용할 풀이를 순서대로 작성해요
            ${"\"\"\""}
            def solution():
                answer = ""
                return answer
            """.trimIndent()
    }
})
