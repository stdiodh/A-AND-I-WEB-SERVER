package com.example.aandi_post_web_server.assignment.api.filter

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AssignmentApiPathMatcherTest : StringSpec({
    "v2 user assignment paths are gated" {
        AssignmentApiPathMatcher.isGated("/v2/assignments/abc-123/course") shouldBe true
        AssignmentApiPathMatcher.isGated("/v2/courses/back-basic/assignments") shouldBe true
        AssignmentApiPathMatcher.isGated("/v2/courses/back-basic/assignments/abc-123") shouldBe true
        AssignmentApiPathMatcher.isGated("/v2/courses/back-basic/weeks/1/assignments") shouldBe true
    }

    "v1 user assignment paths are gated" {
        AssignmentApiPathMatcher.isGated("/v1/courses/back-basic/assignments") shouldBe true
        AssignmentApiPathMatcher.isGated("/v1/courses/back-basic/assignments/abc-123") shouldBe true
        AssignmentApiPathMatcher.isGated("/v1/courses/back-basic/weeks/1/assignments") shouldBe true
        AssignmentApiPathMatcher.isGated("/v1/courses/assignments/abc-123/course") shouldBe true
    }

    "admin paths bypass the gate" {
        AssignmentApiPathMatcher.isGated("/v2/admin/courses/back-basic/assignments") shouldBe false
        AssignmentApiPathMatcher.isGated("/v2/admin/courses/back-basic/assignments/abc-123") shouldBe false
        AssignmentApiPathMatcher.isGated("/v2/admin/assignments/activation") shouldBe false
        AssignmentApiPathMatcher.isGated("/v1/admin/courses/back-basic/assignments") shouldBe false
    }

    "non-assignment paths are not gated" {
        AssignmentApiPathMatcher.isGated("/v2/courses") shouldBe false
        AssignmentApiPathMatcher.isGated("/v2/courses/back-basic") shouldBe false
        AssignmentApiPathMatcher.isGated("/v2/courses/back-basic/weeks") shouldBe false
        AssignmentApiPathMatcher.isGated("/v2/courses/back-basic/outline") shouldBe false
        AssignmentApiPathMatcher.isGated("/actuator/health") shouldBe false
        AssignmentApiPathMatcher.isGated("/v1/report/users") shouldBe false
    }
})
