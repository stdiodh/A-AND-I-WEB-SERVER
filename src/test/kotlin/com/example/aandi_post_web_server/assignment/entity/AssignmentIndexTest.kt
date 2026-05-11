package com.example.aandi_post_web_server.assignment.entity

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.springframework.data.mongodb.core.index.CompoundIndexes

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
            "{'originAssignmentId': {'\$exists': true, '\$ne': null}}"

        indexes.getValue("ux_assignment_course_copy_fingerprint").unique shouldBe true
        indexes.getValue("ux_assignment_course_copy_fingerprint").def shouldBe
            "{'courseId': 1, 'copyFingerprint': 1}"
        indexes.getValue("ux_assignment_course_copy_fingerprint").partialFilter shouldBe
            "{'copyFingerprint': {'\$exists': true, '\$ne': null}}"
    }
})
