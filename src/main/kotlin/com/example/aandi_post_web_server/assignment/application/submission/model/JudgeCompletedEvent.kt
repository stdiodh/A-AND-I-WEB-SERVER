package com.example.aandi_post_web_server.assignment.application.submission.model

import java.time.Instant

data class JudgeCompletedEvent(
    val assignmentId: String,
    val publicCode: String,
    val score: Int,
    val passedCases: Int,
    val totalCases: Int,
    val timestamp: Instant,
)
