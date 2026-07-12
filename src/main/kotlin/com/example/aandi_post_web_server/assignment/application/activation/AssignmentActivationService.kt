package com.example.aandi_post_web_server.assignment.application.activation

import com.example.aandi_post_web_server.assignment.api.v2.dto.AssignmentActivationResponse
import com.example.aandi_post_web_server.assignment.application.port.AssignmentActivationStore
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentActivation
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

@Service
class AssignmentActivationService(
    private val store: AssignmentActivationStore,
) {

    private val log = LoggerFactory.getLogger(AssignmentActivationService::class.java)
    private val cache = AtomicReference<CachedActivation?>(null)

    fun isActive(): Mono<Boolean> = loadCurrent().map { it.active }

    fun getActivation(): Mono<AssignmentActivationResponse> = loadCurrent().map(::toResponse)

    fun setActivation(active: Boolean, updatedBy: String): Mono<AssignmentActivationResponse> {
        val updated = AssignmentActivation(
            id = AssignmentActivation.GLOBAL_ID,
            active = active,
            updatedAt = Instant.now(),
            updatedBy = updatedBy,
        )
        return store.save(updated)
            .doOnNext { saved ->
                cache.set(CachedActivation(saved, Instant.now()))
                log.info(
                    "assignment activation toggled active={} updatedBy={} updatedAt={}",
                    saved.active,
                    saved.updatedBy,
                    saved.updatedAt,
                )
            }
            .map(::toResponse)
    }

    private fun loadCurrent(): Mono<AssignmentActivation> {
        val cached = cache.get()
        if (cached != null && !cached.isExpired()) {
            return Mono.just(cached.value)
        }
        return store.findById(AssignmentActivation.GLOBAL_ID)
            .defaultIfEmpty(AssignmentActivation.defaultActive())
            .doOnNext { value -> cache.set(CachedActivation(value, Instant.now())) }
    }

    private fun toResponse(value: AssignmentActivation): AssignmentActivationResponse =
        AssignmentActivationResponse(
            active = value.active,
            updatedAt = value.updatedAt,
            updatedBy = value.updatedBy,
        )

    private data class CachedActivation(val value: AssignmentActivation, val cachedAt: Instant) {
        fun isExpired(now: Instant = Instant.now()): Boolean =
            Duration.between(cachedAt, now) >= CACHE_TTL
    }

    companion object {
        private val CACHE_TTL: Duration = Duration.ofSeconds(30)
    }
}
