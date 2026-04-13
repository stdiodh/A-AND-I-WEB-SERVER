package com.example.aandi_post_web_server.report.v2.error

open class ReportException(
    val errorCode: ReportErrorCode,
    override val message: String = errorCode.messageTemplate,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class ReportValidationException(
    errorCode: ReportErrorCode = ReportErrorCode.VALIDATE_ERROR,
    message: String = errorCode.messageTemplate,
    cause: Throwable? = null,
) : ReportException(errorCode, message, cause)

class ReportBusinessException(
    errorCode: ReportErrorCode,
    message: String = errorCode.messageTemplate,
    cause: Throwable? = null,
) : ReportException(errorCode, message, cause)

class ReportNotFoundException(
    errorCode: ReportErrorCode,
    message: String = errorCode.messageTemplate,
    cause: Throwable? = null,
) : ReportException(errorCode, message, cause)

class ReportAuthException(
    errorCode: ReportErrorCode,
    message: String = errorCode.messageTemplate,
    cause: Throwable? = null,
) : ReportException(errorCode, message, cause)
