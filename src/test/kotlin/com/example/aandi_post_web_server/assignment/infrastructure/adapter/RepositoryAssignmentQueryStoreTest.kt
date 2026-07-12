package com.example.aandi_post_web_server.assignment.infrastructure.adapter

import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRepository
import io.kotest.core.spec.style.StringSpec
import org.mockito.Mockito
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class RepositoryAssignmentQueryStoreTest : StringSpec({
    "assignment id query delegates to repository" {
        val repository = Mockito.mock(AssignmentRepository::class.java)
        val assignment = Mockito.mock(Assignment::class.java)
        Mockito.`when`(repository.findById("assignment-1")).thenReturn(Mono.just(assignment))
        val store = RepositoryAssignmentQueryStore(repository)

        StepVerifier.create(store.findById("assignment-1"))
            .expectNext(assignment)
            .verifyComplete()

        Mockito.verify(repository).findById("assignment-1")
    }

    "scoped assignment query delegates without fallback" {
        val repository = Mockito.mock(AssignmentRepository::class.java)
        val assignment = Mockito.mock(Assignment::class.java)
        Mockito.`when`(repository.findByIdAndCourseId("assignment-1", "course-1"))
            .thenReturn(Mono.just(assignment))
        val store = RepositoryAssignmentQueryStore(repository)

        StepVerifier.create(store.findByIdAndCourseId("assignment-1", "course-1"))
            .expectNext(assignment)
            .verifyComplete()

        Mockito.verify(repository).findByIdAndCourseId("assignment-1", "course-1")
        Mockito.verify(repository, Mockito.never()).findById(Mockito.anyString())
    }

    "course assignment query delegates to repository" {
        val repository = Mockito.mock(AssignmentRepository::class.java)
        val assignment = Mockito.mock(Assignment::class.java)
        Mockito.`when`(repository.findAllByCourseId("course-1")).thenReturn(Flux.just(assignment))
        val store = RepositoryAssignmentQueryStore(repository)

        StepVerifier.create(store.findAllByCourseId("course-1"))
            .expectNext(assignment)
            .verifyComplete()

        Mockito.verify(repository).findAllByCourseId("course-1")
    }

    "course week assignment query delegates to repository" {
        val repository = Mockito.mock(AssignmentRepository::class.java)
        val assignment = Mockito.mock(Assignment::class.java)
        Mockito.`when`(repository.findAllByCourseIdAndWeekNo("course-1", 2))
            .thenReturn(Flux.just(assignment))
        val store = RepositoryAssignmentQueryStore(repository)

        StepVerifier.create(store.findAllByCourseIdAndWeekNo("course-1", 2))
            .expectNext(assignment)
            .verifyComplete()

        Mockito.verify(repository).findAllByCourseIdAndWeekNo("course-1", 2)
    }

    "repository error is propagated unchanged" {
        val repository = Mockito.mock(AssignmentRepository::class.java)
        val failure = IllegalStateException("query failed")
        Mockito.`when`(repository.findById("assignment-1"))
            .thenReturn(Mono.error(failure))
        val store = RepositoryAssignmentQueryStore(repository)

        StepVerifier.create(store.findById("assignment-1"))
            .expectErrorMatches { error -> error === failure }
            .verify()
    }
})
