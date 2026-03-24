package com.example.aandi_post_web_server.assignment.dtos

import com.example.aandi_post_web_server.assignment.enum.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import com.example.aandi_post_web_server.assignment.enum.AssignmentTemplateLanguage
import com.example.aandi_post_web_server.assignment.enum.AssignmentTestCaseVisibility
import io.kotest.core.spec.style.StringSpec
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
                codeTemplates = listOf(
                    AssignmentCodeTemplatePayload(
                        language = AssignmentTemplateLanguage.KOTLIN,
                        functionTemplate = "/* ... */\nfun solution(): String { ... }",
                    )
                ),
            ),
        )

        val comparison = request.toCreateComparisonItemResponse()

        comparison.metadata.codeTemplates.first().language shouldBe AssignmentTemplateLanguage.KOTLIN
        comparison.metadata.codeTemplates.first().functionTemplate shouldBe "/* ... */\nfun solution(): String { ... }"
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
                codeTemplates = listOf(
                    AssignmentCodeTemplateResponse(
                        language = AssignmentTemplateLanguage.KOTLIN,
                        functionTemplate = "/* ... */\nfun solution(): String { ... }",
                    )
                ),
            ),
        )

        val comparison = detail.toCreateComparisonItemResponse()

        comparison.metadata.codeTemplates.first().functionTemplate shouldBe "/* ... */\nfun solution(): String { ... }"
    }
})
