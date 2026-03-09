package com.example.aandi_post_web_server.common.error

import com.example.aandi_post_web_server.common.openapi.ApiEnvelope
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import org.springframework.core.convert.ConversionFailedException
import org.springframework.core.codec.CodecException
import org.springframework.core.codec.DecodingException
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.reactive.resource.NoResourceFoundException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.ServerWebInputException

@Component
class ErrorResponseFactory {

    fun fromThrowable(exchange: ServerWebExchange, throwable: Throwable): ApiErrorResult {
        if (throwable is WebExchangeBindException) {
            return fromValidation(exchange, throwable)
        }
        if (throwable is ServerWebInputException) {
            return fromServerWebInput(exchange, throwable)
        }
        if (throwable is NoResourceFoundException) {
            return fromNotFound(exchange)
        }
        if (throwable is ResponseStatusException) {
            return fromResponseStatus(exchange, throwable)
        }
        if (throwable is AuthenticationException) {
            return unauthorized(exchange, throwable.message)
        }
        if (throwable is AccessDeniedException) {
            return forbidden(exchange, throwable.message)
        }

        val invalidFormat = throwable.findCause<InvalidFormatException>()
        if (invalidFormat != null) {
            return fromInvalidFormat(exchange, invalidFormat)
        }

        val parseException = throwable.findCause<JsonParseException>()
        if (parseException != null) {
            return fromMalformedJson(exchange, parseException)
        }

        if (throwable is DecodingException || throwable is CodecException) {
            return fromJsonDecode(exchange, throwable)
        }

        return internalError(exchange, throwable.message)
    }

    fun unauthorized(exchange: ServerWebExchange, detailMessage: String? = null): ApiErrorResult =
        build(
            exchange = exchange,
            status = HttpStatus.UNAUTHORIZED,
            code = ErrorCode.UNAUTHORIZED,
            message = ErrorCode.UNAUTHORIZED.defaultMessage,
            fallbackMessage = detailMessage,
        )

    fun forbidden(exchange: ServerWebExchange, detailMessage: String? = null): ApiErrorResult =
        build(
            exchange = exchange,
            status = HttpStatus.FORBIDDEN,
            code = ErrorCode.FORBIDDEN,
            message = ErrorCode.FORBIDDEN.defaultMessage,
            fallbackMessage = detailMessage,
        )

    fun internalError(exchange: ServerWebExchange, detailMessage: String? = null): ApiErrorResult =
        build(
            exchange = exchange,
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            code = ErrorCode.INTERNAL_ERROR,
            message = ErrorCode.INTERNAL_ERROR.defaultMessage,
            fallbackMessage = detailMessage,
        )

    fun fromValidation(exchange: ServerWebExchange, ex: WebExchangeBindException): ApiErrorResult {
        val firstFieldError = ex.bindingResult.fieldErrors.firstOrNull()
        val firstFieldErrorMessage = firstFieldError?.let {
            val reason = it.defaultMessage?.takeIf { msg -> msg.isNotBlank() } ?: "값이 유효하지 않습니다."
            "${it.field}: $reason"
        }
        val firstObjectErrorMessage = ex.bindingResult.globalErrors.firstOrNull()?.defaultMessage
        return build(
            exchange = exchange,
            status = HttpStatus.BAD_REQUEST,
            code = ErrorCode.VALIDATION_ERROR,
            message = ErrorCode.VALIDATION_ERROR.defaultMessage,
            fallbackMessage = firstFieldErrorMessage ?: firstObjectErrorMessage,
        )
    }

    fun fromServerWebInput(exchange: ServerWebExchange, ex: ServerWebInputException): ApiErrorResult {
        val conversionFailed = ex.findCause<ConversionFailedException>()
        if (conversionFailed != null) {
            return fromConversionFailed(exchange, conversionFailed)
        }

        val invalidFormat = ex.findCause<InvalidFormatException>()
        if (invalidFormat != null) {
            return fromInvalidFormat(exchange, invalidFormat)
        }

        val parseException = ex.findCause<JsonParseException>()
        if (parseException != null) {
            return fromMalformedJson(exchange, parseException)
        }

        val reason = ex.reason ?: ex.message
        val hasMissingKeyword = reason.contains("Missing", ignoreCase = true)
        val code = if (hasMissingKeyword) ErrorCode.MISSING_REQUIRED_VALUE else ErrorCode.INPUT_ERROR

        return build(
            exchange = exchange,
            status = HttpStatus.BAD_REQUEST,
            code = code,
            message = code.defaultMessage,
            fallbackMessage = reason,
        )
    }

    fun fromResponseStatus(exchange: ServerWebExchange, ex: ResponseStatusException): ApiErrorResult {
        val status = ex.statusCode
        val code = when (status.value()) {
            400 -> ErrorCode.BAD_REQUEST
            401 -> ErrorCode.UNAUTHORIZED
            403 -> ErrorCode.FORBIDDEN
            404 -> ErrorCode.NOT_FOUND
            409 -> ErrorCode.CONFLICT
            422 -> ErrorCode.UNPROCESSABLE_ENTITY
            else -> ErrorCode.INTERNAL_ERROR
        }
        val message = ex.reason?.takeIf { it.isNotBlank() } ?: code.defaultMessage
        return build(
            exchange = exchange,
            status = status,
            code = code,
            message = message,
        )
    }

