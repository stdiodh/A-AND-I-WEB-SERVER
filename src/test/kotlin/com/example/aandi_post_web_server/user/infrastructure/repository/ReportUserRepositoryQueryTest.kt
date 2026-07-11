package com.example.aandi_post_web_server.user.infrastructure.repository

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.data.mongodb.repository.Query

class ReportUserRepositoryQueryTest : StringSpec({
    "user reads explicitly include legacy active documents and exclude tombstones" {
        val publicCodeQuery = ReportUserRepository::class.java
            .getMethod("findByPublicCodeAndDeletedAtIsNull", String::class.java)
            .getAnnotation(Query::class.java)
        val idsQuery = ReportUserRepository::class.java
            .getMethod("findAllByIdInAndDeletedAtIsNull", Collection::class.java)
            .getAnnotation(Query::class.java)

        publicCodeQuery.value shouldBe "{ 'publicCode': ?0, 'deletedAt': null }"
        idsQuery.value shouldBe "{ '_id': { '\$in': ?0 }, 'deletedAt': null }"
    }
})
