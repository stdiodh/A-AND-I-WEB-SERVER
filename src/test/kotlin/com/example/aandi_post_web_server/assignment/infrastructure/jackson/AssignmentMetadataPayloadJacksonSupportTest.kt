package com.example.aandi_post_web_server.assignment.infrastructure.jackson

import com.example.aandi_post_web_server.assignment.api.dto.AssignmentMetadataPayload
import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentTestCaseRequest
import com.example.aandi_post_web_server.assignment.api.dto.UpdateAssignmentRequest
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentDifficulty
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.shouldBe

class AssignmentMetadataPayloadJacksonSupportTest : StringSpec({
    "testCases visibility 누락은 역직렬화 단계에서 막는다" {
        val objectMapper = jacksonObjectMapper().registerModule(assignmentMetadataPayloadModule())

        val error = shouldThrow<JsonMappingException> {
            objectMapper.readValue<UpdateAssignmentRequest>(
                """
                {
                  "metadata": {
                    "difficulty": "MID",
                    "testCases": [
                      {
                        "seq": 1,
                        "inputValues": ["in"],
                        "outputText": "out"
                      }
                    ]
                  }
                }
                """.trimIndent()
            )
        }

        error.message shouldContain "Missing required value: testCases[0].visibility"
    }

    "PATCH metadata 에서 testCases 생략 여부를 추적한다" {
        val objectMapper = jacksonObjectMapper().registerModule(assignmentMetadataPayloadModule())

        val omitted = objectMapper.readValue<UpdateAssignmentRequest>(
            """
            {
              "metadata": {
                "title": "updated title",
                "difficulty": "MID",
                "description": "updated description"
              }
            }
            """.trimIndent()
        )
        val provided = objectMapper.readValue<UpdateAssignmentRequest>(
            """
            {
              "metadata": {
                "title": "updated title",
                "difficulty": "MID",
                "description": "updated description",
                "testCases": []
              }
            }
            """.trimIndent()
        )

        omitted.metadata!!.testCasesProvided shouldBe false
        provided.metadata!!.testCasesProvided shouldBe true
        objectMapper.writeValueAsString(provided) shouldNotContain "testCasesProvided"
    }

    "프로그램에서 생성하거나 copy한 metadata도 testCases 존재 여부를 값에서 계산한다" {
        val original = AssignmentMetadataPayload(
            difficulty = AssignmentDifficulty.MID,
        )
        val copied = original.copy(
            testCases = listOf(
                CreateAssignmentTestCaseRequest(
                    seq = 1,
                    inputValues = emptyList(),
                    outputText = "out",
                )
            )
        )

        original.testCasesProvided shouldBe false
        copied.testCasesProvided shouldBe true
    }
})
