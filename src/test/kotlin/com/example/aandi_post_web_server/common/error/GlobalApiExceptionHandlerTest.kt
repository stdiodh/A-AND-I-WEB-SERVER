package com.example.aandi_post_web_server.common.error

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.example.aandi_post_web_server.common.api.envelope.V2ApiEnvelope
import com.example.aandi_post_web_server.common.error.v2.AssignmentDeactivatedException
import com.example.aandi_post_web_server.common.error.v2.V2ErrorCode
import com.example.aandi_post_web_server.common.openapi.ApiEnvelope
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.slf4j.LoggerFactory
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebInputException

class GlobalApiExceptionHandlerTest : StringSpec({
    val handler = GlobalApiExceptionHandler(ErrorResponseFactory())
    val logger = LoggerFactory.getLogger(GlobalApiExceptionHandler::class.java) as Logger
    val appender = ListAppender<ILoggingEvent>()

    beforeSpec {
        appender.start()
        logger.addAppender(appender)
    }

    afterSpec {
        logger.detachAppender(appender)
        appender.stop()
    }

    beforeTest {
        appender.list.clear()
    }

    "v1 검증 실패는 첫 field 오류를 VALIDATION_ERROR envelope로 반환하고 WARN 로그를 남긴다" {
        val exchange = v1Exchange("/v1/admin/courses/back-basic/enrollments")

        val response = handler.handleWebExchangeBindException(bindException("publicCode", "must not be blank"), exchange)

        response.statusCode shouldBe HttpStatus.BAD_REQUEST
        response.v1Body().error?.code shouldBe "VALIDATION_ERROR"
        response.v1Body().error?.message shouldBe "publicCode: must not be blank"
        exchange.response.headers.getFirst(RequestIdSupport.HEADER_NAME) shouldBe "req-handler-1"
        val event = appender.list.single()
        event.level shouldBe Level.WARN
        event.formattedMessage shouldBe
            "[requestId=req-handler-1] POST /v1/admin/courses/back-basic/enrollments -> 400 VALIDATION_ERROR (WebExchangeBindException)"
    }

    "v2 검증 실패는 v2 VALIDATE_ERROR envelope로 반환한다" {
        val response = handler.handleWebExchangeBindException(
            bindException("title", "must not be blank"),
            v2Exchange("/v2/admin/courses"),
        )

        response.statusCode shouldBe HttpStatus.BAD_REQUEST
        val body = response.v2Body()
        body.success shouldBe false
        body.data.shouldBeNull()
        body.error?.code shouldBe 40301
        body.error?.value shouldBe "VALIDATE_ERROR"
        body.error?.message shouldBe "title: must not be blank"
        body.error?.alert shouldBe V2ErrorCode.VALIDATE_ERROR.alert
        appender.list.single().level shouldBe Level.WARN
    }

    "v1 입력 오류의 Missing 사유는 MISSING_REQUIRED_VALUE로 반환한다" {
        val response = handler.handleServerWebInputException(
            ServerWebInputException("Missing request parameter 'weekNo'"),
            v1Exchange("/v1/courses/back-basic/assignments"),
        )

        response.statusCode shouldBe HttpStatus.BAD_REQUEST
        response.v1Body().error?.code shouldBe "MISSING_REQUIRED_VALUE"
        response.v1Body().error?.message shouldBe "Missing request parameter 'weekNo'"
    }

    "v2 입력 오류는 사유를 유지한 INPUT_ERROR로 반환한다" {
        val response = handler.handleServerWebInputException(
            ServerWebInputException("Type mismatch."),
            v2Exchange("/api/v2/courses/back-basic/assignments"),
        )

        response.statusCode shouldBe HttpStatus.BAD_REQUEST
        response.v2Body().error?.value shouldBe "VALIDATE_ERROR"
        response.v2Body().error?.message shouldBe "Type mismatch."
        appender.list.single().formattedMessage shouldContain "-> 400 40301 (ServerWebInputException)"
    }

    "v1 4xx ResponseStatusException은 사유를 유지하고 WARN 로그를 남긴다" {
        val response = handler.handleResponseStatusException(
            ResponseStatusException(HttpStatus.CONFLICT, "이미 존재하는 코스 slug입니다: back-basic"),
            v1Exchange("/v1/admin/courses"),
        )

        response.statusCode shouldBe HttpStatus.CONFLICT
        response.v1Body().error?.code shouldBe "CONFLICT"
        response.v1Body().error?.message shouldBe "이미 존재하는 코스 slug입니다: back-basic"
        appender.list.single().level shouldBe Level.WARN
    }

    "v1 5xx ResponseStatusException은 내부 사유를 숨기고 ERROR 로그에 예외를 남긴다" {
        val exception = ResponseStatusException(HttpStatus.BAD_GATEWAY, "upstream password=secret")

        val response = handler.handleResponseStatusException(exception, v1Exchange("/v1/courses"))

        response.statusCode shouldBe HttpStatus.BAD_GATEWAY
        response.v1Body().error?.code shouldBe "INTERNAL_ERROR"
        response.v1Body().error?.message shouldBe ErrorCode.INTERNAL_ERROR.defaultMessage
        val event = appender.list.single()
        event.level shouldBe Level.ERROR
        event.formattedMessage shouldBe "[requestId=req-handler-1] POST /v1/courses -> 502 INTERNAL_ERROR (ResponseStatusException)"
        event.throwableProxy.shouldNotBeNull().message shouldContain "upstream password=secret"
    }

    "v2 ResponseStatusException 404는 RESOURCE_NOT_FOUND로 반환한다" {
        val response = handler.handleResponseStatusException(
            ResponseStatusException(HttpStatus.NOT_FOUND, "코스를 찾을 수 없습니다: missing"),
            v2Exchange("/v2/courses/missing"),
        )

        response.statusCode shouldBe HttpStatus.NOT_FOUND
        response.v2Body().error?.code shouldBe 96501
        response.v2Body().error?.message shouldBe "코스를 찾을 수 없습니다: missing"
    }

    "인증 예외는 v1 UNAUTHORIZED와 v2 AUTHENTICATE_REQUIRED로 각각 반환한다" {
        val v1 = handler.handleAuthenticationException(BadCredentialsException("token expired"), v1Exchange("/v1/courses"))
        val v2 = handler.handleAuthenticationException(BadCredentialsException("token expired"), v2Exchange("/v2/courses"))

        v1.statusCode shouldBe HttpStatus.UNAUTHORIZED
        v1.v1Body().error?.code shouldBe "UNAUTHORIZED"
        v1.v1Body().error?.message shouldBe "token expired"
        v2.statusCode shouldBe HttpStatus.UNAUTHORIZED
        v2.v2Body().error?.code shouldBe 21101
        v2.v2Body().error?.value shouldBe "UNAUTHORIZED"
        v2.v2Body().error?.message shouldBe "token expired"
    }

    "권한 예외는 v1 FORBIDDEN과 v2 FORBIDDEN으로 각각 반환한다" {
        val v1 = handler.handleAccessDeniedException(AccessDeniedException("admin only"), v1Exchange("/v1/admin/courses"))
        val v2 = handler.handleAccessDeniedException(AccessDeniedException("admin only"), v2Exchange("/v2/admin/courses"))

        v1.statusCode shouldBe HttpStatus.FORBIDDEN
        v1.v1Body().error?.code shouldBe "FORBIDDEN"
        v1.v1Body().error?.message shouldBe "admin only"
        v2.statusCode shouldBe HttpStatus.FORBIDDEN
        v2.v2Body().error?.code shouldBe 21201
        v2.v2Body().error?.message shouldBe "admin only"
    }

    "과제 비활성 예외는 503과 안내 문구를 유지하고 v1은 WARN, v2는 ERROR 로그를 남긴다" {
        val v1 = handler.handleAssignmentDeactivatedException(
            AssignmentDeactivatedException("과제 제출 기간이 아닙니다."),
            v1Exchange("/v1/courses/back-basic/assignments"),
        )
        val v2 = handler.handleAssignmentDeactivatedException(
            AssignmentDeactivatedException("과제 제출 기간이 아닙니다."),
            v2Exchange("/v2/courses/back-basic/assignments"),
        )

        v1.statusCode shouldBe HttpStatus.SERVICE_UNAVAILABLE
        v1.v1Body().error?.code shouldBe "ASSIGNMENT_DEACTIVATED"
        v1.v1Body().error?.message shouldBe "과제 제출 기간이 아닙니다."
        v2.statusCode shouldBe HttpStatus.SERVICE_UNAVAILABLE
        v2.v2Body().error?.code shouldBe 50301
        v2.v2Body().error?.message shouldBe "과제 제출 기간이 아닙니다."
        v2.v2Body().error?.alert shouldBe V2ErrorCode.ASSIGNMENT_DEACTIVATED.alert
        appender.list[0].level shouldBe Level.WARN
        appender.list[1].level shouldBe Level.ERROR
    }

    "알 수 없는 예외는 v1과 v2 모두 내부 메시지를 숨긴 500으로 반환하고 ERROR 로그를 남긴다" {
        val failure = IllegalStateException("mongo password=secret")

        val v1 = handler.handleThrowable(failure, v1Exchange("/v1/courses"))
        val v2 = handler.handleThrowable(failure, v2Exchange("/v2/courses"))

        v1.statusCode shouldBe HttpStatus.INTERNAL_SERVER_ERROR
        v1.v1Body().error?.code shouldBe "INTERNAL_ERROR"
        v1.v1Body().error?.message shouldBe ErrorCode.INTERNAL_ERROR.defaultMessage
        v2.statusCode shouldBe HttpStatus.INTERNAL_SERVER_ERROR
        v2.v2Body().error?.code shouldBe 98801
        v2.v2Body().error?.message shouldBe V2ErrorCode.INTERNAL_ERROR.messageTemplate
        appender.list.map { it.level } shouldBe listOf(Level.ERROR, Level.ERROR)
        appender.list[1].formattedMessage shouldBe
            "[requestId=req-handler-1] POST /v2/courses -> 500 98801 (IllegalStateException)"
    }
})

