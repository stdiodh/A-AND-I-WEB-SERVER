package com.example.aandi_post_web_server.common.logging.v2

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class V2StructuredLogSanitizerBranchTest : StringSpec({
    val sanitizer = V2StructuredLogSanitizer(jacksonObjectMapper())

    fun sanitizedField(name: String, value: Any?): Any? = sanitizer.sanitizeMap(mapOf(name to value))[name]

    "비밀값 계열 필드는 이름 변형과 관계없이 ****로 가린다" {
        listOf(
            "newPassword",
            "access_token",
            "REFRESH-TOKEN",
            "token",
            "idToken",
            "authorization",
            "authorizations",
            "proxyAuthorization",
            "secret",
            "webhookSecret",
            "credential",
            "credentials",
            "userCredentialInfo",
            "privateKey",
            "rsaPrivateKey",
            "client_secret",
            "session",
            "userSession",
            "cookie",
            "setCookie",
        ).forEach { field ->
            withClue(field) { sanitizedField(field, "raw-$field") shouldBe "****" }
        }
    }

    "Authenticate와 salt는 값 대신 null로 남긴다" {
        sanitizer.sanitizeMap(mapOf("AUTHENTICATE" to "Bearer x", "Salt" to "s")) shouldBe
            mapOf("AUTHENTICATE" to null, "Salt" to null)
    }

    "비밀값 필드는 객체와 배열 값도 통째로 가린다" {
        sanitizedField("credentials", mapOf("user" to "u", "pass" to "p")) shouldBe "****"
        sanitizedField("passwords", listOf("a", "b")) shouldBe "****"
    }

    "접미사 개인정보 필드는 앞 세 글자만 남기는 식별자 규칙으로 마스킹한다" {
        listOf(
            Triple("contactEmail", "hood@example.com", "hoo*************"),
            Triple("mobilePhone", "01098765432", "010********"),
            Triple("adminLoginId", "admin01", "adm****"),
            Triple("display_username", "tester", "tes***"),
        ).forEach { (field, raw, expected) ->
            withClue(field) { sanitizedField(field, raw) shouldBe expected }
        }
    }

    "짧거나 비어 있는 개인정보 값도 원문을 남기지 않는다" {
        sanitizedField("loginId", "ab") shouldBe "**"
        sanitizedField("phone", "1234567") shouldBe "*******"
        sanitizedField("email", "@example.com") shouldBe "@ex*********"
        sanitizedField("email", "no-at-sign") shouldBe "no-*******"
        sanitizedField("username", "") shouldBe ""
    }

    "숨김 테스트케이스 계열 필드는 내용을 통째로 가린다" {
        listOf("hiddenTestCases", "hidden_case", "myPrivateTestCaseList", "hiddenTestCaseIds").forEach { field ->
            withClue(field) { sanitizedField(field, listOf(mapOf("input" to "1", "output" to "2"))) shouldBe "****" }
        }
    }

    "코드와 입출력 payload 필드는 이름 변형까지 가린다" {
        listOf(
            "expectedOutput",
            "caseExpectedOutput",
            "input",
            "stdinInput",
            "output",
            "programOutput",
            "code",
            "publicCode",
            "sourcecode",
            "submittedSource",
            "userCode",
            "userAnswerCode",
            "codeContent",
        ).forEach { field ->
            withClue(field) { sanitizedField(field, "raw") shouldBe "****" }
        }
    }

    "visibility, caseVisibility, isPrivate, private, hidden 표시가 있는 객체는 입출력 자식을 가리고 설명은 유지한다" {
        listOf(
            mapOf("caseVisibility" to "Private"),
            mapOf("visibility" to "hidden-case"),
            mapOf("isPrivate" to true),
            mapOf("private" to true),
            mapOf("hidden" to true),
        ).forEach { marker ->
            val sanitized = sanitizedField(
                "testCase",
                marker + mapOf(
                    "input" to "1 2",
                    "expectedOutput" to "3",
                    "submittedCode" to "print(3)",
                    "description" to "덧셈",
                ),
            ) as Map<*, *>

            withClue(marker) {
                sanitized["input"] shouldBe "****"
                sanitized["expectedOutput"] shouldBe "****"
                sanitized["submittedCode"] shouldBe "****"
                sanitized["description"] shouldBe "덧셈"
            }
        }
    }

    "공개 객체의 일반 필드와 숫자, 실수, 불리언, null 값은 타입을 유지한다" {
        val sanitized = sanitizedField(
            "testCase",
            mapOf(
                "visibility" to "PUBLIC",
                "isPrivate" to false,
                "hidden" to null,
                "seq" to 3,
                "score" to 9.5,
                "enabled" to true,
                "note" to null,
            ),
        ) as Map<*, *>

        sanitized["seq"] shouldBe 3L
        sanitized["score"] shouldBe 9.5
        sanitized["enabled"] shouldBe true
        sanitized["isPrivate"] shouldBe false
        sanitized["hidden"].shouldBeNull()
        sanitized["note"].shouldBeNull()
        sanitized["visibility"] shouldBe "PUBLIC"
    }

    "최상위 값은 필드 이름 없이 원시 값과 배열로 변환한다" {
        sanitizer.sanitize(null).shouldBeNull()
        sanitizer.sanitize("plain") shouldBe "plain"
        sanitizer.sanitize(listOf(1, 2.5, false)) shouldBe listOf(1L, 2.5, false)
    }

    "직렬화할 수 없는 값은 null, 직렬화할 수 없는 맵은 빈 맵으로 대체한다" {
        sanitizer.sanitize(Unserializable()).shouldBeNull()
        sanitizer.sanitizeMap(mapOf("value" to Unserializable())).shouldBeEmpty()
    }
})

private class Unserializable {
    @Suppress("unused")
    val value: String
        get() = throw IllegalStateException("cannot serialize")
}
