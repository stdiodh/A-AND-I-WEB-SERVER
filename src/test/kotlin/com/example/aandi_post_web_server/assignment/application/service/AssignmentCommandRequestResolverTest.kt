package com.example.aandi_post_web_server.assignment.application.service

import com.example.aandi_post_web_server.assignment.api.dto.AssignmentMetadataPayload
import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentRequest
import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentTestCaseRequest
import com.example.aandi_post_web_server.assignment.api.dto.UpdateAssignmentRequest
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseValidator
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseVisibility
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.test.StepVerifier
import java.time.Instant

class AssignmentCommandRequestResolverTest : StringSpec({
    val resolver = AssignmentCommandRequestResolver(AssignmentTestCaseValidator())

    "생성 요청은 기존처럼 구독 전에 검증한다" {
        val request = createRequest(metadata(title = " "))

        val error = shouldThrow<ResponseStatusException> {
            resolver.resolveCreateRequest(request)
        }

        error.statusCode shouldBe HttpStatus.BAD_REQUEST
        error.reason shouldBe "과제 제목이 필요합니다."
    }

    "생성 요청의 테스트케이스 도메인 예외를 BAD_REQUEST로 변환한다" {
        val request = createRequest(
            metadata(
                testCases = listOf(
                    CreateAssignmentTestCaseRequest(
                        seq = 1,
                        inputValues = listOf("input"),
                        outputText = "output",
                        visibility = AssignmentTestCaseVisibility.EXCLUDED,
                    )
                )
            )
        )

        val error = shouldThrow<ResponseStatusException> {
            resolver.resolveCreateRequest(request)
        }

        error.statusCode shouldBe HttpStatus.BAD_REQUEST
        error.reason shouldBe "testCases must contain at least one gradable case"
    }

    "PATCH에서 testCases를 생략하면 기존 값을 유지하도록 해석한다" {
        val metadata = metadata(testCases = emptyList())
        val request = UpdateAssignmentRequest(metadata = metadata)

        resolver.shouldReplaceTestCases(metadata) shouldBe false
        StepVerifier.create(resolver.resolveUpdateRequest(request, replaceTestCases = false))
            .expectNext(request)
            .verifyComplete()
    }

    "PATCH에서 제공된 testCases는 검증한다" {
        val metadata = metadata(
            testCases = listOf(
                CreateAssignmentTestCaseRequest(
                    seq = 1,
                    inputValues = listOf("input"),
                    outputText = "output",
                    visibility = AssignmentTestCaseVisibility.EXCLUDED,
                )
            )
        )

        resolver.shouldReplaceTestCases(metadata) shouldBe true
        val error = shouldThrow<ResponseStatusException> {
            resolver.resolveUpdateRequest(UpdateAssignmentRequest(metadata = metadata), replaceTestCases = true)
        }

        error.statusCode shouldBe HttpStatus.BAD_REQUEST
        error.reason shouldBe "testCases must contain at least one gradable case"
    }
})

private fun createRequest(metadata: AssignmentMetadataPayload): CreateAssignmentRequest =
    CreateAssignmentRequest(
        weekNo = 1,
        orderInWeek = 1,
        startAt = Instant.parse("2026-03-01T00:00:00Z"),
        endAt = Instant.parse("2026-03-02T00:00:00Z"),
        metadata = metadata,
    )

private fun metadata(
    title: String = "title",
    testCases: List<CreateAssignmentTestCaseRequest> = listOf(
        CreateAssignmentTestCaseRequest(
            seq = 1,
            inputValues = listOf("input"),
            outputText = "output",
        )
    ),
): AssignmentMetadataPayload =
    AssignmentMetadataPayload(
        title = title,
        difficulty = AssignmentDifficulty.LOW,
        description = "description",
        testCases = testCases,
    )
