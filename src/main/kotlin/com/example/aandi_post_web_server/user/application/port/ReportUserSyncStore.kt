package com.example.aandi_post_web_server.user.application.port

import com.example.aandi_post_web_server.user.entity.ReportUser
import reactor.core.publisher.Mono

interface ReportUserSyncStore {
    fun upsertActive(user: ReportUser): Mono<Void>
    fun upsertTombstone(user: ReportUser): Mono<Void>
    fun findById(userId: String): Mono<ReportUser>
}
