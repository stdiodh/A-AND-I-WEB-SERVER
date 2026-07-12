package com.example.aandi_post_web_server.assignment.domain.model

import com.example.aandi_post_web_server.assignment.entity.AssignmentTestCase
import com.example.aandi_post_web_server.assignment.entity.AssignmentRequirement
import com.example.aandi_post_web_server.assignment.entity.AssignmentDelivery
import java.time.Instant

data class AssignmentRequirementDraft(
    val sortOrder: Int,
    val requirementText: String,
)

data class AssignmentTestCaseDraft(
    val seq: Int,
    val inputValues: List<String>,
    val outputText: String,
    val visibility: AssignmentTestCaseVisibility,
)

class AssignmentRequirementDrafts private constructor(
    private val values: List<AssignmentRequirementDraft>,
) {
    companion object {
        fun from(drafts: List<AssignmentRequirementDraft>): AssignmentRequirementDrafts =
            AssignmentRequirementDrafts(drafts.toList())
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

class AssignmentTestCaseDrafts private constructor(
    private val values: List<AssignmentTestCaseDraft>,
) {
    companion object {
        fun from(drafts: List<AssignmentTestCaseDraft>): AssignmentTestCaseDrafts {
            validateUniqueSeq(drafts)
            return AssignmentTestCaseDrafts(drafts.toList())
        }

        private fun validateUniqueSeq(drafts: List<AssignmentTestCaseDraft>) {
            val seqValues = drafts.map { it.seq }
            require(seqValues.distinct().size == seqValues.size) { "testCases.seq 값은 과제 내에서 유일해야 합니다." }
        }
    }

    fun isEmpty(): Boolean = values.isEmpty()

    fun toEntities(assignmentId: String, createdAt: Instant): List<AssignmentTestCase> {
        return values.map {
            AssignmentTestCase(
                assignmentId = assignmentId,
                seq = it.seq,
                inputValues = it.inputValues,
                outputText = it.outputText,
                visibility = it.visibility,
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
