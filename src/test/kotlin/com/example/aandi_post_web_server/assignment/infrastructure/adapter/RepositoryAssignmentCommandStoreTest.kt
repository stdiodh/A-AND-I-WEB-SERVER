package com.example.aandi_post_web_server.assignment.infrastructure.adapter

import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRepository
import io.kotest.core.spec.style.StringSpec
import org.mockito.Mockito
import org.springframework.dao.DuplicateKeyException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class RepositoryAssignmentCommandStoreTest : StringSpec({
    "assignment save delegates to repository" {
        val repository = Mockito.mock(AssignmentRepository::class.java)
        val assignment = Mockito.mock(Assignment::class.java)
        Mockito.`when`(repository.save(assignment)).thenReturn(Mono.just(assignment))
        val store = RepositoryAssignmentCommandStore(repository)

        StepVerifier.create(store.save(assignment))
            .expectNext(assignment)
            .verifyComplete()

        Mockito.verify(repository).save(assignment)
    }

    "scoped assignment query preserves empty result" {
        val repository = Mockito.mock(AssignmentRepository::class.java)
        Mockito.`when`(repository.findByIdAndCourseId("assignment-1", "course-1"))
            .thenReturn(Mono.empty())
        val store = RepositoryAssignmentCommandStore(repository)

        StepVerifier.create(store.findByIdAndCourseId("assignment-1", "course-1"))
            .verifyComplete()

        Mockito.verify(repository).findByIdAndCourseId("assignment-1", "course-1")
    }

    "course assignment query delegates to repository" {
        val repository = Mockito.mock(AssignmentRepository::class.java)
        val assignment = Mockito.mock(Assignment::class.java)
        Mockito.`when`(repository.findAllByCourseId("course-1")).thenReturn(Flux.just(assignment))
        val store = RepositoryAssignmentCommandStore(repository)

        StepVerifier.create(store.findAllByCourseId("course-1"))
            .expectNext(assignment)
            .verifyComplete()

        Mockito.verify(repository).findAllByCourseId("course-1")
    }

    "assignment slot query delegates with exact coordinates" {
        val repository = Mockito.mock(AssignmentRepository::class.java)
        val assignment = Mockito.mock(Assignment::class.java)
        Mockito.`when`(repository.findByCourseIdAndWeekNoAndOrderInWeek("course-1", 2, 3))
            .thenReturn(Mono.just(assignment))
        val store = RepositoryAssignmentCommandStore(repository)

        StepVerifier.create(store.findByCourseIdAndWeekNoAndOrderInWeek("course-1", 2, 3))
            .expectNext(assignment)
            .verifyComplete()

        Mockito.verify(repository).findByCourseIdAndWeekNoAndOrderInWeek("course-1", 2, 3)
    }

    "duplicate key save error is propagated unchanged" {
        val repository = Mockito.mock(AssignmentRepository::class.java)
        val assignment = Mockito.mock(Assignment::class.java)
        val failure = DuplicateKeyException("duplicate assignment")
        Mockito.`when`(repository.save(assignment)).thenReturn(Mono.error(failure))
        val store = RepositoryAssignmentCommandStore(repository)

        StepVerifier.create(store.save(assignment))
            .expectErrorMatches { error -> error === failure }
            .verify()
    }
})
