package com.example.aandi_post_web_server.assignment.jackson

import com.example.aandi_post_web_server.assignment.dtos.UpdateAssignmentRequest
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.shouldBe

class AssignmentMetadataPayloadJacksonSupportTest : StringSpec({
    "testCases visibility 누락은 역직렬화 단계에서 막는다" {
        val tracker = AssignmentMetadataPayloadTestCasePresenceTracker()
        val objectMapper = jacksonObjectMapper().registerModule(assignmentMetadataPayloadModule(tracker))

        val error = shouldThrow<JsonMappingException> {
            objectMapper.readValue<UpdateAssignmentRequest>(
                """
                {
                  "metadata": {
                    "difficulty": "MID",
                    "testCases": [
                      {
                        "seq": 1,
                        "inputText": "in",
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
        val tracker = AssignmentMetadataPayloadTestCasePresenceTracker()
        val objectMapper = jacksonObjectMapper().registerModule(assignmentMetadataPayloadModule(tracker))

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

        tracker.consumeTestCasesProvided(omitted.metadata!!) shouldBe false
        tracker.consumeTestCasesProvided(provided.metadata!!) shouldBe true
    }
})
