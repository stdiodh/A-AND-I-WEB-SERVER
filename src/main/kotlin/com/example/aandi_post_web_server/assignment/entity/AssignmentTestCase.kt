package com.example.aandi_post_web_server.assignment.entity

import com.example.aandi_post_web_server.assignment.enum.AssignmentTestCaseVisibility
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "assignment_test_cases")
@CompoundIndex(name = "ux_assignment_test_case_seq", def = "{'assignmentId': 1, 'seq': 1}", unique = true)
data class AssignmentTestCase(
    @Id
    val id: String? = null,
    val assignmentId: String,
    val seq: Int,
    val inputValues: List<String>,
    val outputText: String,
    val visibility: AssignmentTestCaseVisibility = AssignmentTestCaseVisibility.PUBLIC,
    val description: String? = null,
    val createdAt: Instant = Instant.now(),
)
