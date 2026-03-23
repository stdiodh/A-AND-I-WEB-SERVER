package com.example.aandi_post_web_server.user.service

enum class ReportUserSyncOutcome {
    UPSERTED,
    DELETED,
    IGNORED_STALE,
}
