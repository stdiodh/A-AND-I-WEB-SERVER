package com.example.aandi_post_web_server.assignment.application.activation

import com.example.aandi_post_web_server.assignment.domain.model.AssignmentActivation
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentActivationRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import reactor.core.publisher.Mono
import java.time.Instant

class AssignmentActivationServiceTest : StringSpec({

    "no document in repository returns default active=true" {
        val repository = Mockito.mock(AssignmentActivationRepository::class.java)
        Mockito.`when`(repository.findById(AssignmentActivation.GLOBAL_ID)).thenReturn(Mono.empty())
        val service = AssignmentActivationService(repository)

        val active = service.isActive().block()
        active!!.shouldBeTrue()

        val response = service.getActivation().block()!!
        response.active.shouldBeTrue()
        response.updatedAt shouldBe Instant.EPOCH
        response.updatedBy shouldBe null
    }

    "stored deactivated document is returned" {
        val repository = Mockito.mock(AssignmentActivationRepository::class.java)
        val stored = AssignmentActivation(
            id = AssignmentActivation.GLOBAL_ID,
            active = false,
            updatedAt = Instant.parse("2026-06-10T01:00:00Z"),
            updatedBy = "admin-1",
        )
        Mockito.`when`(repository.findById(AssignmentActivation.GLOBAL_ID)).thenReturn(Mono.just(stored))
        val service = AssignmentActivationService(repository)

        service.isActive().block()!!.shouldBeFalse()
        val response = service.getActivation().block()!!
        response.active.shouldBeFalse()
        response.updatedBy shouldBe "admin-1"
    }

    "setActivation saves with caller and updates cache" {
        val repository = Mockito.mock(AssignmentActivationRepository::class.java)
        val captor = ArgumentCaptor.forClass(AssignmentActivation::class.java)
        Mockito.`when`(repository.save(Mockito.any(AssignmentActivation::class.java)))
            .thenAnswer { Mono.just(it.arguments[0] as AssignmentActivation) }
        val service = AssignmentActivationService(repository)

        val response = service.setActivation(active = false, updatedBy = "admin-9").block()!!
        response.active.shouldBeFalse()
        response.updatedBy shouldBe "admin-9"

        Mockito.verify(repository).save(captor.capture())
        captor.value.active.shouldBeFalse()
        captor.value.id shouldBe AssignmentActivation.GLOBAL_ID
        captor.value.updatedBy shouldBe "admin-9"

        Mockito.`when`(repository.findById(AssignmentActivation.GLOBAL_ID))
            .thenReturn(Mono.error(IllegalStateException("should not hit repository because of cache")))
        service.isActive().block()!!.shouldBeFalse()
    }
})
