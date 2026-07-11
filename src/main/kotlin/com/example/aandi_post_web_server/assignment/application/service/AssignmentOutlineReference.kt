package com.example.aandi_post_web_server.assignment.application.service

import com.example.aandi_post_web_server.assignment.domain.model.AssignmentDifficulty
import java.time.Instant

data class AssignmentOutlineReference(
    val assignmentId: String,
    val weekNo: Int,
    val orderInWeek: Int,
    val title: String,
    val difficulty: AssignmentDifficulty,
    val startAt: Instant,
    val endAt: Instant,
)
