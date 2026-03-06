package com.example.aandi_post_web_server.common.error

enum class ErrorCode(val defaultMessage: String) {
    VALIDATION_ERROR("요청 값이 올바르지 않습니다."),
    INPUT_ERROR("요청 입력을 처리할 수 없습니다."),
    JSON_PARSE_ERROR("요청 본문(JSON) 형식이 올바르지 않습니다."),
    ENUM_MISMATCH("열거형 값이 올바르지 않습니다."),
    MISSING_REQUIRED_VALUE("필수 요청 값이 누락되었습니다."),
    UNAUTHORIZED("인증이 필요하거나 토큰이 유효하지 않습니다."),
    FORBIDDEN("요청을 수행할 권한이 없습니다."),
    NOT_FOUND("요청한 리소스를 찾을 수 없습니다."),
    CONFLICT("이미 존재하는 리소스입니다."),
    UNPROCESSABLE_ENTITY("요청은 유효하지만 처리할 수 없습니다."),
    BAD_REQUEST("잘못된 요청입니다."),
    INTERNAL_ERROR("서버 내부 오류가 발생했습니다."),
}
