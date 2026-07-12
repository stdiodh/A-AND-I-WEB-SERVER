package com.example.aandi_post_web_server.course.infrastructure.adapter

import com.example.aandi_post_web_server.course.domain.model.CoursePhase
import com.example.aandi_post_web_server.course.domain.model.CourseTrack
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseMetadata
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseRepository
import io.kotest.core.spec.style.StringSpec
import org.mockito.Mockito
import org.springframework.data.domain.Sort
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.LocalDate

class RepositoryCourseStoreTest : StringSpec({
    "newest course query delegates with createdAt descending sort" {
        val repository = Mockito.mock(CourseRepository::class.java)
        val sort = Sort.by(Sort.Direction.DESC, "createdAt")
        val course = course()
        Mockito.`when`(repository.findAll(sort)).thenReturn(Flux.just(course))
        val store = RepositoryCourseStore(repository)

        StepVerifier.create(store.findAllNewestFirst())
            .expectNext(course)
            .verifyComplete()

        Mockito.verify(repository).findAll(sort)
    }

    "course id collection query delegates to findAllById" {
        val repository = Mockito.mock(CourseRepository::class.java)
        val courseIds = listOf("course-1", "course-2")
        val course = course()
        Mockito.`when`(repository.findAllById(courseIds)).thenReturn(Flux.just(course))
        val store = RepositoryCourseStore(repository)

        StepVerifier.create(store.findAllByIds(courseIds))
            .expectNext(course)
            .verifyComplete()

        Mockito.verify(repository).findAllById(courseIds)
    }
})

private fun course(): Course =
    Course(
        id = "course-1",
        slug = "backend-basic",
        fieldTag = CourseTrack.SP,
        startDate = LocalDate.of(2026, 3, 1),
        endDate = LocalDate.of(2026, 3, 30),
        metadata = CourseMetadata(
            title = "Backend Basic",
            phase = CoursePhase.BASIC,
        ),
    )
