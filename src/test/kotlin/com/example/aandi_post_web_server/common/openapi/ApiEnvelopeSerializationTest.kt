package com.example.aandi_post_web_server.common.openapi

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class ApiEnvelopeSerializationTest : StringSpec({

    "success envelope should serialize error as null" {
        val json = jacksonObjectMapper().writeValueAsString(ApiEnvelope.success(mapOf("id" to "course-1")))

        json.shouldContain("\"success\":true")
        json.shouldContain("\"error\":null")
        json.shouldNotContain("\"code\":\"VALIDATION_ERROR\"")
    }

    "failure envelope should serialize error payload" {
        val json = jacksonObjectMapper().writeValueAsString(
            ApiEnvelope.failure(code = "VALIDATION_ERROR", message = "요청 값이 올바르지 않습니다.")
        )

        json.shouldContain("\"success\":false")
        json.shouldContain("\"data\":null")
        json.shouldContain("\"error\":{\"code\":\"VALIDATION_ERROR\",\"message\":\"요청 값이 올바르지 않습니다.\"}")
    }
})

