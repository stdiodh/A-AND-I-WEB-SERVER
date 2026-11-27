package com.example.aandi_post_web_server.common.error

import com.example.aandi_post_web_server.common.error.v2.V2ErrorCode
import com.example.aandi_post_web_server.course.domain.model.CourseTrack
import com.fasterxml.jackson.core.JsonLocation
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebInputException

class ErrorResponseFactoryBranchTest : StringSpec({
    val factory = ErrorResponseFactory()

    "상세 메시지 없이 만든 인증, 권한, 과제 비활성 오류는 기본 문구를 사용한다" {
        val unauthorized = factory.unauthorized(branchExchange())
        val forbidden = factory.forbidden(branchExchange())
        val deactivated = factory.assignmentDeactivated(branchExchange())

        unauthorized.status shouldBe HttpStatus.UNAUTHORIZED
        unauthorized.body.error?.message shouldBe ErrorCode.UNAUTHORIZED.defaultMessage
        forbidden.status shouldBe HttpStatus.FORBIDDEN
        forbidden.body.error?.message shouldBe ErrorCode.FORBIDDEN.defaultMessage
        deactivated.status shouldBe HttpStatus.SERVICE_UNAVAILABLE
        deactivated.body.error?.code shouldBe "ASSIGNMENT_DEACTIVATED"
        deactivated.body.error?.message shouldBe V2ErrorCode.ASSIGNMENT_DEACTIVATED.messageTemplate
    }

    "ServerWebInputException의 enum InvalidFormatException 원인은 배열 인덱스를 포함한 경로로 안내한다" {
        val invalidFormat = InvalidFormatException(null, "bad enum", "XX", CourseTrack::class.java).apply {
            prependPath(Any(), "fieldTag")
            prependPath(Any(), 0)
            prependPath(Any(), "courses")
        }

        val result = factory.fromServerWebInput(
            branchExchange(),
            ServerWebInputException("Failed to read HTTP message", null, invalidFormat),
        )

        result.status shouldBe HttpStatus.BAD_REQUEST
        result.body.error?.code shouldBe "ENUM_MISMATCH"
        result.body.error?.message shouldBe "courses.[0].fieldTag 값 'XX' 은(는) 올바르지 않습니다. 허용값: [NO, FL, SP]"
    }

    "경로와 거부값이 없는 enum 오류는 요청 값과 null로 안내한다" {
        val invalidFormat = InvalidFormatException(null, "bad enum", null, CourseTrack::class.java)

        val result = factory.fromInvalidFormat(branchExchange(), invalidFormat)

        result.body.error?.code shouldBe "ENUM_MISMATCH"
        result.body.error?.message shouldBe "요청 값 'null' 은(는) 올바르지 않습니다. 허용값: [NO, FL, SP]"
    }

    "ServerWebInputException의 JsonParseException 원인은 위치 정보를 담은 JSON_PARSE_ERROR로 매핑한다" {
        val parseException = JsonParseException(null, "Unexpected character", JsonLocation(null, 10L, 3, 7))

        val result = factory.fromServerWebInput(
            branchExchange(),
            ServerWebInputException("Failed to read HTTP message", null, parseException),
        )

        result.body.error?.code shouldBe "JSON_PARSE_ERROR"
        result.body.error?.message shouldBe "${ErrorCode.JSON_PARSE_ERROR.defaultMessage} (line=3, column=7)"
    }

    "Missing 키워드가 없는 입력 오류는 INPUT_ERROR와 원래 사유를 반환한다" {
        val result = factory.fromServerWebInput(branchExchange(), ServerWebInputException("Type mismatch."))

        result.body.error?.code shouldBe "INPUT_ERROR"
        result.body.error?.message shouldBe "Type mismatch."
    }

    "사유가 없는 4xx ResponseStatusException은 코드 기본 문구를 사용한다" {
        val result = factory.fromResponseStatus(branchExchange(), ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY))

        result.status shouldBe HttpStatus.UNPROCESSABLE_ENTITY
        result.body.error?.code shouldBe "UNPROCESSABLE_ENTITY"
        result.body.error?.message shouldBe ErrorCode.UNPROCESSABLE_ENTITY.defaultMessage
    }

    "이미 request id 응답 헤더가 있으면 덮어쓰지 않는다" {
        val exchange = branchExchange()
        exchange.response.headers.set(RequestIdSupport.HEADER_NAME, "req-existing")

        factory.internalError(exchange)

        exchange.response.headers[RequestIdSupport.HEADER_NAME] shouldBe listOf("req-existing")
    }
})

private fun branchExchange(): MockServerWebExchange =
    MockServerWebExchange.from(
        MockServerHttpRequest.post("/v1/admin/courses").header(RequestIdSupport.HEADER_NAME, "req-branch").build(),
    )
