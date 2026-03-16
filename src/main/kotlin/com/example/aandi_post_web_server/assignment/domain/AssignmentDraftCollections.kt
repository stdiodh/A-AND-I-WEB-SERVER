package com.example.aandi_post_web_server.assignment.domain

import com.example.aandi_post_web_server.assignment.dtos.CreateAssignmentExampleRequest
import com.example.aandi_post_web_server.assignment.dtos.CreateAssignmentRequirementRequest
import com.example.aandi_post_web_server.assignment.entity.AssignmentExample
import com.example.aandi_post_web_server.assignment.entity.AssignmentRequirement
import com.example.aandi_post_web_server.assignment.entity.AssignmentDelivery
import java.time.Instant

data class AssignmentRequirementDraft(
    val sortOrder: Int,
    val requirementText: String,
)

data class AssignmentExampleDraft(
    val seq: Int,
    val inputText: String,
    val outputText: String,
)

class AssignmentRequirementDrafts private constructor(
    private val values: List<AssignmentRequirementDraft>,
) {
    companion object {
        fun fromRequests(requests: List<CreateAssignmentRequirementRequest>): AssignmentRequirementDrafts {
            val drafts = requests.map { AssignmentRequirementDraft(it.sortOrder, it.requirementText) }
            return AssignmentRequirementDrafts(drafts)
        }
    }

    fun isEmpty(): Boolean = values.isEmpty()

    fun toEntities(assignmentId: String, createdAt: Instant): List<AssignmentRequirement> {
        return values.map {
            AssignmentRequirement(
                assignmentId = assignmentId,
                sortOrder = it.sortOrder,
                requirementText = it.requirementText,
                createdAt = createdAt,
            )
        }
    }
}

class AssignmentExampleDrafts private constructor(
    private val values: List<AssignmentExampleDraft>,
) {
    companion object {
        fun fromRequests(requests: List<CreateAssignmentExampleRequest>): AssignmentExampleDrafts {
            val drafts = requests.map {
                AssignmentExampleDraft(
                    seq = it.seq,
                    inputText = it.inputText,
                    outputText = it.outputText,
                )
            }
            validateUniqueSeq(drafts)
            return AssignmentExampleDrafts(drafts)
        }

        private fun validateUniqueSeq(drafts: List<AssignmentExampleDraft>) {
            val seqValues = drafts.map { it.seq }
            require(seqValues.distinct().size == seqValues.size) { "examples.seq 값은 과제 내에서 유일해야 합니다." }
        }
    }

    fun isEmpty(): Boolean = values.isEmpty()

    fun toEntities(assignmentId: String, createdAt: Instant): List<AssignmentExample> {
        return values.map {
            AssignmentExample(
                assignmentId = assignmentId,
                seq = it.seq,
                inputText = it.inputText,
                outputText = it.outputText,
                description = null,
                createdAt = createdAt,
            )
        }
    }
}

class DeliveredAssignmentIds private constructor(
    private val values: Set<String>,
) {
    companion object {
        fun fromDeliveries(deliveries: List<AssignmentDelivery>): DeliveredAssignmentIds {
            return DeliveredAssignmentIds(deliveries.map { it.assignmentId }.toSet())
        }
    }

    fun isEmpty(): Boolean = values.isEmpty()

    fun asCollection(): Collection<String> = values
}
