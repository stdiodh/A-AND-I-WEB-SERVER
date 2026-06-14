package com.example.aandi_post_web_server.assignment.application.activation

import org.springframework.util.AntPathMatcher

object AssignmentApiPathMatcher {

    private val matcher = AntPathMatcher()

    private val GATED_PATTERNS = listOf(
        "/v2/assignments/*/course",
        "/v2/courses/*/assignments",
        "/v2/courses/*/assignments/*",
        "/v2/courses/*/weeks/*/assignments",
        "/v1/courses/*/assignments",
        "/v1/courses/*/assignments/*",
        "/v1/courses/*/weeks/*/assignments",
        "/v1/courses/assignments/*/course",
    )

    private val ADMIN_BYPASS_PATTERNS = listOf(
        "/v1/admin/**",
        "/v2/admin/**",
    )

    fun isGated(path: String): Boolean {
        if (ADMIN_BYPASS_PATTERNS.any { matcher.match(it, path) }) {
            return false
        }
        return GATED_PATTERNS.any { matcher.match(it, path) }
    }
}
