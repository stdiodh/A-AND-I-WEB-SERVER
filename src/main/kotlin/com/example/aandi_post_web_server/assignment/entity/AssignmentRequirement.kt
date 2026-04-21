package com.example.aandi_post_web_server.assignment.entity

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.TypeAlias
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "assignment_requirements")
@TypeAlias("assignmentRequirement")
@CompoundIndex(name = "ux_assignment_requirement_sort", def = "{'assignmentId': 1, 'sortOrder': 1}", unique = true)
data class AssignmentRequirement(
    @Id
    val id: String? = null,
    val assignmentId: String,
    val sortOrder: Int,
    val requirementText: String,
    val createdAt: Instant = Instant.now(),
)
