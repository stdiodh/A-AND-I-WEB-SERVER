package com.example.aandi_post_web_server.user.service

import com.example.aandi_post_web_server.course.domain.PublicCode
import com.example.aandi_post_web_server.user.client.AuthUserClient
import com.example.aandi_post_web_server.user.dtos.UserSyncRequest
import com.example.aandi_post_web_server.user.dtos.UserSyncResponse
import com.example.aandi_post_web_server.user.entity.ReportUser
import com.example.aandi_post_web_server.user.repository.ReportUserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import java.time.Instant

@Service
class AdminUserSyncService(
    private val authUserClient: AuthUserClient,
    private val reportUserRepository: ReportUserRepository,
) {
    fun syncByPublicCode(request: UserSyncRequest, authorizationHeader: String?): Mono<UserSyncResponse> {
        val publicCode = parsePublicCode(request.publicCode)
        val authHeader = requireAuthorizationHeader(authorizationHeader)
        return authUserClient.findByPublicCode(publicCode.value, authHeader)
            .flatMap { authUser ->
                val now = Instant.now()
                reportUserRepository.save(
                    ReportUser(
                        id = authUser.id,
                        publicCode = authUser.publicCode,
                        username = authUser.username,
                        role = authUser.role,
                        nickname = authUser.nickname,
                        profileImageUrl = authUser.profileImageUrl,
                        syncedAt = now,
                        updatedAt = now,
                    )
                )
            }
            .map { user ->
                UserSyncResponse(
                    userId = user.id,
                    publicCode = user.publicCode,
                    username = user.username,
                    synced = true,
                    source = "AUTH_SERVER",
                    syncedAt = user.syncedAt,
                )
            }
    }

    private fun parsePublicCode(raw: String): PublicCode =
        runCatching { PublicCode.from(raw) }
            .getOrElse { throw ResponseStatusException(HttpStatus.BAD_REQUEST, it.message ?: "잘못된 publicCode입니다.") }

    private fun requireAuthorizationHeader(raw: String?): String {
        if (raw.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization 헤더가 필요합니다.")
        }
        return raw
    }
}
