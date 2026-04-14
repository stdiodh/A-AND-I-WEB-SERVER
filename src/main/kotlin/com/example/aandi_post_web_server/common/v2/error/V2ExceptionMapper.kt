package com.example.aandi_post_web_server.common.v2.error

import org.springframework.core.convert.ConversionFailedException
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebInputException

object V2ExceptionMapper {
    data class V2ErrorResult(
        val errorCode: V2ErrorCode,
        val message: String,
        val status: HttpStatus = errorCode.httpStatus,
    )

    fun fromThrowable(throwable: Throwable): V2ErrorResult {
        if (throwable is V2Exception) {
            return V2ErrorResult(
                errorCode = throwable.errorCode,
                message = throwable.message,
            )
        }

        if (throwable is WebExchangeBindException) {
            return V2ErrorResult(
                errorCode = V2ErrorCode.VALIDATE_ERROR,
                message = firstValidationMessage(throwable),
            )
        }

        if (throwable is ServerWebInputException) {
            val conversionFailed = throwable.findCause<ConversionFailedException>()
            val message = conversionFailed?.message ?: throwable.reason ?: V2ErrorCode.INPUT_ERROR.messageTemplate
            return V2ErrorResult(
                errorCode = V2ErrorCode.INPUT_ERROR,
                message = message,
            )
        }

        if (throwable is AuthenticationException) {
            return V2ErrorResult(
                errorCode = V2ErrorCode.AUTHENTICATE_REQUIRED,
                message = throwable.message ?: V2ErrorCode.AUTHENTICATE_REQUIRED.messageTemplate,
            )
        }

        if (throwable is AccessDeniedException) {
            return V2ErrorResult(
                errorCode = V2ErrorCode.FORBIDDEN,
                message = throwable.message ?: V2ErrorCode.FORBIDDEN.messageTemplate,
            )
        }

        if (throwable is ResponseStatusException) {
            return fromResponseStatus(throwable)
        }

        return V2ErrorResult(
            errorCode = V2ErrorCode.INTERNAL_ERROR,
            message = throwable.message ?: V2ErrorCode.INTERNAL_ERROR.messageTemplate,
        )
    }

    private fun fromResponseStatus(ex: ResponseStatusException): V2ErrorResult {
        val reason = ex.reason?.takeIf { it.isNotBlank() } ?: ex.statusCode.toString()
        val status = ex.statusCode as? HttpStatus ?: HttpStatus.INTERNAL_SERVER_ERROR
        val errorCode = when (status.value()) {
            400 -> V2ErrorCode.INPUT_ERROR
            401 -> V2ErrorCode.AUTHENTICATE_REQUIRED
            403 -> V2ErrorCode.FORBIDDEN
            404 -> V2ErrorCode.RESOURCE_NOT_FOUND
            409 -> V2ErrorCode.CONFLICT
            422 -> V2ErrorCode.UNPROCESSABLE_ENTITY
            else -> V2ErrorCode.INTERNAL_ERROR
        }
        return V2ErrorResult(
            errorCode = errorCode,
            message = reason,
            status = status,
        )
    }

    private fun firstValidationMessage(ex: WebExchangeBindException): String {
        val firstFieldError = ex.bindingResult.fieldErrors.firstOrNull()
        if (firstFieldError != null) {
            val reason = firstFieldError.defaultMessage?.takeIf { it.isNotBlank() } ?: "값이 유효하지 않습니다."
            return "${firstFieldError.field}: $reason"
        }
        val firstGlobalError = ex.bindingResult.globalErrors.firstOrNull()?.defaultMessage
        return firstGlobalError ?: V2ErrorCode.VALIDATE_ERROR.messageTemplate
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
}
