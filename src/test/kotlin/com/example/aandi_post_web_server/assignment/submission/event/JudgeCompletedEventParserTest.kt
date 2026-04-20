package com.example.aandi_post_web_server.assignment.infrastructure.submission.event

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class JudgeCompletedEventParserTest : StringSpec({
    val parser = JudgeCompletedEventParser(
        jacksonObjectMapper().registerModule(JavaTimeModule()),
    )

    "raw judge completed message 를 파싱한다" {
        val result = parser.parse(
            """
            {
              "eventType": "JUDGE_COMPLETED",
              "publicCode": "A00123",
              "problemId": "quiz-101",
              "score": 80,
              "passedCases": 8,
              "totalCases": 10,
              "timestamp": "2026-04-09T02:15:30.123Z"
            }
            """.trimIndent()
        )

        result shouldBe JudgeCompletedEventParseResult.Parsed(
            JudgeCompletedEvent(
                assignmentId = "quiz-101",
                publicCode = "A00123",
                score = 80,
                passedCases = 8,
                totalCases = 10,
                timestamp = Instant.parse("2026-04-09T02:15:30.123Z"),
            )
        )
    }

    "sns envelope 안의 Message 값을 파싱한다" {
        val result = parser.parse(
            """
            {
              "Type": "Notification",
              "MessageId": "52f8a9dd-19e1-4c13-a5b5-77d59c35d001",
              "TopicArn": "arn:aws:sns:ap-northeast-2:000000000000:judge-submission-events.fifo",
              "Message": "{\"eventType\":\"JUDGE_COMPLETED\",\"publicCode\":\"A00123\",\"problemId\":\"quiz-101\",\"score\":100,\"passedCases\":10,\"totalCases\":10,\"timestamp\":\"2026-04-09T02:15:30.123Z\"}"
            }
            """.trimIndent()
        )

        result shouldBe JudgeCompletedEventParseResult.Parsed(
            JudgeCompletedEvent(
                assignmentId = "quiz-101",
                publicCode = "A00123",
                score = 100,
                passedCases = 10,
                totalCases = 10,
                timestamp = Instant.parse("2026-04-09T02:15:30.123Z"),
            )
        )
    }

    "judge completed 가 아닌 eventType 은 안전하게 무시한다" {
        val result = parser.parse(
            """
            {
              "eventType": "JUDGE_RUNNING",
              "publicCode": "A00123",
              "problemId": "quiz-101",
              "score": 10,
              "passedCases": 1,
              "totalCases": 10,
              "timestamp": "2026-04-09T02:15:30.123Z"
            }
            """.trimIndent()
        )

        result shouldBe JudgeCompletedEventParseResult.Ignored("unsupported_event_type:JUDGE_RUNNING")
    }
})
