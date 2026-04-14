package com.example.aandi_post_web_server.common.v2.api

import com.example.aandi_post_web_server.common.v2.error.V2ErrorCode

object V2ApiResponseFactory {
    fun <T> success(data: T): V2ApiEnvelope<T> =
        V2ApiEnvelope(
            success = true,
            data = data,
            error = null,
            timestamp = V2ApiEnvelope.now(),
        )

    fun failure(
        errorCode: V2ErrorCode,
        message: String = errorCode.messageTemplate,
    ): V2ApiEnvelope<Nothing?> =
        V2ApiEnvelope(
            success = false,
            data = null,
            error = V2ApiError(
                code = errorCode.code,
                message = message,
                value = errorCode.value,
                alert = errorCode.alert,
            ),
            timestamp = V2ApiEnvelope.now(),
        )
}
