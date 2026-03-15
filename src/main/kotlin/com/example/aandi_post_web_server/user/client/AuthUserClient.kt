package com.example.aandi_post_web_server.user.client

import reactor.core.publisher.Mono

interface AuthUserClient {
    fun findByPublicCode(publicCode: String): Mono<AuthUserLookupPayload>
}

data class AuthApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: AuthApiError? = null,
)

data class AuthApiError(
    val code: String,
    val message: String,
)

data class AuthUserLookupPayload(
    val id: String,
    val username: String,
    val role: String,
    val publicCode: String,
    val nickname: String? = null,
    val profileImageUrl: String? = null,
)
