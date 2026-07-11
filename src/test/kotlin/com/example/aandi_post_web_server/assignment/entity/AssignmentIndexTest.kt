package com.example.aandi_post_web_server.assignment.entity

import com.example.aandi_post_web_server.assignment.submission.entity.AssignmentSubmissionStatusProjection
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseEnrollment
import com.example.aandi_post_web_server.course.entity.CourseWeek
import com.example.aandi_post_web_server.user.entity.ReportUser
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.core.io.ClassPathResource
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.IndexDirection
import org.springframework.data.mongodb.core.index.Indexed

class AssignmentIndexTest : StringSpec({
    "Assignment 는 복사 중복 방지용 partial unique index 를 선언한다" {
        val indexes = Assignment::class.java
            .getAnnotation(CompoundIndexes::class.java)
            .value
            .associateBy { it.name }

        indexes.keys shouldContain "ux_assignment_course_week_order"
        indexes.keys shouldContain "ux_assignment_course_origin"
        indexes.keys shouldContain "ux_assignment_course_copy_fingerprint"

        indexes.getValue("ux_assignment_course_week_order").unique shouldBe true
        indexes.getValue("ux_assignment_course_origin").unique shouldBe true
        indexes.getValue("ux_assignment_course_origin").def shouldBe "{'courseId': 1, 'originAssignmentId': 1}"
        indexes.getValue("ux_assignment_course_origin").partialFilter shouldBe
            "{'originAssignmentId': {'\$type': 'string'}}"

        indexes.getValue("ux_assignment_course_copy_fingerprint").unique shouldBe true
        indexes.getValue("ux_assignment_course_copy_fingerprint").def shouldBe
            "{'courseId': 1, 'copyFingerprint': 1}"
        indexes.getValue("ux_assignment_course_copy_fingerprint").partialFilter shouldBe
            "{'copyFingerprint': {'\$type': 'string'}}"
    }

    "자동 인덱스 생성은 명시적으로 비활성화한다" {
        val properties = YamlPropertiesFactoryBean().apply {
            setResources(ClassPathResource("application.yml"))
        }.getObject()

        properties?.getProperty("spring.data.mongodb.auto-index-creation") shouldBe "false"
    }

    "단일 필드 unique index 는 안정된 이름을 선언한다" {
        val courseSlug = Course::class.java.getDeclaredField("slug").getAnnotation(Indexed::class.java)
        val courseCreatedAt = Course::class.java.getDeclaredField("createdAt").getAnnotation(Indexed::class.java)
        val userPublicCode = ReportUser::class.java.getDeclaredField("publicCode").getAnnotation(Indexed::class.java)

        courseSlug.name shouldBe "ux_course_slug"
        courseSlug.unique shouldBe true
        courseCreatedAt.name shouldBe "ix_course_created_at_desc"
        courseCreatedAt.direction shouldBe IndexDirection.DESCENDING
        userPublicCode.name shouldBe "ux_user_public_code"
        userPublicCode.unique shouldBe true
    }

    "수강 조회용 user status index 를 선언한다" {
        val indexes = CourseEnrollment::class.java
            .getAnnotation(CompoundIndexes::class.java)
            .value
            .associateBy { it.name }

        indexes.getValue("ux_course_enrollment").unique shouldBe true
        indexes.getValue("ux_course_enrollment").def shouldBe "{'courseId': 1, 'userId': 1}"
        indexes.getValue("ix_course_enrollment_user_status").unique shouldBe false
        indexes.getValue("ix_course_enrollment_user_status").def shouldBe "{'userId': 1, 'status': 1}"
    }

    "제출 상태는 복합 unique index 만 사용한다" {
        val compoundIndex = AssignmentSubmissionStatusProjection::class.java
            .getAnnotation(CompoundIndex::class.java)

        compoundIndex.name shouldBe "ux_assignment_submission_status_assignment_public_code"
        compoundIndex.def shouldBe "{'assignmentId': 1, 'publicCode': 1}"
        compoundIndex.unique shouldBe true
        AssignmentSubmissionStatusProjection::class.java
            .getDeclaredField("assignmentId")
            .getAnnotation(Indexed::class.java) shouldBe null
        AssignmentSubmissionStatusProjection::class.java
            .getDeclaredField("publicCode")
            .getAnnotation(Indexed::class.java) shouldBe null
    }

    "나머지 복합 unique index 계약을 유지한다" {
        val expected = listOf(
            Triple(CourseWeek::class.java, "ux_course_week", "{'courseId': 1, 'weekNo': 1}"),
            Triple(AssignmentRequirement::class.java, "ux_assignment_requirement_sort", "{'assignmentId': 1, 'sortOrder': 1}"),
            Triple(AssignmentTestCase::class.java, "ux_assignment_test_case_seq", "{'assignmentId': 1, 'seq': 1}"),
            Triple(AssignmentDelivery::class.java, "ux_assignment_delivery_user", "{'assignmentId': 1, 'userId': 1}"),
        )

        expected.forEach { (entity, name, definition) ->
            val index = entity.getAnnotation(CompoundIndex::class.java)
            index.name shouldBe name
            index.def shouldBe definition
            index.unique shouldBe true
        }
    }
})
