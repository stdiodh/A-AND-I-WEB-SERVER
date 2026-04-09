package com.example.aandi_post_web_server.report.v2.mapper

import com.example.aandi_post_web_server.assignment.dtos.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentSummaryResponse

class ReportResponseMapper {
    companion object {
        fun assignmentSummaryList(data: List<AssignmentSummaryResponse>): List<AssignmentSummaryResponse> = data

        fun assignmentDetail(data: AssignmentDetailResponse): AssignmentDetailResponse = data
    }
}
