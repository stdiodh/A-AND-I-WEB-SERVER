package com.example.aandi_post_web_server.course.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow

class CourseValueObjectsTest : StringSpec({
    "CourseSlug는 trim + lowercase로 정규화된다" {
        CourseSlug.from("  Back-Basic  ").value shouldBe "back-basic"
    }

    "CourseSlug는 빈 문자열을 허용하지 않는다" {
        shouldThrow<IllegalArgumentException> {
            CourseSlug.from("   ")
        }
    }

    "CourseId/UserId/AssignmentId는 trim 후 저장된다" {
        CourseId.from(" course-1 ").value shouldBe "course-1"
        UserId.from(" user-1 ").value shouldBe "user-1"
        AssignmentId.from(" 8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111 ").value shouldBe "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
    }

    "WeekNo는 1 이상만 허용한다" {
        WeekNo.from(1).value shouldBe 1
        shouldThrow<IllegalArgumentException> {
            WeekNo.from(0)
        }
    }
})
