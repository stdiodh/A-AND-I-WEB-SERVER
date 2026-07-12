package com.example.aandi_post_web_server.assignment.infrastructure.jackson

import com.example.aandi_post_web_server.assignment.api.dto.UpdateAssignmentRequest
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class AssignmentRequestJacksonConfigTest : StringSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    init {
        "production ObjectMapper tracks an explicitly empty testCases field" {
            val request = objectMapper.readValue(
                """
                {
                  "metadata": {
                    "difficulty": "MID",
                    "testCases": []
                  }
                }
                """.trimIndent(),
                UpdateAssignmentRequest::class.java,
            )

            val metadata = requireNotNull(request.metadata)
            metadata.testCases shouldBe emptyList()
            metadata.testCasesProvided shouldBe true
        }
    }
}
