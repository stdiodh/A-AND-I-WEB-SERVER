package com.example.aandi_post_web_server.common.config

import com.example.aandi_post_web_server.assignment.entity.AssignmentTestCase
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import com.example.aandi_post_web_server.assignment.enum.AssignmentTestCaseVisibility
import com.example.aandi_post_web_server.course.enum.CourseStatus
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import java.time.Instant
import java.util.Date

@Configuration
class MongoEnumCompatibilityConfig {

    @Bean
    fun mongoCustomConversions(): MongoCustomConversions =
        MongoCustomConversions(
            listOf(
                StringToCourseStatusReadingConverter(),
                StringToAssignmentStatusReadingConverter(),
                DocumentToAssignmentTestCaseReadingConverter(),
            ),
        )
}

@ReadingConverter
class StringToCourseStatusReadingConverter : Converter<String, CourseStatus> {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun convert(source: String): CourseStatus {
        val normalized = source.trim().uppercase()
        return when (normalized) {
            "DRAFT" -> CourseStatus.DRAFT
            "PUBLISHED" -> CourseStatus.PUBLISHED
            // Legacy compatibility: old status value
            "ARCHIVED" -> CourseStatus.DRAFT
            else -> {
                log.warn("Unknown legacy CourseStatus '{}'. Fallback to DRAFT.", source)
                CourseStatus.DRAFT
            }
        }
    }
}

@ReadingConverter
class StringToAssignmentStatusReadingConverter : Converter<String, AssignmentStatus> {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun convert(source: String): AssignmentStatus {
        val normalized = source.trim().uppercase()
        return when (normalized) {
            "DRAFT" -> AssignmentStatus.DRAFT
            "PUBLISHED" -> AssignmentStatus.PUBLISHED
            // Legacy compatibility: old status value
            "ARCHIVED" -> AssignmentStatus.DRAFT
            else -> {
                log.warn("Unknown legacy AssignmentStatus '{}'. Fallback to DRAFT.", source)
                AssignmentStatus.DRAFT
            }
        }
    }
}

@ReadingConverter
class DocumentToAssignmentTestCaseReadingConverter : Converter<Document, AssignmentTestCase> {

    override fun convert(source: Document): AssignmentTestCase =
        AssignmentTestCase(
            id = source["_id"]?.toString(),
            assignmentId = source.getString("assignmentId"),
            seq = (source["seq"] as Number).toInt(),
            inputValues = readInputValues(source),
            outputText = source.getString("outputText"),
            visibility = readVisibility(source.getString("visibility")),
            description = source.getString("description"),
            createdAt = readCreatedAt(source["createdAt"]),
        )

    private fun readInputValues(source: Document): List<String> {
        val rawInputValues = source["inputValues"]
        if (rawInputValues is List<*>) {
            return rawInputValues.mapNotNull { it?.toString() }
        }
        if (rawInputValues is String) {
            return if (rawInputValues.isEmpty()) emptyList() else listOf(rawInputValues)
        }

        val legacyInputText = source.getString("inputText") ?: return emptyList()
        if (legacyInputText.isEmpty()) {
            return emptyList()
        }
        return legacyInputText.replace("\r\n", "\n").split("\n")
    }

    private fun readVisibility(source: String?): AssignmentTestCaseVisibility {
        if (source == null) {
            return AssignmentTestCaseVisibility.PUBLIC
        }
        return AssignmentTestCaseVisibility.valueOf(source.trim().uppercase())
    }

    private fun readCreatedAt(source: Any?): Instant {
        if (source is Instant) {
            return source
        }
        if (source is Date) {
            return source.toInstant()
        }
        return Instant.now()
    }
}
