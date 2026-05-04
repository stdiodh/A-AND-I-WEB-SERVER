package com.example.aandi_post_web_server.assignment.api.dto

import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTemplateLanguage
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "언어별 코드 템플릿 요청")
data class AssignmentCodeTemplatePayload(
    @field:Schema(description = "언어", example = "KOTLIN")
    val language: AssignmentTemplateLanguage,
    @field:Schema(description = "함수 템플릿", example = "/* 문제/해석/풀이 */\nfun solution(): String {\n    var answer = \"\"\n    return answer\n}")
    val functionTemplate: String,
)

@Schema(description = "언어별 코드 템플릿 응답")
data class AssignmentCodeTemplateResponse(
    @field:Schema(description = "언어", example = "KOTLIN")
    val language: AssignmentTemplateLanguage,
    @field:Schema(description = "함수 템플릿")
    val functionTemplate: String,
)
