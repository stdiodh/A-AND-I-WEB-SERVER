package com.example.aandi_post_web_server.assignment.infrastructure.adapter

import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRepository
import io.kotest.core.spec.style.StringSpec
import org.mockito.Mockito
import org.springframework.dao.DuplicateKeyException
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class RepositoryAssignmentCopyStoreTest : StringSpec({
    "source assignment query delegates to repository" {
        val fixture = CopyStoreFixture()
        val assignment = Mockito.mock(Assignment::class.java)
        Mockito.`when`(fixture.repository.findById("assignment-1")).thenReturn(Mono.just(assignment))

        StepVerifier.create(fixture.store.findById("assignment-1"))
            .expectNext(assignment)
            .verifyComplete()

        Mockito.verify(fixture.repository).findById("assignment-1")
    }

    "assignment save delegates to repository" {
        val fixture = CopyStoreFixture()
        val assignment = Mockito.mock(Assignment::class.java)
        Mockito.`when`(fixture.repository.save(assignment)).thenReturn(Mono.just(assignment))

        StepVerifier.create(fixture.store.save(assignment))
            .expectNext(assignment)
            .verifyComplete()

        Mockito.verify(fixture.repository).save(assignment)
    }

    "origin assignment query delegates with exact arguments" {
        val fixture = CopyStoreFixture()
        val assignment = Mockito.mock(Assignment::class.java)
        Mockito.`when`(fixture.repository.findByCourseIdAndOriginAssignmentId("course-1", "origin-1"))
            .thenReturn(Mono.just(assignment))

        StepVerifier.create(fixture.store.findByCourseIdAndOriginAssignmentId("course-1", "origin-1"))
            .expectNext(assignment)
            .verifyComplete()

        Mockito.verify(fixture.repository).findByCourseIdAndOriginAssignmentId("course-1", "origin-1")
    }

    "legacy scoped assignment query delegates with exact arguments" {
        val fixture = CopyStoreFixture()
        val assignment = Mockito.mock(Assignment::class.java)
        Mockito.`when`(fixture.repository.findByIdAndCourseId("origin-1", "course-1"))
            .thenReturn(Mono.just(assignment))

        StepVerifier.create(fixture.store.findByIdAndCourseId("origin-1", "course-1"))
            .expectNext(assignment)
            .verifyComplete()

        Mockito.verify(fixture.repository).findByIdAndCourseId("origin-1", "course-1")
    }

    "copy fingerprint query delegates with exact arguments" {
        val fixture = CopyStoreFixture()
        val assignment = Mockito.mock(Assignment::class.java)
        Mockito.`when`(fixture.repository.findByCourseIdAndCopyFingerprint("course-1", "fingerprint-1"))
            .thenReturn(Mono.just(assignment))

        StepVerifier.create(fixture.store.findByCourseIdAndCopyFingerprint("course-1", "fingerprint-1"))
            .expectNext(assignment)
            .verifyComplete()

        Mockito.verify(fixture.repository).findByCourseIdAndCopyFingerprint("course-1", "fingerprint-1")
    }

    "assignment slot query delegates with exact coordinates" {
        val fixture = CopyStoreFixture()
        val assignment = Mockito.mock(Assignment::class.java)
        Mockito.`when`(fixture.repository.findByCourseIdAndWeekNoAndOrderInWeek("course-1", 2, 3))
            .thenReturn(Mono.just(assignment))

        StepVerifier.create(fixture.store.findByCourseIdAndWeekNoAndOrderInWeek("course-1", 2, 3))
            .expectNext(assignment)
            .verifyComplete()

        Mockito.verify(fixture.repository).findByCourseIdAndWeekNoAndOrderInWeek("course-1", 2, 3)
    }

    "duplicate key save error is propagated unchanged" {
        val fixture = CopyStoreFixture()
        val assignment = Mockito.mock(Assignment::class.java)
        val failure = DuplicateKeyException("duplicate copy")
        Mockito.`when`(fixture.repository.save(assignment)).thenReturn(Mono.error(failure))

        StepVerifier.create(fixture.store.save(assignment))
            .expectErrorMatches { error -> error === failure }
            .verify()
    }
})

private class CopyStoreFixture {
    val repository: AssignmentRepository = Mockito.mock(AssignmentRepository::class.java)
    val store = RepositoryAssignmentCopyStore(repository)
}
