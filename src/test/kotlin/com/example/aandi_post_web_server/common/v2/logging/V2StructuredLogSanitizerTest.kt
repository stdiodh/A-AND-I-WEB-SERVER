package com.example.aandi_post_web_server.common.logging.v2

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

class V2StructuredLogSanitizerTest : StringSpec({
    val sanitizer = V2StructuredLogSanitizer(jacksonObjectMapper())

    "민감 필드는 중첩 JSON과 배열에서도 원문을 남기지 않는다" {
        val sanitized = sanitizer.sanitize(
            mapOf(
                "password" to "pass-1234",
                "accessToken" to "access-token-raw",
                "refreshToken" to "refresh-token-raw",
                "Authorization" to "Bearer secret",
                "Authenticate" to "Bearer v2-secret",
                "salt" to "salt-raw",
                "email" to "hood@example.com",
                "phone" to "01012345678",
                "loginId" to "han12345",
                "userName" to "testerName",
                "privateTestCases" to listOf(
                    mapOf("input" to "private input", "expectedOutput" to "private output"),
                ),
                "testCases" to listOf(
                    mapOf("visibility" to "PUBLIC", "input" to "public input", "expectedOutput" to "public output"),
                    mapOf("visibility" to "HIDDEN", "input" to "hidden input", "expectedOutput" to "hidden output"),
                ),
                "submittedCode" to "fun main() = println(\"secret\")",
            )
        ) as Map<*, *>

        sanitized["password"] shouldBe "****"
        sanitized["accessToken"] shouldBe "****"
        sanitized["refreshToken"] shouldBe "****"
        sanitized["Authorization"] shouldBe "****"
        sanitized["Authenticate"] shouldBe null
        sanitized["salt"] shouldBe null
        sanitized["email"] shouldBe "hoo*@example.com"
        sanitized["phone"] shouldBe "010******78"
        sanitized["loginId"] shouldBe "han*****"
        sanitized["userName"] shouldBe "tes*******"
        sanitized["privateTestCases"] shouldBe "****"
        sanitized["submittedCode"] shouldBe "****"

        val testCases = sanitized["testCases"] as List<*>
        val publicCase = testCases[0] as Map<*, *>
        val hiddenCase = testCases[1] as Map<*, *>
        publicCase["input"] shouldBe "public input"
        publicCase["expectedOutput"] shouldBe "****"
        hiddenCase["input"] shouldBe "****"
        hiddenCase["expectedOutput"] shouldBe "****"

        sanitized.toString().shouldNotContainRawSecrets()
    }
})

private fun String.shouldNotContainRawSecrets() {
    listOf(
        "pass-1234",
        "access-token-raw",
        "refresh-token-raw",
        "Bearer secret",
        "Bearer v2-secret",
        "salt-raw",
        "private input",
        "private output",
        "hidden input",
        "hidden output",
        "fun main()",
    ).forEach { forbidden ->
        this shouldNotContain forbidden
    }
}
