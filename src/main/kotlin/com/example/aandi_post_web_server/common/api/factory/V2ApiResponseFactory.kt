package com.example.aandi_post_web_server.common.api.factory

import com.example.aandi_post_web_server.common.api.envelope.V2ApiEnvelope
import com.example.aandi_post_web_server.common.api.envelope.V2ApiError
import com.example.aandi_post_web_server.common.error.v2.V2ErrorCode

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
