package com.example.aandi_post_web_server.report.api.v2.error

import org.springframework.core.convert.ConversionFailedException
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebInputException

class ReportExceptionMapper {
    data class ReportErrorResult(
        val errorCode: ReportErrorCode,
        val message: String,
        val status: HttpStatus = errorCode.httpStatus,
    )

    companion object {
        fun fromThrowable(throwable: Throwable): ReportErrorResult {
            if (throwable is ReportException) {
                return ReportErrorResult(
                    errorCode = throwable.errorCode,
                    message = throwable.message,
                )
            }

            if (throwable is WebExchangeBindException) {
                return ReportErrorResult(
                    errorCode = ReportErrorCode.VALIDATE_ERROR,
                    message = firstValidationMessage(throwable),
                )
            }

            if (throwable is ServerWebInputException) {
                val conversionFailed = throwable.findCause<ConversionFailedException>()
                val message = conversionFailed?.message ?: throwable.reason ?: ReportErrorCode.INPUT_ERROR.messageTemplate
                return ReportErrorResult(
                    errorCode = ReportErrorCode.INPUT_ERROR,
                    message = message,
                )
            }

            if (throwable is AuthenticationException) {
                return ReportErrorResult(
                    errorCode = ReportErrorCode.AUTHENTICATE_REQUIRED,
                    message = throwable.message ?: ReportErrorCode.AUTHENTICATE_REQUIRED.messageTemplate,
                )
            }

            if (throwable is AccessDeniedException) {
                return ReportErrorResult(
                    errorCode = ReportErrorCode.REPORT_FORBIDDEN,
                    message = throwable.message ?: ReportErrorCode.REPORT_FORBIDDEN.messageTemplate,
                )
            }

            if (throwable is ResponseStatusException) {
                return fromResponseStatus(throwable)
            }

            return ReportErrorResult(
                errorCode = ReportErrorCode.REPORT_INTERNAL_ERROR,
                message = throwable.message ?: ReportErrorCode.REPORT_INTERNAL_ERROR.messageTemplate,
            )
        }

        private fun fromResponseStatus(ex: ResponseStatusException): ReportErrorResult {
            val reason = ex.reason?.takeIf { it.isNotBlank() } ?: ex.statusCode.toString()
            val errorCode = when (ex.statusCode.value()) {
                400 -> ReportErrorCode.INPUT_ERROR
                401 -> ReportErrorCode.AUTHENTICATE_REQUIRED
                403 -> ReportErrorCode.REPORT_FORBIDDEN
                404 -> if (reason.contains("코스")) ReportErrorCode.COURSE_NOT_FOUND else ReportErrorCode.ASSIGNMENT_NOT_FOUND
                409 -> ReportErrorCode.ASSIGNMENT_ALREADY_SUBMITTED
                else -> ReportErrorCode.REPORT_INTERNAL_ERROR
            }
            val status = ex.statusCode as? HttpStatus ?: errorCode.httpStatus
            return ReportErrorResult(
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
            return firstGlobalError ?: ReportErrorCode.VALIDATE_ERROR.messageTemplate
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
}
