package com.example.aandi_post_web_server.common.error.v2

open class V2Exception(
    val errorCode: V2ErrorCode,
    override val message: String = errorCode.messageTemplate,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class V2ValidationException(
    errorCode: V2ErrorCode = V2ErrorCode.VALIDATE_ERROR,
    message: String = errorCode.messageTemplate,
    cause: Throwable? = null,
) : V2Exception(errorCode, message, cause)
