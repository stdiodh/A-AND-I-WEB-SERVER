package com.example.aandi_post_web_server.user.application.service

enum class ReportUserSyncOutcome {
    UPSERTED,
    DELETED,
    IGNORED_STALE,
}
