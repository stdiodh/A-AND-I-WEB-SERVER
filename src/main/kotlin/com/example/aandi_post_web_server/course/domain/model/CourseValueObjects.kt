package com.example.aandi_post_web_server.course.domain.model

import com.example.aandi_post_web_server.course.domain.model.CourseTrack
import java.util.UUID

@JvmInline
value class CourseSlug private constructor(val value: String) {
    companion object {
        fun from(raw: String): CourseSlug {
            val normalized = raw.trim().lowercase()
            require(normalized.isNotBlank()) { "courseSlug는 비어 있을 수 없습니다." }
            return CourseSlug(normalized)
        }
    }
}

@JvmInline
value class CourseId private constructor(val value: String) {
    companion object {
        fun from(raw: String): CourseId {
            val normalized = raw.trim()
            require(normalized.isNotBlank()) { "courseId는 비어 있을 수 없습니다." }
            return CourseId(normalized)
        }
    }
}

@JvmInline
value class UserId private constructor(val value: String) {
    companion object {
        fun from(raw: String): UserId {
            val normalized = raw.trim()
            require(normalized.isNotBlank()) { "userId는 비어 있을 수 없습니다." }
            return UserId(normalized)
        }
    }
}

@JvmInline
value class PublicCode private constructor(val value: String) {
    val legacyValue: String
        get() = value.removePrefix("#")

    val track: CourseTrack?
        get() = CourseTrack.entries.firstOrNull { it.name == value.removePrefix("#").take(2) }

    companion object {
        private val PATTERN = Regex("^#[A-Z]{2}\\d{3}$")

        fun from(raw: String): PublicCode {
            val trimmed = raw.trim().uppercase()
            require(trimmed.isNotBlank()) { "publicCode는 비어 있을 수 없습니다." }
            val normalized = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
            require(PATTERN.matches(normalized)) { "publicCode 형식이 올바르지 않습니다. 예: #FL301" }
            return PublicCode(normalized)
        }
    }
}

@JvmInline
value class WeekNo private constructor(val value: Int) {
    companion object {
        fun from(raw: Int): WeekNo {
            require(raw > 0) { "weekNo는 1 이상이어야 합니다." }
            return WeekNo(raw)
        }
    }
}

@JvmInline
value class AssignmentId private constructor(val value: String) {
    companion object {
        fun from(raw: String): AssignmentId {
            val normalized = raw.trim()
            require(normalized.isNotBlank()) { "assignmentId는 비어 있을 수 없습니다." }
            require(runCatching { UUID.fromString(normalized) }.isSuccess) { "assignmentId는 UUID 형식이어야 합니다." }
            return AssignmentId(normalized)
        }
    }
}
