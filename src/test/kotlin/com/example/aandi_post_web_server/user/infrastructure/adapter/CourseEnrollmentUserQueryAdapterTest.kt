package com.example.aandi_post_web_server.user.infrastructure.adapter

import com.example.aandi_post_web_server.user.entity.ReportUser
import com.example.aandi_post_web_server.user.infrastructure.repository.ReportUserRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class CourseEnrollmentUserQueryAdapterTest : StringSpec({
    "user fields required for enrollment are projected" {
        val reportUserRepository = Mockito.mock(ReportUserRepository::class.java)
        val adapter = CourseEnrollmentUserQueryAdapter(reportUserRepository)
        Mockito.`when`(reportUserRepository.findByPublicCode("#FL301"))
            .thenReturn(Mono.just(enrollmentReportUser()))

        StepVerifier.create(adapter.findByPublicCode("#FL301"))
            .assertNext { user ->
                user.id shouldBe "user-1"
                user.publicCode shouldBe "#FL301"
                user.username shouldBe "alice"
                user.role shouldBe "ORGANIZER"
            }
            .verifyComplete()
    }

    "missing repository user remains empty" {
        val reportUserRepository = Mockito.mock(ReportUserRepository::class.java)
        val adapter = CourseEnrollmentUserQueryAdapter(reportUserRepository)
        Mockito.`when`(reportUserRepository.findByPublicCode("#FL999"))
            .thenReturn(Mono.empty())

        StepVerifier.create(adapter.findByPublicCode("#FL999"))
            .verifyComplete()
    }
})

private fun enrollmentReportUser(): ReportUser =
    ReportUser(
        id = "user-1",
        publicCode = "#FL301",
        username = "alice",
        role = "ORGANIZER",
    )
