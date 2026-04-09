package com.example.aandi_post_web_server.report.v2.api

import com.example.aandi_post_web_server.report.v2.error.ReportErrorCode

class ReportApiResponseFactory {
    companion object {
        fun <T> success(data: T): ReportApiEnvelope<T> = ReportApiEnvelope(
            success = ReportApiEnvelope.SUCCESS,
            data = data,
            error = null,
            timestamp = ReportApiEnvelope.now(),
        )

        fun failure(
            errorCode: ReportErrorCode,
            message: String = errorCode.messageTemplate,
        ): ReportApiEnvelope<Nothing?> = ReportApiEnvelope(
            success = ReportApiEnvelope.FAIL,
            data = null,
            error = ReportApiError(
                code = errorCode.code,
                message = message,
                value = errorCode.value,
                alert = errorCode.alert,
            ),
            timestamp = ReportApiEnvelope.now(),
        )
    }
}
