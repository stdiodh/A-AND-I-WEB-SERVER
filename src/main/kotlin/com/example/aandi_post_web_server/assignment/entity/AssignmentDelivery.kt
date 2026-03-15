package com.example.aandi_post_web_server.assignment.entity

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "assignment_deliveries")
@CompoundIndex(name = "ux_assignment_delivery_user", def = "{'assignmentId': 1, 'userId': 1}", unique = true)
data class AssignmentDelivery(
    @Id
    val id: String? = null,
    val assignmentId: String,
    val userId: String,
    val deliveredAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
)
