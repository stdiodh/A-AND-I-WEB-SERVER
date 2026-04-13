package com.example.aandi_post_web_server.common.error

import com.example.aandi_post_web_server.common.openapi.ApiEnvelope
import com.example.aandi_post_web_server.report.v2.api.ReportApiEnvelope
import com.example.aandi_post_web_server.report.v2.api.ReportApiResponseFactory
import com.example.aandi_post_web_server.report.v2.error.ReportExceptionMapper
import com.example.aandi_post_web_server.report.v2.security.ReportPathMatcher
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.ServerWebInputException

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class GlobalApiExceptionHandler(
    private val errorResponseFactory: ErrorResponseFactory,
) {

    private val log = LoggerFactory.getLogger(GlobalApiExceptionHandler::class.java)

    @ExceptionHandler(WebExchangeBindException::class)
    fun handleWebExchangeBindException(
        ex: WebExchangeBindException,
        exchange: ServerWebExchange,
    ): ResponseEntity<*> {
        if (ReportPathMatcher.isReportV2Path(exchange.request.path.pathWithinApplication().value())) {
            val reportResult = ReportExceptionMapper.fromThrowable(ex)
            logReportByStatus(exchange, reportResult, ex)
            return toReportResponseEntity(reportResult)
        }
        val result = errorResponseFactory.fromValidation(exchange, ex)
        logWarn(exchange, result, ex)
        return toResponseEntity(result)
    }

    @ExceptionHandler(ServerWebInputException::class)
    fun handleServerWebInputException(
        ex: ServerWebInputException,
        exchange: ServerWebExchange,
    ): ResponseEntity<*> {
        if (ReportPathMatcher.isReportV2Path(exchange.request.path.pathWithinApplication().value())) {
            val reportResult = ReportExceptionMapper.fromThrowable(ex)
            logReportByStatus(exchange, reportResult, ex)
            return toReportResponseEntity(reportResult)
        }
        val result = errorResponseFactory.fromServerWebInput(exchange, ex)
        logWarn(exchange, result, ex)
        return toResponseEntity(result)
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(
        ex: ResponseStatusException,
        exchange: ServerWebExchange,
    ): ResponseEntity<*> {
        if (ReportPathMatcher.isReportV2Path(exchange.request.path.pathWithinApplication().value())) {
            val reportResult = ReportExceptionMapper.fromThrowable(ex)
            logReportByStatus(exchange, reportResult, ex)
            return toReportResponseEntity(reportResult)
        }
        val result = errorResponseFactory.fromResponseStatus(exchange, ex)
        logByStatus(exchange, result, ex)
        return toResponseEntity(result)
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(
        ex: AuthenticationException,
        exchange: ServerWebExchange,
    ): ResponseEntity<*> {
        if (ReportPathMatcher.isReportV2Path(exchange.request.path.pathWithinApplication().value())) {
            val reportResult = ReportExceptionMapper.fromThrowable(ex)
            logReportByStatus(exchange, reportResult, ex)
            return toReportResponseEntity(reportResult)
        }
        val result = errorResponseFactory.unauthorized(exchange, ex.message)
        logWarn(exchange, result, ex)
        return toResponseEntity(result)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(
        ex: AccessDeniedException,
        exchange: ServerWebExchange,
    ): ResponseEntity<*> {
        if (ReportPathMatcher.isReportV2Path(exchange.request.path.pathWithinApplication().value())) {
            val reportResult = ReportExceptionMapper.fromThrowable(ex)
            logReportByStatus(exchange, reportResult, ex)
            return toReportResponseEntity(reportResult)
        }
        val result = errorResponseFactory.forbidden(exchange, ex.message)
        logWarn(exchange, result, ex)
        return toResponseEntity(result)
    }

    @ExceptionHandler(Throwable::class)
    fun handleThrowable(
        ex: Throwable,
        exchange: ServerWebExchange,
    ): ResponseEntity<*> {
        if (ReportPathMatcher.isReportV2Path(exchange.request.path.pathWithinApplication().value())) {
            val reportResult = ReportExceptionMapper.fromThrowable(ex)
            logReportByStatus(exchange, reportResult, ex)
            return toReportResponseEntity(reportResult)
        }
        val result = errorResponseFactory.fromThrowable(exchange, ex)
        logByStatus(exchange, result, ex)
        return toResponseEntity(result)
    }

    private fun toResponseEntity(result: ApiErrorResult): ResponseEntity<ApiEnvelope<Nothing?>> {
        return ResponseEntity.status(result.status).body(result.body)
    }

    private fun toReportResponseEntity(result: ReportExceptionMapper.ReportErrorResult): ResponseEntity<ReportApiEnvelope<Nothing?>> {
        return ResponseEntity.status(result.status)
            .body(ReportApiResponseFactory.failure(result.errorCode, result.message))
    }

    private fun logWarn(exchange: ServerWebExchange, result: ApiErrorResult, ex: Throwable) {
        val requestId = RequestIdSupport.resolveRequestId(exchange)
        val errorCode = result.body.error?.code ?: ErrorCode.INTERNAL_ERROR.name
        log.warn(
            "[requestId={}] {} {} -> {} {} ({})",
            requestId,
            exchange.request.method,
            exchange.request.path.pathWithinApplication().value(),
            result.status.value(),
            errorCode,
            ex.javaClass.simpleName,
        )
    }

    private fun logByStatus(exchange: ServerWebExchange, result: ApiErrorResult, ex: Throwable) {
        val requestId = RequestIdSupport.resolveRequestId(exchange)
        val errorCode = result.body.error?.code ?: ErrorCode.INTERNAL_ERROR.name
        if (result.status.value() >= 500) {
            log.error(
                "[requestId={}] {} {} -> {} {} ({})",
                requestId,
                exchange.request.method,
                exchange.request.path.pathWithinApplication().value(),
                result.status.value(),
                errorCode,
                ex.javaClass.simpleName,
                ex,
            )
            return
        }

        logWarn(exchange, result, ex)
    }

    private fun logReportByStatus(
        exchange: ServerWebExchange,
        result: ReportExceptionMapper.ReportErrorResult,
        ex: Throwable,
    ) {
        val requestId = RequestIdSupport.resolveRequestId(exchange)
        if (result.status.value() >= 500) {
            log.error(
                "[requestId={}] {} {} -> {} {} ({})",
                requestId,
                exchange.request.method,
                exchange.request.path.pathWithinApplication().value(),
                result.status.value(),
                result.errorCode.code,
                ex.javaClass.simpleName,
                ex,
            )
            return
        }

        log.warn(
            "[requestId={}] {} {} -> {} {} ({})",
            requestId,
            exchange.request.method,
            exchange.request.path.pathWithinApplication().value(),
            result.status.value(),
            result.errorCode.code,
            ex.javaClass.simpleName,
        )
    }
}
