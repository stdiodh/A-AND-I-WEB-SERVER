package com.example.aandi_post_web_server.common.error.v2

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.core.MethodParameter
import org.springframework.core.convert.ConversionFailedException
import org.springframework.core.convert.TypeDescriptor
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebInputException

class V2ExceptionMapperBranchTest : StringSpec({
    "field 검증 메시지가 비어 있으면 기본 검증 문구를 붙인다" {
        val result = V2ExceptionMapper.fromThrowable(
            v2BindException { addError(FieldError("request", "title", " ", false, null, null, " ")) },
        )

        result.errorCode shouldBe V2ErrorCode.VALIDATE_ERROR
        result.status shouldBe HttpStatus.BAD_REQUEST
        result.message shouldBe "title: 값이 유효하지 않습니다."
    }

    "field 오류가 없으면 global 검증 오류 메시지를 사용한다" {
        val result = V2ExceptionMapper.fromThrowable(
            v2BindException { addError(ObjectError("request", "startDate must be before endDate")) },
        )

        result.message shouldBe "startDate must be before endDate"
    }

    "검증 오류가 비어 있으면 VALIDATE_ERROR 기본 문구를 사용한다" {
        V2ExceptionMapper.fromThrowable(v2BindException { }).message shouldBe V2ErrorCode.VALIDATE_ERROR.messageTemplate
    }

    "입력 오류의 ConversionFailedException 원인은 변환 실패 메시지를 우선한다" {
        val conversionFailed = ConversionFailedException(
            TypeDescriptor.valueOf(String::class.java),
            TypeDescriptor.valueOf(Int::class.javaObjectType),
            "abc",
            NumberFormatException("For input string: \"abc\""),
        )

        val result = V2ExceptionMapper.fromThrowable(
            ServerWebInputException("Type mismatch.", null, conversionFailed),
        )

        result.errorCode shouldBe V2ErrorCode.INPUT_ERROR
        result.message shouldBe conversionFailed.message
    }

    "인증과 권한 예외의 메시지가 없으면 기본 문구를 사용한다" {
        val authentication = V2ExceptionMapper.fromThrowable(BadCredentialsException(null))
        val accessDenied = V2ExceptionMapper.fromThrowable(AccessDeniedException(null))

        authentication.errorCode shouldBe V2ErrorCode.AUTHENTICATE_REQUIRED
        authentication.message shouldBe V2ErrorCode.AUTHENTICATE_REQUIRED.messageTemplate
        accessDenied.errorCode shouldBe V2ErrorCode.FORBIDDEN
        accessDenied.message shouldBe V2ErrorCode.FORBIDDEN.messageTemplate
    }

    "ResponseStatusException 4xx 상태를 v2 코드로 매핑하고 빈 사유는 상태 문자열로 대체한다" {
        listOf(
            HttpStatus.BAD_REQUEST to V2ErrorCode.INPUT_ERROR,
            HttpStatus.UNAUTHORIZED to V2ErrorCode.AUTHENTICATE_REQUIRED,
            HttpStatus.FORBIDDEN to V2ErrorCode.FORBIDDEN,
            HttpStatus.UNPROCESSABLE_ENTITY to V2ErrorCode.UNPROCESSABLE_ENTITY,
        ).forEach { (status, expectedCode) ->
            val result = V2ExceptionMapper.fromThrowable(ResponseStatusException(status))

            result.errorCode shouldBe expectedCode
            result.status shouldBe status
            result.message shouldBe status.toString()
        }
    }

    "HttpStatus enum에 없는 상태 코드는 500 내부 오류로 정규화한다" {
        val result = V2ExceptionMapper.fromThrowable(
            ResponseStatusException(HttpStatusCode.valueOf(499), "client closed"),
        )

        result.status shouldBe HttpStatus.INTERNAL_SERVER_ERROR
        result.errorCode shouldBe V2ErrorCode.INTERNAL_ERROR
        result.message shouldBe V2ErrorCode.INTERNAL_ERROR.messageTemplate
    }

    "V2ValidationException은 지정한 코드와 메시지를 유지한다" {
        val result = V2ExceptionMapper.fromThrowable(
            V2ValidationException(V2ErrorCode.HEADER_INVALID, "timestamp 헤더 형식이 올바르지 않습니다."),
        )

        result.errorCode shouldBe V2ErrorCode.HEADER_INVALID
        result.status shouldBe HttpStatus.BAD_REQUEST
        result.message shouldBe "timestamp 헤더 형식이 올바르지 않습니다."
    }
})

private fun v2BindException(configure: BeanPropertyBindingResult.() -> Unit): WebExchangeBindException {
    val bindingResult = BeanPropertyBindingResult(V2BindTarget(""), "request").apply(configure)
    val method = V2BindController::class.java.getDeclaredMethod("handle", V2BindTarget::class.java)
    return WebExchangeBindException(MethodParameter(method, 0), bindingResult)
}

private data class V2BindTarget(val title: String)

private class V2BindController {
    @Suppress("unused", "UNUSED_PARAMETER")
    fun handle(target: V2BindTarget) = Unit
}
