package com.example.aandi_post_web_server.report.v2.error

import com.example.aandi_post_web_server.report.v2.api.ReportApiEnvelope
import com.example.aandi_post_web_server.report.v2.api.ReportApiResponseFactory
import com.example.aandi_post_web_server.report.v2.controller.ReportQueryV2Controller
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(assignableTypes = [ReportQueryV2Controller::class])
@Order(Ordered.HIGHEST_PRECEDENCE)
class ReportExceptionHandler {

    @ExceptionHandler(Throwable::class)
    fun handleThrowable(ex: Throwable): ResponseEntity<ReportApiEnvelope<Nothing?>> {
        val result = ReportExceptionMapper.fromThrowable(ex)
        return ResponseEntity.status(result.status)
            .body(ReportApiResponseFactory.failure(result.errorCode, result.message))
    }
}
