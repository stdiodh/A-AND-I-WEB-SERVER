package com.example.aandi_post_web_server.user.infrastructure.adapter

import com.example.aandi_post_web_server.user.entity.ReportUser
import com.example.aandi_post_web_server.user.infrastructure.repository.ReportUserRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

class AdminAssignmentSubmissionUserQueryAdapterTest : StringSpec({
    "users are projected to id and nullable nickname" {
        val reportUserRepository = Mockito.mock(ReportUserRepository::class.java)
        val adapter = AdminAssignmentSubmissionUserQueryAdapter(reportUserRepository)
        val userIds = listOf("user-1", "user-2")
        Mockito.`when`(reportUserRepository.findAllById(userIds))
            .thenReturn(
                Flux.just(
                    reportUser("user-1", "앨리스"),
                    reportUser("user-2", null),
                )
            )

        StepVerifier.create(adapter.findAllByIds(userIds))
            .assertNext { user ->
                user.id shouldBe "user-1"
                user.nickname shouldBe "앨리스"
            }
            .assertNext { user ->
                user.id shouldBe "user-2"
                user.nickname shouldBe null
            }
            .verifyComplete()
    }
})

private fun reportUser(
    id: String,
    nickname: String?,
): ReportUser =
    ReportUser(
        id = id,
        publicCode = "#$id",
        username = "$id-name",
        role = "USER",
        nickname = nickname,
    )
