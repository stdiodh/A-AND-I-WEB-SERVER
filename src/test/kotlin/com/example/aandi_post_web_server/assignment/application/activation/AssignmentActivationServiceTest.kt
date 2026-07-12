package com.example.aandi_post_web_server.assignment.application.activation

import com.example.aandi_post_web_server.assignment.application.port.AssignmentActivationStore
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentActivation
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import reactor.core.publisher.Mono
import java.time.Instant

class AssignmentActivationServiceTest : StringSpec({

    "no document in store returns default active=true" {
        val store = RecordingAssignmentActivationStore()
        val service = AssignmentActivationService(store)

        val active = service.isActive().block()
        active!!.shouldBeTrue()

        val response = service.getActivation().block()!!
        response.active.shouldBeTrue()
        response.updatedAt shouldBe Instant.EPOCH
        response.updatedBy shouldBe null
        store.lastFindId shouldBe AssignmentActivation.GLOBAL_ID
    }

    "stored deactivated document is returned" {
        val stored = AssignmentActivation(
            id = AssignmentActivation.GLOBAL_ID,
            active = false,
            updatedAt = Instant.parse("2026-06-10T01:00:00Z"),
            updatedBy = "admin-1",
        )
        val store = RecordingAssignmentActivationStore(current = stored)
        val service = AssignmentActivationService(store)

        service.isActive().block()!!.shouldBeFalse()
        val response = service.getActivation().block()!!
        response.active.shouldBeFalse()
        response.updatedBy shouldBe "admin-1"
    }

    "setActivation saves with caller and updates cache" {
        val store = RecordingAssignmentActivationStore()
        val service = AssignmentActivationService(store)

        val response = service.setActivation(active = false, updatedBy = "admin-9").block()!!
        response.active.shouldBeFalse()
        response.updatedBy shouldBe "admin-9"

        store.saved!!.active.shouldBeFalse()
        store.saved!!.id shouldBe AssignmentActivation.GLOBAL_ID
        store.saved!!.updatedBy shouldBe "admin-9"
        store.saveCalls shouldBe 1

        store.findError = IllegalStateException("should not hit store because of cache")
        service.isActive().block()!!.shouldBeFalse()
        store.findCalls shouldBe 0
    }
})

private class RecordingAssignmentActivationStore(
    private var current: AssignmentActivation? = null,
) : AssignmentActivationStore {
    var saved: AssignmentActivation? = null
        private set
    var saveCalls: Int = 0
        private set
    var findCalls: Int = 0
        private set
    var lastFindId: String? = null
        private set
    var findError: Throwable? = null

    override fun findById(id: String): Mono<AssignmentActivation> {
        findCalls += 1
        lastFindId = id
        findError?.let { return Mono.error(it) }
        return Mono.justOrEmpty(current)
    }

    override fun save(activation: AssignmentActivation): Mono<AssignmentActivation> {
        saveCalls += 1
        saved = activation
        current = activation
        return Mono.just(activation)
    }
}