private fun v1Exchange(path: String): MockServerWebExchange =
    MockServerWebExchange.from(
        MockServerHttpRequest.post(path).header(RequestIdSupport.HEADER_NAME, "req-handler-1").build(),
    )

private fun v2Exchange(path: String): MockServerWebExchange = v1Exchange(path)

private fun ResponseEntity<*>.v1Body(): ApiEnvelope<*> {
    val body = body.shouldBeInstanceOf<ApiEnvelope<*>>()
    body.success shouldBe false
    body.data.shouldBeNull()
    return body
}

private fun ResponseEntity<*>.v2Body(): V2ApiEnvelope<*> = body.shouldBeInstanceOf<V2ApiEnvelope<*>>()

private fun bindException(field: String, message: String): WebExchangeBindException {
    val bindingResult = BeanPropertyBindingResult(HandlerBindTarget(""), "request")
    bindingResult.addError(FieldError("request", field, message))
    val method = HandlerBindController::class.java.getDeclaredMethod("handle", HandlerBindTarget::class.java)
    return WebExchangeBindException(MethodParameter(method, 0), bindingResult)
}

private data class HandlerBindTarget(val title: String)

private class HandlerBindController {
    @Suppress("unused", "UNUSED_PARAMETER")
    fun handle(target: HandlerBindTarget) = Unit
}
