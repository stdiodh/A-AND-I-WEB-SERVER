package com.example.aandi_post_web_server.assignment.domain.model

import java.time.Clock
import java.time.Instant

data class EffectiveAssignmentPublication(
    val status: AssignmentStatus,
    val publishedAt: Instant?,
)

class AssignmentPublicationPolicy(
    private val clock: Clock = Clock.systemUTC(),
) {
    fun now(): Instant = Instant.now(clock)

    fun initialPublishedAt(startAt: Instant, now: Instant = now()): Instant =
        if (now >= startAt) now else startAt

    fun resolve(
        status: AssignmentStatus,
        startAt: Instant,
        publishedAt: Instant?,
        now: Instant = now(),
    ): EffectiveAssignmentPublication {
        if (status != AssignmentStatus.PUBLISHED || now < startAt) {
            return EffectiveAssignmentPublication(
                status = if (status == AssignmentStatus.PUBLISHED) AssignmentStatus.DRAFT else status,
                publishedAt = null,
            )
        }

        return EffectiveAssignmentPublication(
            status = AssignmentStatus.PUBLISHED,
            publishedAt = publishedAt ?: startAt,
        )
    }
}
