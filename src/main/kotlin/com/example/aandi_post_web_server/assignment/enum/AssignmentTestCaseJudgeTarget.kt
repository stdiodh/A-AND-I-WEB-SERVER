package com.example.aandi_post_web_server.assignment.enum

enum class AssignmentTestCaseJudgeTarget {
    PUBLIC,
    HIDDEN,
    EXCLUDED,
    ;

    fun shouldPublishToOj(): Boolean = this == PUBLIC || this == HIDDEN
}
