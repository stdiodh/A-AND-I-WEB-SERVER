package com.example.aandi_post_web_server.user.event

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.Instant

enum class AuthUserEventType {
    UserProfileUpdated,
    UserDeleted,
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AuthUserEvent(
    val eventType: AuthUserEventType,
    val eventId: String? = null,
    val occurredAt: Instant? = null,
    val id: String,
    val publicCode: String? = null,
    val username: String? = null,
    val role: String? = null,
    val nickname: String? = null,
    val profileImageUrl: String? = null,
    val updatedAt: Instant? = null,
) {
    fun effectiveUpdatedAt(): Instant? = updatedAt ?: occurredAt
}
