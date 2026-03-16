package com.example.aandi_post_web_server.submission.enum

enum class AssignmentSubmissionLanguage {
    KOTLIN,
    DART,
    PYTHON,
}

enum class AssignmentSubmissionStatus {
    PENDING,
    RUNNING,
    ACCEPTED,
    WRONG_ANSWER,
    TIME_LIMIT_EXCEEDED,
    MEMORY_LIMIT_EXCEEDED,
    RUNTIME_ERROR,
    COMPILE_ERROR,
}
