package com.example.aandi_post_web_server.user.application.service

import com.example.aandi_post_web_server.user.application.model.AuthUserEvent
import com.example.aandi_post_web_server.user.application.model.AuthUserEventType
import com.example.aandi_post_web_server.user.application.port.ReportUserSyncStore
import com.example.aandi_post_web_server.user.entity.ReportUser
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class ReportUserSyncService(
    private val reportUserSyncStore: ReportUserSyncStore,
) {

    private val log = LoggerFactory.getLogger(ReportUserSyncService::class.java)

    fun sync(event: AuthUserEvent): Mono<ReportUserSyncOutcome> =
        when (event.eventType) {
            AuthUserEventType.UserProfileUpdated -> upsertUser(event)
            AuthUserEventType.UserDeleted -> deleteUser(event)
        }

    private fun upsertUser(event: AuthUserEvent): Mono<ReportUserSyncOutcome> {
        val effectiveUpdatedAt = event.requireEffectiveUpdatedAt()
        val userId = event.id.requireNonBlank("id")
        val reportUser = ReportUser(
            id = userId,
            publicCode = event.publicCode.requireActivePublicCode(),
            username = event.username.requireNonBlank("username"),
            role = event.role.requireNonBlank("role"),
            nickname = event.nickname?.trim()?.takeIf { it.isNotEmpty() },
            profileImageUrl = event.profileImageUrl?.trim()?.takeIf { it.isNotEmpty() },
            syncedAt = Instant.now(),
            updatedAt = effectiveUpdatedAt,
        )

        return conditionalUpsert(
            write = reportUserSyncStore.upsertActive(reportUser),
            userId = userId,
            effectiveUpdatedAt = effectiveUpdatedAt,
            appliedOutcome = ReportUserSyncOutcome.UPSERTED,
            event = event,
        )
            .doOnError(DuplicateKeyException::class.java) { ex ->
                logDuplicateKeyConflict(event, effectiveUpdatedAt, ex)
            }
    }

    private fun deleteUser(event: AuthUserEvent): Mono<ReportUserSyncOutcome> {
        val effectiveUpdatedAt = event.requireEffectiveUpdatedAt()
        val userId = event.id.requireNonBlank("id")
        val tombstone = ReportUser(
            id = userId,
            publicCode = TOMBSTONE_PUBLIC_CODE_PREFIX + userId,
            username = TOMBSTONE_USERNAME,
            role = TOMBSTONE_ROLE,
            syncedAt = Instant.now(),
            updatedAt = effectiveUpdatedAt,
            deletedAt = effectiveUpdatedAt,
        )

        return conditionalUpsert(
            write = reportUserSyncStore.upsertTombstone(tombstone),
            userId = userId,
            effectiveUpdatedAt = effectiveUpdatedAt,
            appliedOutcome = ReportUserSyncOutcome.DELETED,
            event = event,
        )
            .doOnError(DuplicateKeyException::class.java) { ex ->
                logDuplicateKeyConflict(event, effectiveUpdatedAt, ex)
            }
    }

    private fun conditionalUpsert(
        write: Mono<Void>,
        userId: String,
        effectiveUpdatedAt: Instant,
        appliedOutcome: ReportUserSyncOutcome,
        event: AuthUserEvent,
    ): Mono<ReportUserSyncOutcome> =
        write
            .thenReturn(appliedOutcome)
            .onErrorResume(DuplicateKeyException::class.java) { ex ->
                reportUserSyncStore.findById(userId)
                    .flatMap { existing ->
                        val isStale = when (event.eventType) {
                            AuthUserEventType.UserProfileUpdated ->
                                existing.updatedAt.isAfter(effectiveUpdatedAt) ||
                                    (existing.updatedAt == effectiveUpdatedAt && existing.deletedAt != null)
                            AuthUserEventType.UserDeleted -> existing.updatedAt.isAfter(effectiveUpdatedAt)
                        }
                        if (isStale) {
                            Mono.just(ReportUserSyncOutcome.IGNORED_STALE)
                        } else {
                            Mono.error(ex)
                        }
                    }
                    .switchIfEmpty(
                        Mono.defer {
                            log.error(
                                "report-user sync duplicate key conflict without existing user: userId={}, publicCode={}, eventType={}, eventId={}, updatedAt={}",
                                userId,
                                event.publicCode,
                                event.eventType,
                                event.eventId,
                                effectiveUpdatedAt,
                                ex,
                            )
                            Mono.error(ex)
                        }
                    )
            }

    private fun logDuplicateKeyConflict(
        event: AuthUserEvent,
        effectiveUpdatedAt: Instant,
        ex: DuplicateKeyException,
    ) {
        log.error(
            "report-user sync duplicate key conflict: userId={}, publicCode={}, eventType={}, eventId={}, updatedAt={}",
            event.id,
            event.publicCode,
            event.eventType,
            event.eventId,
            effectiveUpdatedAt,
            ex,
        )
    }

    private fun AuthUserEvent.requireEffectiveUpdatedAt(): Instant =
        effectiveUpdatedAt()
            ?.truncatedTo(ChronoUnit.MILLIS)
            ?: throw IllegalArgumentException(
                "auth user event는 updatedAt 또는 occurredAt이 필요합니다. eventType=${eventType}, id=${id}",
            )

    private fun String?.requireNonBlank(fieldName: String): String =
        this?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("auth user event의 ${fieldName} 값이 비어 있습니다.")

    private fun String?.requireActivePublicCode(): String =
        requireNonBlank("publicCode").also { publicCode ->
            require(!publicCode.startsWith(TOMBSTONE_PUBLIC_CODE_PREFIX)) {
                "auth user event의 publicCode가 예약된 tombstone prefix를 사용합니다."
            }
        }

    private companion object {
        const val TOMBSTONE_PUBLIC_CODE_PREFIX = "__deleted__:"
        const val TOMBSTONE_USERNAME = "__deleted__"
        const val TOMBSTONE_ROLE = "DELETED"
    }
}
