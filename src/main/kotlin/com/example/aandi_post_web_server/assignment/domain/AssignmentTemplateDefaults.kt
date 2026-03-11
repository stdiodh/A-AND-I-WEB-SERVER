package com.example.aandi_post_web_server.assignment.domain

import com.example.aandi_post_web_server.assignment.entity.AssignmentCodeTemplate
import com.example.aandi_post_web_server.assignment.entity.AssignmentSubmissionGuide
import com.example.aandi_post_web_server.assignment.enum.AssignmentTemplateLanguage

object AssignmentTemplateDefaults {
    fun submissionGuide(): AssignmentSubmissionGuide = AssignmentSubmissionGuide()

    fun codeTemplates(): List<AssignmentCodeTemplate> = listOf(
        AssignmentCodeTemplate(
            language = AssignmentTemplateLanguage.KOTLIN,
            commentTemplate =
                """
                /*
                [문제]
                > 이해한 방식으로 문제를 다시 정의해요
                [해석]
                > 문제의 요구사항을 분석한 내용을 작성해요
                [풀이]
                > 적용할 풀이를 순서대로 작성해요
                */
                """.trimIndent(),
            functionTemplate =
                """
                fun solution(): String {
                    var answer = ""
                    return answer
                }
                """.trimIndent(),
            runnableTemplate =
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
                    /* 사용자가 작성할 코드 영역 */
                    var answer = "Hello World!"
                    return answer
                }

                fun main() {
                    /* 컴파일 테스트용 실행 영역 */
                    println(solution())
                }
                """.trimIndent(),
        ),
        AssignmentCodeTemplate(
            language = AssignmentTemplateLanguage.DART,
            commentTemplate =
                """
                /*
                [문제]
                > 이해한 방식으로 문제를 다시 정의해요
                [해석]
                > 문제의 요구사항을 분석한 내용을 작성해요
                [풀이]
                > 적용할 풀이를 순서대로 작성해요
                */
                """.trimIndent(),
            functionTemplate =
                """
                String solution() {
                  var answer = '';
                  return answer;
                }
                """.trimIndent(),
            runnableTemplate =
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
                  /* 사용자가 작성할 코드 영역 */
                  var answer = 'Hello World!';
                  return answer;
                }

                void main() {
                  /* 컴파일 테스트용 실행 영역 */
                  print(solution());
                }
                """.trimIndent(),
        ),
    )
}
