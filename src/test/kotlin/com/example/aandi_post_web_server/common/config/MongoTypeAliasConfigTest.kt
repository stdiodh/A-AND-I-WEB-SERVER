package com.example.aandi_post_web_server.common.config

import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.entity.AssignmentDelivery
import com.example.aandi_post_web_server.assignment.entity.AssignmentMetadata
import com.example.aandi_post_web_server.assignment.entity.AssignmentRequirement
import com.example.aandi_post_web_server.assignment.entity.AssignmentTestCase
import com.example.aandi_post_web_server.assignment.submission.entity.AssignmentSubmissionStatusProjection
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTemplateLanguage
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseVisibility
import com.example.aandi_post_web_server.course.domain.model.CourseStatus
import com.example.aandi_post_web_server.course.domain.model.CourseTrack
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseEnrollment
import com.example.aandi_post_web_server.course.entity.CourseMetadata
import com.example.aandi_post_web_server.course.entity.CourseWeek
import com.example.aandi_post_web_server.user.entity.ReportUser
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.bson.Document
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import java.time.Instant
import java.time.LocalDate

class MongoTypeAliasConfigTest : StringSpec({
    val converter = createMongoConverter()
    val fixedInstant = Instant.parse("2026-04-20T12:00:00Z")

    "assignment documents write stable type alias" {
        val document = writeDocument(
            converter,
            Assignment(
                id = "assignment-1",
                courseId = "course-1",
                courseSlug = "back-basic",
                createdBy = "hood",
                weekNo = 1,
                orderInWeek = 1,
                startAt = fixedInstant,
                endAt = fixedInstant.plusSeconds(3600),
                metadata = AssignmentMetadata(
                    title = "터미널 계산기",
                    difficulty = AssignmentDifficulty.MID,
                    description = "# 문제 설명",
                    timeLimitMinutes = 30,
                    codeTemplates = listOf(),
                    learningGoals = listOf("기본 입출력"),
                ),
                status = AssignmentStatus.PUBLISHED,
                createdAt = fixedInstant,
                updatedAt = fixedInstant,
                publishedAt = fixedInstant,
            ),
        )

        document["_class"] shouldBe "assignment"
    }

    "other mongo documents write stable type aliases" {
        writeDocument(
            converter,
            AssignmentDelivery(
                id = "delivery-1",
                assignmentId = "assignment-1",
                userId = "user-1",
                deliveredAt = fixedInstant,
                createdAt = fixedInstant,
            ),
        )["_class"] shouldBe "assignmentDelivery"

        writeDocument(
            converter,
            AssignmentRequirement(
                id = "requirement-1",
                assignmentId = "assignment-1",
                sortOrder = 1,
                requirementText = "정수를 입력받아 합을 출력한다.",
                createdAt = fixedInstant,
            ),
        )["_class"] shouldBe "assignmentRequirement"

        writeDocument(
            converter,
            AssignmentTestCase(
                id = "test-case-1",
                assignmentId = "assignment-1",
                seq = 1,
                inputValues = listOf("1", "2"),
                outputText = "3",
                visibility = AssignmentTestCaseVisibility.PUBLIC,
                createdAt = fixedInstant,
            ),
        )["_class"] shouldBe "assignmentTestCase"

        writeDocument(
            converter,
            AssignmentSubmissionStatusProjection(
                id = "projection-1",
                assignmentId = "assignment-1",
                publicCode = "A001",
                firstCompletedAt = fixedInstant,
                lastCompletedAt = fixedInstant,
                latestScore = 100,
                latestPassedCases = 10,
                latestTotalCases = 10,
                lastEventTimestamp = fixedInstant,
                createdAt = fixedInstant,
                updatedAt = fixedInstant,
                version = 1L,
            ),
        )["_class"] shouldBe "assignmentSubmissionStatusProjection"

        writeDocument(
            converter,
            Course(
                id = "course-1",
                slug = "back-basic",
                fieldTag = CourseTrack.SP,
                startDate = LocalDate.parse("2026-03-02"),
                endDate = LocalDate.parse("2026-06-29"),
                metadata = CourseMetadata(title = "백엔드 기초"),
                status = CourseStatus.PUBLISHED,
                createdAt = fixedInstant,
                updatedAt = fixedInstant,
            ),
        )["_class"] shouldBe "course"

        writeDocument(
            converter,
            CourseEnrollment(
                id = "enrollment-1",
                courseId = "course-1",
                userId = "user-1",
                publicCode = "A001",
                username = "hood",
                joinedAt = fixedInstant,
                updatedAt = fixedInstant,
            ),
        )["_class"] shouldBe "courseEnrollment"

        writeDocument(
            converter,
            CourseWeek(
                id = "week-1",
                courseId = "course-1",
                weekNo = 1,
                title = "1주차",
                startDate = LocalDate.parse("2026-03-02"),
                endDate = LocalDate.parse("2026-03-08"),
                createdAt = fixedInstant,
                updatedAt = fixedInstant,
            ),
        )["_class"] shouldBe "courseWeek"

        writeDocument(
            converter,
            ReportUser(
                id = "user-1",
                publicCode = "A001",
                username = "hood",
                role = "ADMIN",
                nickname = "hood",
                syncedAt = fixedInstant,
                updatedAt = fixedInstant,
            ),
        )["_class"] shouldBe "reportUser"
    }
})

private fun createMongoConverter(): MappingMongoConverter {
    val customConversions = MongoCustomConversions(listOf<Any>())
    val mappingContext = MongoMappingContext()
    mappingContext.setSimpleTypeHolder(customConversions.simpleTypeHolder)
    mappingContext.afterPropertiesSet()

    return MappingMongoConverter(NoOpDbRefResolver.INSTANCE, mappingContext).apply {
        setCustomConversions(customConversions)
        afterPropertiesSet()
    }
}

private fun writeDocument(converter: MappingMongoConverter, source: Any): Document {
    return Document().also { converter.write(source, it) }
}
