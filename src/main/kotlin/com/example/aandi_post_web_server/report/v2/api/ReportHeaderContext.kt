package com.example.aandi_post_web_server.report.v2.api

data class ReportHeaderContext(
    val deviceOS: String,
    val authenticate: String,
    val timestamp: String,
    val salt: String?,
) {
    companion object {
        const val ATTRIBUTE_NAME: String = "reportV2HeaderContext"
    }
}
