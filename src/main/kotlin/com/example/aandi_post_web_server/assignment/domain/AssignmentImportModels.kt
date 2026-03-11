package com.example.aandi_post_web_server.assignment.domain

import com.example.aandi_post_web_server.assignment.dtos.AssignmentImportSourcePayload
import com.example.aandi_post_web_server.assignment.dtos.CreateAssignmentExampleRequest

data class ImportedAssignmentContent(
    val title: String,
    val description: String,
    val inputDescription: String? = null,
    val outputDescription: String? = null,
    val examples: List<CreateAssignmentExampleRequest> = emptyList(),
)

interface AssignmentImportService {
    fun import(source: AssignmentImportSourcePayload): reactor.core.publisher.Mono<ImportedAssignmentContent>
}
