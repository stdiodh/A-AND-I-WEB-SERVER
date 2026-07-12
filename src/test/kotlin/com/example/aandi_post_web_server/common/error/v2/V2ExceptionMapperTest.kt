package com.example.aandi_post_web_server.common.error.v2

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class V2ExceptionMapperTest : StringSpec({
    "내부 오류의 원인 메시지를 기본 문구로 대체한다" {
        val cases = listOf(
            IllegalStateException("database password=secret") to HttpStatus.INTERNAL_SERVER_ERROR,
            V2Exception(V2ErrorCode.INTERNAL_ERROR, "internal host=db.private") to HttpStatus.INTERNAL_SERVER_ERROR,
            ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "stack trace detail",
            ) to HttpStatus.INTERNAL_SERVER_ERROR,
            ResponseStatusException(HttpStatus.BAD_GATEWAY, "upstream token=secret") to HttpStatus.BAD_GATEWAY,
        )

        cases.forEach { (exception, expectedStatus) ->
            val result = V2ExceptionMapper.fromThrowable(exception)

            result.errorCode shouldBe V2ErrorCode.INTERNAL_ERROR
            result.message shouldBe V2ErrorCode.INTERNAL_ERROR.messageTemplate
            result.status shouldBe expectedStatus
        }
    }

    "ResponseStatusException 503은 과제 비활성 기본 문구를 사용한다" {
        val result = V2ExceptionMapper.fromThrowable(
            ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "internal maintenance detail"),
        )

        result.status shouldBe HttpStatus.SERVICE_UNAVAILABLE
        result.errorCode shouldBe V2ErrorCode.ASSIGNMENT_DEACTIVATED
        result.message shouldBe V2ErrorCode.ASSIGNMENT_DEACTIVATED.messageTemplate
    }

    "명시적인 사용자 안내 오류는 상세 문구를 유지한다" {
        val cases = listOf(
            Triple(
                V2Exception(V2ErrorCode.CONFLICT, "이미 등록된 사용자입니다."),
                V2ErrorCode.CONFLICT,
                "이미 등록된 사용자입니다.",
            ),
            Triple(
                ResponseStatusException(HttpStatus.CONFLICT, "이미 등록된 사용자입니다."),
                V2ErrorCode.CONFLICT,
                "이미 등록된 사용자입니다.",
            ),
            Triple(
                AssignmentDeactivatedException("과제 제출 기간이 아닙니다."),
                V2ErrorCode.ASSIGNMENT_DEACTIVATED,
                "과제 제출 기간이 아닙니다.",
            ),
        )

        cases.forEach { (exception, expectedCode, expectedMessage) ->
            val result = V2ExceptionMapper.fromThrowable(exception)

            result.status shouldBe expectedCode.httpStatus
            result.errorCode shouldBe expectedCode
            result.message shouldBe expectedMessage
        }
    }
})
