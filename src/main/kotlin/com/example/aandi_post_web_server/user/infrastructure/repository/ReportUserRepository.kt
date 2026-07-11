package com.example.aandi_post_web_server.user.infrastructure.repository

import com.example.aandi_post_web_server.user.entity.ReportUser
import org.springframework.data.mongodb.repository.Query
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface ReportUserRepository : ReactiveMongoRepository<ReportUser, String> {
    @Query("{ 'publicCode': ?0, 'deletedAt': null }")
    fun findByPublicCodeAndDeletedAtIsNull(publicCode: String): Mono<ReportUser>

    @Query("{ '_id': { '\$in': ?0 }, 'deletedAt': null }")
    fun findAllByIdInAndDeletedAtIsNull(ids: Collection<String>): Flux<ReportUser>
}
