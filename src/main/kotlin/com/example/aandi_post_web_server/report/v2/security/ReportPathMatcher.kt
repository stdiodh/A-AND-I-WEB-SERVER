package com.example.aandi_post_web_server.report.v2.security

object ReportPathMatcher {
    fun isReportV2Path(path: String): Boolean =
        path == "/v2/report" ||
            path.startsWith("/v2/report/") ||
            path == "/v2/admin/report" ||
            path.startsWith("/v2/admin/report/")
}
