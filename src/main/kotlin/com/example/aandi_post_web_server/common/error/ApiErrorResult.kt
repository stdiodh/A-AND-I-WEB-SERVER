package com.example.aandi_post_web_server.common.error

import com.example.aandi_post_web_server.common.openapi.ApiEnvelope
import org.springframework.http.HttpStatusCode

data class ApiErrorResult(
    val status: HttpStatusCode,
    val body: ApiEnvelope<Nothing?>,
)
