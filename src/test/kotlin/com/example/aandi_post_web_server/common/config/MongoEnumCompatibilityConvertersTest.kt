package com.example.aandi_post_web_server.common.config

import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseVisibility
import com.example.aandi_post_web_server.assignment.entity.AssignmentTestCase
import com.example.aandi_post_web_server.course.domain.model.CourseStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import org.bson.Document
import org.bson.types.ObjectId
import java.time.Instant

class MongoEnumCompatibilityConvertersTest : StringSpec({
    "코스 상태 변환기는 대소문자와 공백을 정규화하고 legacy 값을 DRAFT로 읽는다" {
        val converter = StringToCourseStatusReadingConverter()

        forAll(
            row("DRAFT", CourseStatus.DRAFT),
            row(" published ", CourseStatus.PUBLISHED),
            row("archived", CourseStatus.DRAFT),
            row("UNKNOWN_STATUS", CourseStatus.DRAFT),
        ) { raw, expected ->
            converter.convert(raw) shouldBe expected
        }
    }

    "과제 상태 변환기는 대소문자와 공백을 정규화하고 legacy 값을 DRAFT로 읽는다" {
        val converter = StringToAssignmentStatusReadingConverter()

        forAll(
            row("draft", AssignmentStatus.DRAFT),
            row("PUBLISHED ", AssignmentStatus.PUBLISHED),
            row("ARCHIVED", AssignmentStatus.DRAFT),
            row("", AssignmentStatus.DRAFT),
        ) { raw, expected ->
            converter.convert(raw) shouldBe expected
        }
    }

    "inputValues가 단일 문자열이면 한 개 값 목록으로 읽는다" {
        val testCase = convertTestCase("inputValues" to "single")

        testCase.inputValues shouldContainExactly listOf("single")
    }

    "inputValues가 빈 문자열이면 빈 목록으로 읽는다" {
        val testCase = convertTestCase("inputValues" to "")

        testCase.inputValues.shouldBeEmpty()
    }

    "inputValues 배열의 null 원소는 제외하고 문자열로 변환한다" {
        val testCase = convertTestCase("inputValues" to listOf("a", null, 3))

        testCase.inputValues shouldContainExactly listOf("a", "3")
    }

    "legacy inputText의 CRLF 줄바꿈은 LF 기준으로 분리한다" {
        val testCase = convertTestCase("inputText" to "1\r\n2\r\n3")

        testCase.inputValues shouldContainExactly listOf("1", "2", "3")
    }

    "legacy inputText가 비어 있거나 입력 필드가 없으면 빈 목록으로 읽는다" {
        convertTestCase("inputText" to "").inputValues.shouldBeEmpty()
        convertTestCase().inputValues.shouldBeEmpty()
    }

    "visibility가 없으면 PUBLIC, 값이 있으면 공백과 대소문자를 정규화한다" {
        convertTestCase().visibility shouldBe AssignmentTestCaseVisibility.PUBLIC
        convertTestCase("visibility" to " excluded ").visibility shouldBe AssignmentTestCaseVisibility.EXCLUDED
    }

    "createdAt은 Instant를 그대로 쓰고 없으면 변환 시각으로 채운다" {
        val createdAt = Instant.parse("2026-03-24T02:00:42.292Z")
        convertTestCase("createdAt" to createdAt).createdAt shouldBe createdAt

        val before = Instant.now()
        val fallback = convertTestCase().createdAt
        val after = Instant.now()
        (!fallback.isBefore(before) && !fallback.isAfter(after)) shouldBe true
    }

    "ObjectId _id와 Long seq, description을 엔티티 필드로 옮긴다" {
        val objectId = ObjectId("69c1f04a76aab82913aa2c13")

        val testCase = convertTestCase(
            "_id" to objectId,
            "seq" to 7L,
            "description" to "경계값",
        )

        testCase.id shouldBe "69c1f04a76aab82913aa2c13"
        testCase.seq shouldBe 7
        testCase.description shouldBe "경계값"
    }

    "_id가 없는 문서는 id를 null로 읽는다" {
        val source = Document(
            mapOf(
                "assignmentId" to "assignment-1",
                "seq" to 1,
                "outputText" to "ok",
            ),
        )

        DocumentToAssignmentTestCaseReadingConverter().convert(source).id.shouldBeNull()
    }

    "mongoCustomConversions는 세 호환 변환기를 등록한다" {
        val conversions = MongoEnumCompatibilityConfig().mongoCustomConversions()

        conversions.hasCustomReadTarget(String::class.java, CourseStatus::class.java) shouldBe true
        conversions.hasCustomReadTarget(String::class.java, AssignmentStatus::class.java) shouldBe true
        conversions.hasCustomReadTarget(Document::class.java, AssignmentTestCase::class.java) shouldBe true
    }
})

private fun convertTestCase(vararg overrides: Pair<String, Any?>): AssignmentTestCase {
    val fields = linkedMapOf<String, Any?>(
        "_id" to "case-1",
        "assignmentId" to "49247af2-5328-486e-b608-e2919c941868",
        "seq" to 1,
        "outputText" to "6",
    )
    overrides.forEach { (key, value) -> fields[key] = value }
    return DocumentToAssignmentTestCaseReadingConverter().convert(Document(fields))
}
