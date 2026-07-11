package com.example.aandi_post_web_server.course.entity

import com.example.aandi_post_web_server.course.domain.model.EnrollmentStatus
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.TypeAlias
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "course_enrollments")
@TypeAlias("courseEnrollment")
@CompoundIndexes(
    CompoundIndex(name = "ux_course_enrollment", def = "{'courseId': 1, 'userId': 1}", unique = true),
    CompoundIndex(name = "ix_course_enrollment_user_status", def = "{'userId': 1, 'status': 1}"),
)
data class CourseEnrollment(
    @Id
    val id: String? = null,
    val courseId: String,
    val userId: String,
    val publicCode: String = "",
    val username: String = "",
    val status: EnrollmentStatus = EnrollmentStatus.ENABLED,
    val joinedAt: Instant = Instant.now(),
    val bannedAt: Instant? = null,
    val banReason: String? = null,
    val updatedAt: Instant = Instant.now(),
)
