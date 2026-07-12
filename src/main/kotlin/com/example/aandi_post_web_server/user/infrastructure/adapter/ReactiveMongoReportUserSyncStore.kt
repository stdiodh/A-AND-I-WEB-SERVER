package com.example.aandi_post_web_server.user.infrastructure.adapter

import com.example.aandi_post_web_server.user.application.port.ReportUserSyncStore
import com.example.aandi_post_web_server.user.entity.ReportUser
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class ReactiveMongoReportUserSyncStore(
    private val reactiveMongoTemplate: ReactiveMongoTemplate,
) : ReportUserSyncStore {
    override fun upsertActive(user: ReportUser): Mono<Void> {
        val query = Query.query(
            Criteria.where("_id").`is`(user.id).orOperator(
                Criteria.where("updatedAt").lt(user.updatedAt),
                Criteria.where("updatedAt").exists(false),
                Criteria().andOperator(
                    Criteria.where("updatedAt").`is`(user.updatedAt),
                    Criteria.where("deletedAt").`is`(null),
                ),
            ),
        )
        val update = Update()
            .set("publicCode", user.publicCode)
            .set("username", user.username)
            .set("role", user.role)
            .set("nickname", user.nickname)
            .set("profileImageUrl", user.profileImageUrl)
            .set("syncedAt", user.syncedAt)
            .set("updatedAt", user.updatedAt)
            .setOnInsert("_class", REPORT_USER_TYPE_ALIAS)
            .unset("deletedAt")

        return reactiveMongoTemplate.upsert(query, update, ReportUser::class.java).then()
    }

    override fun upsertTombstone(user: ReportUser): Mono<Void> {
        val query = Query.query(
            Criteria.where("_id").`is`(user.id).orOperator(
                Criteria.where("updatedAt").lte(user.updatedAt),
                Criteria.where("updatedAt").exists(false),
            ),
        )
        val update = Update()
            .set("publicCode", user.publicCode)
            .set("username", user.username)
            .set("role", user.role)
            .set("syncedAt", user.syncedAt)
            .set("updatedAt", user.updatedAt)
            .set("deletedAt", user.deletedAt)
            .setOnInsert("_class", REPORT_USER_TYPE_ALIAS)
            .unset("nickname")
            .unset("profileImageUrl")

        return reactiveMongoTemplate.upsert(query, update, ReportUser::class.java).then()
    }

    override fun findById(userId: String): Mono<ReportUser> =
        reactiveMongoTemplate.findById(userId, ReportUser::class.java)

    private companion object {
        const val REPORT_USER_TYPE_ALIAS = "reportUser"
    }
}
