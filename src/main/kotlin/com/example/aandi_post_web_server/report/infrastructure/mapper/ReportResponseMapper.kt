package com.example.aandi_post_web_server.report.infrastructure.mapper

import com.example.aandi_post_web_server.assignment.api.dto.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentSummaryResponse

class ReportResponseMapper {
    companion object {
        fun assignmentSummaryList(data: List<AssignmentSummaryResponse>): List<AssignmentSummaryResponse> = data

        fun assignmentDetail(data: AssignmentDetailResponse): AssignmentDetailResponse = data
    }
}
