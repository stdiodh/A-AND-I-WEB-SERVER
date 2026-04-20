package com.example.aandi_post_web_server.common.config

import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseVisibility
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.bson.Document
import java.time.Instant
import java.util.Date

class MongoEnumCompatibilityConfigTest : StringSpec({
    "legacy inputText 문서는 inputValues 배열로 읽는다" {
        val converter = DocumentToAssignmentTestCaseReadingConverter()
        val createdAt = Instant.parse("2026-03-24T02:00:42.292Z")

        val testCase = converter.convert(
            Document(
                mapOf(
                    "_id" to "69c1f04a76aab82913aa2c13",
                    "assignmentId" to "49247af2-5328-486e-b608-e2919c941868",
                    "seq" to 1,
                    "inputText" to "1\n2\n3",
                    "outputText" to "6",
                    "visibility" to "PUBLIC",
                    "createdAt" to Date.from(createdAt),
                ),
            ),
        )

        testCase.inputValues shouldContainExactly listOf("1", "2", "3")
        testCase.outputText shouldBe "6"
        testCase.visibility shouldBe AssignmentTestCaseVisibility.PUBLIC
        testCase.createdAt shouldBe createdAt
    }

    "새 inputValues 문서는 그대로 읽는다" {
        val converter = DocumentToAssignmentTestCaseReadingConverter()

        val testCase = converter.convert(
            Document(
                mapOf(
                    "_id" to "69c1f04a76aab82913aa2c13",
                    "assignmentId" to "49247af2-5328-486e-b608-e2919c941868",
                    "seq" to 1,
                    "inputValues" to listOf("param1", "param2"),
                    "outputText" to "+1",
                    "visibility" to "HIDDEN",
                ),
            ),
        )

        testCase.inputValues shouldContainExactly listOf("param1", "param2")
        testCase.visibility shouldBe AssignmentTestCaseVisibility.HIDDEN
    }
})
