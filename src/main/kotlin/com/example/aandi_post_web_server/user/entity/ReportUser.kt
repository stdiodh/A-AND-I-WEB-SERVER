package com.example.aandi_post_web_server.user.entity

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "users")
data class ReportUser(
    @Id
    val id: String,
    @Indexed(unique = true)
    val publicCode: String,
    val username: String,
    val role: String,
    val nickname: String? = null,
    val profileImageUrl: String? = null,
    val syncedAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
