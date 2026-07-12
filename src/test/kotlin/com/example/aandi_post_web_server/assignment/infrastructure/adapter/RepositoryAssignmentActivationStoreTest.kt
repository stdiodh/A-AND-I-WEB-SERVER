package com.example.aandi_post_web_server.assignment.infrastructure.adapter

import com.example.aandi_post_web_server.assignment.domain.model.AssignmentActivation
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentActivationRepository
import io.kotest.core.spec.style.StringSpec
import org.mockito.Mockito
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant

class RepositoryAssignmentActivationStoreTest : StringSpec({
    "findById delegates to the repository" {
        val repository = Mockito.mock(AssignmentActivationRepository::class.java)
        val activation = activation()
        Mockito.`when`(repository.findById(AssignmentActivation.GLOBAL_ID))
            .thenReturn(Mono.just(activation))
        val store = RepositoryAssignmentActivationStore(repository)

        StepVerifier.create(store.findById(AssignmentActivation.GLOBAL_ID))
            .expectNext(activation)
            .verifyComplete()

        Mockito.verify(repository).findById(AssignmentActivation.GLOBAL_ID)
    }

    "save delegates to the repository" {
        val repository = Mockito.mock(AssignmentActivationRepository::class.java)
        val activation = activation()
        Mockito.`when`(repository.save(activation)).thenReturn(Mono.just(activation))
        val store = RepositoryAssignmentActivationStore(repository)

        StepVerifier.create(store.save(activation))
            .expectNext(activation)
            .verifyComplete()

        Mockito.verify(repository).save(activation)
    }
})

private fun activation(): AssignmentActivation =
    AssignmentActivation(
        id = AssignmentActivation.GLOBAL_ID,
        active = false,
        updatedAt = Instant.parse("2026-06-10T01:00:00Z"),
        updatedBy = "admin-1",
    )
