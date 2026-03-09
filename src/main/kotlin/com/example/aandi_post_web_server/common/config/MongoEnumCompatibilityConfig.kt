package com.example.aandi_post_web_server.common.config

import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import com.example.aandi_post_web_server.course.enum.CourseStatus
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.mongodb.core.convert.MongoCustomConversions

@Configuration
class MongoEnumCompatibilityConfig {

    @Bean
    fun mongoCustomConversions(): MongoCustomConversions =
        MongoCustomConversions(
            listOf(
                StringToCourseStatusReadingConverter(),
                StringToAssignmentStatusReadingConverter(),
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

