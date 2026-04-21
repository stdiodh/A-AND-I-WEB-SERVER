package com.example.aandi_post_web_server.report.api.v2.error

import org.springframework.http.HttpStatus

enum class ReportErrorCode(
    val httpStatus: HttpStatus,
    val code: Int,
    val value: String,
    val messageTemplate: String,
    val alert: String,
) {
    VALIDATE_ERROR(
        httpStatus = HttpStatus.BAD_REQUEST,
        code = 40301,
        value = "VALIDATE_ERROR",
        messageTemplate = "요청 DTO 검증에 실패했습니다.",
        alert = "입력값 형식이 올바르지 않습니다.",
    ),
    INPUT_ERROR(
        httpStatus = HttpStatus.BAD_REQUEST,
        code = 40301,
        value = "VALIDATE_ERROR",
        messageTemplate = "요청 입력을 처리할 수 없습니다.",
        alert = "입력값 형식이 올바르지 않습니다.",
    ),
    HEADER_INVALID(
        httpStatus = HttpStatus.BAD_REQUEST,
        code = 40301,
        value = "VALIDATE_ERROR",
        messageTemplate = "report 헤더 형식이 올바르지 않습니다.",
        alert = "입력값 형식이 올바르지 않습니다.",
    ),
    AUTHENTICATE_REQUIRED(
        httpStatus = HttpStatus.UNAUTHORIZED,
        code = 21101,
        value = "UNAUTHORIZED",
        messageTemplate = "인증 헤더가 없거나 토큰이 유효하지 않습니다.",
        alert = "로그인이 필요합니다.",
    ),
    REPORT_FORBIDDEN(
        httpStatus = HttpStatus.FORBIDDEN,
        code = 21201,
        value = "FORBIDDEN",
        messageTemplate = "report 리소스 접근 권한이 없습니다.",
        alert = "접근 권한이 없습니다.",
    ),
    ASSIGNMENT_NOT_FOUND(
        httpStatus = HttpStatus.NOT_FOUND,
        code = 96501,
        value = "RESOURCE_NOT_FOUND",
        messageTemplate = "과제를 찾을 수 없습니다.",
        alert = "요청한 정보를 찾을 수 없습니다.",
    ),
    COURSE_NOT_FOUND(
        httpStatus = HttpStatus.NOT_FOUND,
        code = 96501,
        value = "RESOURCE_NOT_FOUND",
        messageTemplate = "코스를 찾을 수 없습니다.",
        alert = "요청한 정보를 찾을 수 없습니다.",
    ),
    ASSIGNMENT_ALREADY_SUBMITTED(
        httpStatus = HttpStatus.CONFLICT,
        code = 44501,
        value = "ASSIGNMENT_ALREADY_SUBMITTED",
        messageTemplate = "이미 제출된 과제입니다.",
        alert = "이미 제출된 과제입니다.",
    ),
    REPORT_INTERNAL_ERROR(
        httpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
        code = 98801,
        value = "INTERNAL_SERVER_ERROR",
        messageTemplate = "예기치 못한 내부 오류가 발생했습니다.",
        alert = "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
    ),
}
