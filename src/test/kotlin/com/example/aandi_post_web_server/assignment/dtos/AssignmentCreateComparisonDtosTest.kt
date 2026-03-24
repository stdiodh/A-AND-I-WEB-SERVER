package com.example.aandi_post_web_server.assignment.dtos

import com.example.aandi_post_web_server.assignment.enum.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.enum.AssignmentProblemStep
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import com.example.aandi_post_web_server.assignment.enum.AssignmentTemplateLanguage
import com.example.aandi_post_web_server.assignment.enum.AssignmentTestCaseVisibility
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import java.time.Instant

class AssignmentCreateComparisonDtosTest : StringSpec({
    "생성 요청 비교 DTO는 메타데이터 확장 필드를 포함한다" {
        val request = CreateAssignmentRequest(
            weekNo = 3,
            orderInWeek = 1,
            startAt = Instant.parse("2026-03-24T00:00:00Z"),
            endAt = Instant.parse("2026-03-31T00:00:00Z"),
            metadata = AssignmentMetadataPayload(
                title = "데이터 포맷 변환 프로그램",
                difficulty = AssignmentDifficulty.HIGH,
                description = "문제 설명",
                testCases = listOf(
                    CreateAssignmentTestCaseRequest(
                        seq = 1,
                        inputValues = listOf("입력"),
                        outputText = "출력",
                        visibility = AssignmentTestCaseVisibility.PUBLIC,
                    )
                ),
                problemDetail = AssignmentProblemDetailPayload(
                    inputDescription = "문자열 명령이 주어진다.",
                    outputDescription = "변환된 결과를 출력한다.",
                    classification = AssignmentProblemClassificationPayload(
                        algorithmStep = AssignmentProblemStep.STEP1,
                        difficultyStep = 2,
                    ),
                ),
                submissionGuide = AssignmentSubmissionGuidePayload(
                    title = "문제 풀이 템플릿",
                    description = "제출 코드 상단에는 문제-해석-풀이 주석을 작성해야 합니다.",
                    commentSections = listOf("문제", "해석", "풀이"),
                ),
                codeTemplates = listOf(
                    AssignmentCodeTemplatePayload(
                        language = AssignmentTemplateLanguage.KOTLIN,
                        commentTemplate = "/* ... */",
                        functionTemplate = "fun solution(): String { ... }",
                        runnableTemplate = "fun main() { println(solution()) }",
                    )
                ),
                attributes = mapOf("language" to "kotlin"),
            ),
        )

        val comparison = request.toCreateComparisonItemResponse()

        comparison.metadata.problemDetail?.classification?.algorithmStep shouldBe AssignmentProblemStep.STEP1
        comparison.metadata.submissionGuide?.commentSections shouldContainExactly listOf("문제", "해석", "풀이")
        comparison.metadata.codeTemplates.first().language shouldBe AssignmentTemplateLanguage.KOTLIN
        comparison.metadata.attributes shouldContain ("language" to "kotlin")
    }

    "상세 응답 비교 DTO는 메타데이터 확장 필드를 포함한다" {
        val detail = AssignmentDetailResponse(
            id = "assignment-1",
            courseSlug = "back-basic",
            weekNo = 3,
            orderInWeek = 1,
            startAt = Instant.parse("2026-03-24T00:00:00Z"),
            endAt = Instant.parse("2026-03-31T00:00:00Z"),
            status = AssignmentStatus.PUBLISHED,
            publishedAt = Instant.parse("2026-03-24T00:00:00Z"),
            metadata = AssignmentDetailMetadataResponse(
                title = "데이터 포맷 변환 프로그램",
                difficulty = AssignmentDifficulty.HIGH,
                description = "문제 설명",
                testCases = listOf(
                    AssignmentTestCaseResponse(
                        seq = 1,
                        inputValues = listOf("입력"),
                        outputText = "출력",
                        visibility = AssignmentTestCaseVisibility.PUBLIC,
                    )
                ),
                problemDetail = AssignmentProblemDetailResponse(
                    inputDescription = "문자열 명령이 주어진다.",
                    outputDescription = "변환된 결과를 출력한다.",
                    classification = AssignmentProblemClassificationResponse(
                        algorithmStep = AssignmentProblemStep.STEP1,
                        difficultyStep = 2,
                    ),
                ),
                submissionGuide = AssignmentSubmissionGuideResponse(
                    title = "문제 풀이 템플릿",
                    description = "제출 코드 상단에는 문제-해석-풀이 주석을 작성해야 합니다.",
                    commentSections = listOf("문제", "해석", "풀이"),
                ),
                codeTemplates = listOf(
                    AssignmentCodeTemplateResponse(
                        language = AssignmentTemplateLanguage.KOTLIN,
                        commentTemplate = "/* ... */",
                        functionTemplate = "fun solution(): String { ... }",
                        runnableTemplate = "fun main() { println(solution()) }",
                    )
                ),
                attributes = mapOf("language" to "kotlin"),
            ),
        )

        val comparison = detail.toCreateComparisonItemResponse()

        comparison.metadata.problemDetail?.outputDescription shouldBe "변환된 결과를 출력한다."
        comparison.metadata.submissionGuide?.title shouldBe "문제 풀이 템플릿"
        comparison.metadata.codeTemplates.first().functionTemplate shouldBe "fun solution(): String { ... }"
        comparison.metadata.attributes shouldContain ("language" to "kotlin")
    }
})
