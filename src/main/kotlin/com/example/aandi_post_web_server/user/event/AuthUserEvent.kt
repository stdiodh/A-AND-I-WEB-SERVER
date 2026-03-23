package com.example.aandi_post_web_server.user.event

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.Instant

enum class AuthUserEventType {
    UserProfileUpdated,
    UserDeleted,
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AuthUserEvent(
    @JsonAlias("eventType", "type")
    val eventType: AuthUserEventType,
    val eventId: String? = null,
    val occurredAt: Instant? = null,
    @JsonAlias("id", "userId")
    val id: String,
    val publicCode: String? = null,
    val username: String? = null,
    val role: String? = null,
    val userTrack: String? = null,
    val cohort: Int? = null,
    val cohortOrder: Int? = null,
    val nickname: String? = null,
    val profileImageUrl: String? = null,
    val updatedAt: Instant? = null,
    val version: Long? = null,
) {
    fun effectiveUpdatedAt(): Instant? = updatedAt ?: occurredAt
}
