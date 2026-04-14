package com.example.aandi_post_web_server.user.service

import com.example.aandi_post_web_server.user.entity.ReportUser
import com.example.aandi_post_web_server.user.event.AuthUserEvent
import com.example.aandi_post_web_server.user.event.AuthUserEventType
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Instant

@Service
class ReportUserSyncService(
    private val reactiveMongoTemplate: ReactiveMongoTemplate,
) {

    private val log = LoggerFactory.getLogger(ReportUserSyncService::class.java)

    fun sync(event: AuthUserEvent): Mono<ReportUserSyncOutcome> =
        if (event.eventType == AuthUserEventType.UserProfileUpdated) {
            upsertUser(event)
        } else {
            deleteUser(event)
        }

    private fun upsertUser(event: AuthUserEvent): Mono<ReportUserSyncOutcome> {
        val effectiveUpdatedAt = event.requireEffectiveUpdatedAt()
        val reportUser = ReportUser(
            id = event.id.requireNonBlank("id"),
            publicCode = event.publicCode.requireNonBlank("publicCode"),
            username = event.username.requireNonBlank("username"),
            role = event.role.requireNonBlank("role"),
            nickname = event.nickname?.trim()?.takeIf { it.isNotEmpty() },
            profileImageUrl = event.profileImageUrl?.trim()?.takeIf { it.isNotEmpty() },
            syncedAt = Instant.now(),
            updatedAt = effectiveUpdatedAt,
        )

        val updateQuery = Query.query(
            Criteria.where("_id").`is`(reportUser.id).orOperator(
                Criteria.where("updatedAt").lte(effectiveUpdatedAt),
                Criteria.where("updatedAt").exists(false),
            )
        )
        val update = Update()
            .set("publicCode", reportUser.publicCode)
            .set("username", reportUser.username)
            .set("role", reportUser.role)
            .set("nickname", reportUser.nickname)
            .set("profileImageUrl", reportUser.profileImageUrl)
            .set("syncedAt", reportUser.syncedAt)
            .set("updatedAt", reportUser.updatedAt)

        return reactiveMongoTemplate.updateFirst(updateQuery, update, ReportUser::class.java)
            .flatMap { result ->
                when {
                    result.matchedCount > 0L -> Mono.just(ReportUserSyncOutcome.UPSERTED)
                    else -> insertIfAbsent(reportUser, effectiveUpdatedAt, event)
                }
            }
            .doOnError(DuplicateKeyException::class.java) { ex ->
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
    }

    private fun insertIfAbsent(
        reportUser: ReportUser,
        effectiveUpdatedAt: Instant,
        event: AuthUserEvent,
    ): Mono<ReportUserSyncOutcome> =
        reactiveMongoTemplate.insert(reportUser)
            .thenReturn(ReportUserSyncOutcome.UPSERTED)
            .onErrorResume(DuplicateKeyException::class.java) { ex ->
                reactiveMongoTemplate.findById(reportUser.id, ReportUser::class.java)
                    .flatMap { existing ->
                        if (!existing.updatedAt.isBefore(effectiveUpdatedAt)) {
                            Mono.just(ReportUserSyncOutcome.IGNORED_STALE)
                        } else {
                            Mono.error(ex)
                        }
                    }
                    .switchIfEmpty(
                        Mono.defer {
                            log.error(
                                "report-user sync duplicate key conflict without existing user: userId={}, publicCode={}, eventType={}, eventId={}, updatedAt={}",
                                event.id,
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

    private fun deleteUser(event: AuthUserEvent): Mono<ReportUserSyncOutcome> {
        val effectiveUpdatedAt = event.requireEffectiveUpdatedAt()
        val query = Query.query(
            Criteria.where("_id").`is`(event.id.requireNonBlank("id")).orOperator(
                Criteria.where("updatedAt").lte(effectiveUpdatedAt),
                Criteria.where("updatedAt").exists(false),
            )
        )

        return reactiveMongoTemplate.remove(query, ReportUser::class.java)
            .map { result ->
                if (result.deletedCount > 0L) {
                    ReportUserSyncOutcome.DELETED
                } else {
                    ReportUserSyncOutcome.IGNORED_STALE
                }
            }
    }

    private fun AuthUserEvent.requireEffectiveUpdatedAt(): Instant =
        effectiveUpdatedAt() ?: throw IllegalArgumentException(
            "auth user event는 updatedAt 또는 occurredAt이 필요합니다. eventType=${eventType}, id=${id}",
        )

    private fun String?.requireNonBlank(fieldName: String): String =
        this?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("auth user event의 ${fieldName} 값이 비어 있습니다.")
}
