package com.example.aandi_post_web_server.common.logging.v2

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class V2StructuredLogSanitizerFieldRuleTest : StringSpec({
    val sanitizer = V2StructuredLogSanitizer(jacksonObjectMapper())

    fun sanitizedField(name: String, value: Any?): Any? = sanitizer.sanitizeMap(mapOf(name to value))[name]

    "비밀값 규칙은 정확 일치, 포함, 접미사 이름을 모두 ****로 가린다" {
        listOf(
            "password",
            "PASSWORD",
            "newPassword",
            "passwordHint",
            "accessToken",
            "refresh_token",
            "token",
            "idToken",
            "authorization",
            "authorizations",
            "proxyAuthorization",
            "secret",
            "clientSecret",
            "webhookSecret",
            "credential",
            "credentials",
            "userCredentialInfo",
            "privateKey",
            "rsaPrivateKey",
            "session",
            "userSession",
            "cookie",
            "setCookie",
        ).forEach { field ->
            withClue(field) { sanitizedField(field, "raw-value") shouldBe "****" }
        }
    }

    "Authenticate와 salt는 원래 이름이 정확히 같을 때만 null로 남긴다" {
        sanitizedField("Authenticate", "Bearer raw").shouldBeNull()
        sanitizedField("SALT", "raw").shouldBeNull()
        sanitizedField("authen_ticate", "Bearer raw") shouldBe "****"
        sanitizedField("salt_", "raw") shouldBe "****"
    }

    "비밀값 규칙의 접두사나 중간 일치만으로는 가리지 않는다" {
        listOf(
            "tokenizer",
            "sessionId",
            "secretary",
            "authorizationHeader",
            "authenticated",
            "salty",
            "cookieConsent",
            "privateKeyId",
        ).forEach { field ->
            withClue(field) { sanitizedField(field, "raw-value") shouldBe "raw-value" }
        }
    }

    "부분 마스킹 규칙은 정확 일치와 접미사 이름에 적용한다" {
        listOf(
            Triple("email", "hood@example.com", "hoo*@example.com"),
            Triple("contactEmail", "hood@example.com", "hoo*************"),
            Triple("phone", "01012345678", "010******78"),
            Triple("mobilePhone", "01012345678", "010********"),
            Triple("loginId", "han12345", "han*****"),
            Triple("adminLoginId", "admin01", "adm****"),
            Triple("username", "tester", "tes***"),
            Triple("display_username", "tester", "tes***"),
        ).forEach { (field, raw, expected) ->
            withClue(field) { sanitizedField(field, raw) shouldBe expected }
        }
        sanitizedField("emailVerified", "yes") shouldBe "yes"
    }

    "숨김 테스트케이스 규칙은 이름에 숨김 표식이 포함되면 값을 통째로 가린다" {
        listOf(
            "privateTestCases",
            "hiddenTestCases",
            "hiddenCase",
            "hiddenCases",
            "hidden_case",
            "myPrivateTestCaseList",
            "hiddenTestCaseIds",
        ).forEach { field ->
            withClue(field) { sanitizedField(field, listOf(mapOf("note" to "n"))) shouldBe "****" }
        }
        sanitizedField("hiddenFlag", "raw") shouldBe "raw"
    }

    "코드와 입출력 규칙은 접미사와 정확 일치 이름을 가린다" {
        listOf(
            "expectedOutput",
            "caseExpectedOutput",
            "input",
            "stdinInput",
            "output",
            "programOutput",
            "code",
            "publicCode",
            "zipCode",
            "sourceCode",
            "submittedCode",
            "submittedSource",
            "userCode",
            "userAnswerCode",
            "codeContent",
        ).forEach { field ->
            withClue(field) { sanitizedField(field, "raw") shouldBe "****" }
        }
    }

    "코드와 입출력 규칙에 걸리지 않는 이름은 원문을 유지한다" {
        listOf(
            "inputs",
            "codes",
            "outputFormat",
            "submittedSourceFile",
            "codeContentType",
        ).forEach { field ->
            withClue(field) { sanitizedField(field, "raw") shouldBe "raw" }
        }
    }

    "숨김 표시 객체는 입출력 자식을 가리고 나머지 자식은 유지한다" {
        listOf(
            mapOf("visibility" to "PRIVATE"),
            mapOf("visibility" to "hidden-case"),
            mapOf("visibility" to null, "caseVisibility" to "Private"),
            mapOf("caseVisibility" to "HIDDEN"),
            mapOf("isPrivate" to true),
            mapOf("private" to true),
            mapOf("hidden" to true),
            mapOf("visibility" to "PUBLIC"),
            mapOf("hidden" to null, "isPrivate" to false),
        ).forEach { marker ->
            val sanitized = sanitizedField(
                "testCase",
                marker + mapOf(
                    "input" to "1 2",
                    "expectedOutput" to "3",
                    "submittedCode" to "print(3)",
                    "description" to "sum",
                    "nested" to mapOf("output" to "3", "note" to "n"),
                ),
            ) as Map<*, *>

            withClue(marker) {
                sanitized["input"] shouldBe "****"
                sanitized["expectedOutput"] shouldBe "****"
                sanitized["submittedCode"] shouldBe "****"
                sanitized["description"] shouldBe "sum"
                sanitized["nested"] shouldBe mapOf("output" to "****", "note" to "n")
            }
        }
    }
})