    fun fromInvalidFormat(exchange: ServerWebExchange, ex: InvalidFormatException): ApiErrorResult {
        if (ex.targetType?.isEnum == true) {
            val enumType = ex.targetType as Class<out Enum<*>>
            val fieldPath = ex.path.joinToString(".") { it.fieldName ?: "[${it.index}]" }
            val message = enumMismatchMessage(
                enumType = enumType,
                rejected = ex.value?.toString(),
                target = fieldPath.takeIf { it.isNotBlank() },
            )
            return build(
                exchange = exchange,
                status = HttpStatus.BAD_REQUEST,
                code = ErrorCode.ENUM_MISMATCH,
                message = message,
            )
        }

        return build(
            exchange = exchange,
            status = HttpStatus.BAD_REQUEST,
            code = ErrorCode.INPUT_ERROR,
            message = ErrorCode.INPUT_ERROR.defaultMessage,
            fallbackMessage = ex.originalMessage,
        )
    }

    fun fromConversionFailed(exchange: ServerWebExchange, ex: ConversionFailedException): ApiErrorResult {
        val targetType = ex.targetType.type as? Class<*>
        if (targetType?.isEnum == true) {
            @Suppress("UNCHECKED_CAST")
            val enumType = targetType as Class<out Enum<*>>
            val message = enumMismatchMessage(
                enumType = enumType,
                rejected = ex.value?.toString(),
                target = ex.targetType.name.takeIf { it.isNotBlank() },
            )
            return build(
                exchange = exchange,
                status = HttpStatus.BAD_REQUEST,
                code = ErrorCode.ENUM_MISMATCH,
                message = message,
            )
        }

        return build(
            exchange = exchange,
            status = HttpStatus.BAD_REQUEST,
            code = ErrorCode.INPUT_ERROR,
            message = ErrorCode.INPUT_ERROR.defaultMessage,
            fallbackMessage = ex.message,
        )
    }

    fun fromMalformedJson(exchange: ServerWebExchange, ex: JsonParseException): ApiErrorResult {
        val message = if (ex.location != null && ex.location.lineNr > 0 && ex.location.columnNr > 0) {
            "${ErrorCode.JSON_PARSE_ERROR.defaultMessage} (line=${ex.location.lineNr}, column=${ex.location.columnNr})"
        } else {
            ErrorCode.JSON_PARSE_ERROR.defaultMessage
        }
        return build(
            exchange = exchange,
            status = HttpStatus.BAD_REQUEST,
            code = ErrorCode.JSON_PARSE_ERROR,
            message = message,
        )
    }

    fun fromJsonDecode(exchange: ServerWebExchange, throwable: Throwable): ApiErrorResult {
        val parseException = throwable.findCause<JsonParseException>()
        if (parseException != null) {
            return fromMalformedJson(exchange, parseException)
        }

        return build(
            exchange = exchange,
            status = HttpStatus.BAD_REQUEST,
            code = ErrorCode.JSON_PARSE_ERROR,
            message = ErrorCode.JSON_PARSE_ERROR.defaultMessage,
            fallbackMessage = throwable.message,
        )
    }

    fun fromNotFound(exchange: ServerWebExchange): ApiErrorResult =
        build(
            exchange = exchange,
            status = HttpStatus.NOT_FOUND,
            code = ErrorCode.NOT_FOUND,
            message = ErrorCode.NOT_FOUND.defaultMessage,
        )

    private fun build(
        exchange: ServerWebExchange,
        status: HttpStatusCode,
        code: ErrorCode,
        message: String,
        fallbackMessage: String? = null,
    ): ApiErrorResult {
        val resolvedMessage = fallbackMessage
            ?.takeIf { it.isNotBlank() }
            ?: message.takeIf { it.isNotBlank() }
            ?: code.defaultMessage
        val body = ApiEnvelope.failure(code = code.name, message = resolvedMessage)
        if (!exchange.response.headers.containsKey(RequestIdSupport.HEADER_NAME)) {
            exchange.response.headers.set(RequestIdSupport.HEADER_NAME, RequestIdSupport.resolveRequestId(exchange))
        }
        return ApiErrorResult(status = status, body = body)
    }

    private inline fun <reified T : Throwable> Throwable.findCause(): T? {
        var current: Throwable? = this
        while (current != null) {
            if (current is T) {
                return current
            }
            current = current.cause
        }
        return null
    }

    private fun enumMismatchMessage(
        enumType: Class<out Enum<*>>,
        rejected: String?,
        target: String?,
    ): String {
        val allowed = enumType.enumConstants.joinToString(", ") { it.name }
        val targetText = target?.let { "$it 값" } ?: "요청 값"
        val rejectedText = rejected?.takeIf { it.isNotBlank() } ?: "null"
        return "$targetText '$rejectedText' 은(는) 올바르지 않습니다. 허용값: [$allowed]"
    }

}
