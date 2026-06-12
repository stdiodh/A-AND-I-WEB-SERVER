package com.example.aandi_post_web_server.assignment.domain.model

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.TypeAlias
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "assignmentActivationSettings")
@TypeAlias("assignmentActivation")
data class AssignmentActivation(
    @Id
    val id: String = GLOBAL_ID,
    val active: Boolean,
    val updatedAt: Instant,
    val updatedBy: String?,
) {
    companion object {
        const val GLOBAL_ID: String = "global"

        fun defaultActive(): AssignmentActivation =
            AssignmentActivation(
                id = GLOBAL_ID,
                active = true,
                updatedAt = Instant.EPOCH,
                updatedBy = null,
            )
    }
}
