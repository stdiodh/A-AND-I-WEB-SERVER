package com.example.aandi_post_web_server.user.repository

import com.example.aandi_post_web_server.user.entity.ReportUser
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Mono

interface ReportUserRepository : ReactiveMongoRepository<ReportUser, String> {
    fun findByPublicCode(publicCode: String): Mono<ReportUser>
}
