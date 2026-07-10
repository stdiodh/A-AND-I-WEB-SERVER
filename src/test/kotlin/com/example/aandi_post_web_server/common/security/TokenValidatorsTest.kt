package com.example.aandi_post_web_server.common.security

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class TokenValidatorsTest : StringSpec({
    "AccessTokenClaimsValidator는 정상 ACCESS 토큰을 통과시킨다" {
        val now = Instant.parse("2026-03-05T00:00:00Z")
        val validator = AccessTokenClaimsValidator(
            clockSkew = Duration.ofSeconds(30),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

        val jwt = buildJwt(
            tokenType = "ACCESS",
            role = "USER",
            subject = UUID.randomUUID().toString(),
            jti = "jti-1",
            issuedAt = now,
            audience = listOf("aandi-gateway"),
        )

        val result = validator.validate(jwt)
        result.hasErrors() shouldBe false
    }

    "AccessTokenClaimsValidator는 token_type이 ACCESS가 아니면 실패한다" {
        val now = Instant.parse("2026-03-05T00:00:00Z")
        val validator = AccessTokenClaimsValidator(Duration.ofSeconds(30), Clock.fixed(now, ZoneOffset.UTC))
        val jwt = buildJwt(tokenType = "REFRESH", issuedAt = now)

        val result = validator.validate(jwt)
        result.hasErrors() shouldBe true
    }

    "AccessTokenClaimsValidator는 미래 iat를 거부한다" {
        val now = Instant.parse("2026-03-05T00:00:00Z")
        val validator = AccessTokenClaimsValidator(Duration.ofSeconds(30), Clock.fixed(now, ZoneOffset.UTC))
        val jwt = buildJwt(issuedAt = now.plusSeconds(31))

        val result = validator.validate(jwt)
        result.hasErrors() shouldBe true
    }

    "AccessTokenClaimsValidator는 유효하지 않은 UUID subject를 거부한다" {
        val now = Instant.parse("2026-03-05T00:00:00Z")
        val validator = AccessTokenClaimsValidator(Duration.ofSeconds(30), Clock.fixed(now, ZoneOffset.UTC))
        val result = validator.validate(buildJwt(subject = "not-a-uuid", issuedAt = now))

        result.errors.single().description shouldBe "sub must be UUID"
    }

    "AccessTokenClaimsValidator는 지원하지 않는 role을 거부한다" {
        val now = Instant.parse("2026-03-05T00:00:00Z")
        val validator = AccessTokenClaimsValidator(Duration.ofSeconds(30), Clock.fixed(now, ZoneOffset.UTC))
        val result = validator.validate(buildJwt(role = "SUPER_ADMIN", issuedAt = now))

        result.errors.single().description shouldBe "role must be one of USER, ORGANIZER, ADMIN"
    }

    "AccessTokenClaimsValidator는 blank jti를 거부한다" {
        val now = Instant.parse("2026-03-05T00:00:00Z")
        val validator = AccessTokenClaimsValidator(Duration.ofSeconds(30), Clock.fixed(now, ZoneOffset.UTC))
        val result = validator.validate(buildJwt(jti = " ", issuedAt = now))

        result.errors.single().description shouldBe "jti is required"
    }

    "AccessTokenClaimsValidator는 iat 누락을 거부한다" {
        val now = Instant.parse("2026-03-05T00:00:00Z")
        val validator = AccessTokenClaimsValidator(Duration.ofSeconds(30), Clock.fixed(now, ZoneOffset.UTC))
        val result = validator.validate(buildJwt(issuedAt = null))

        result.errors.single().description shouldBe "iat is required"
    }

    "AccessTokenClaimsValidator는 clock skew 경계의 iat를 허용한다" {
        val now = Instant.parse("2026-03-05T00:00:00Z")
        val validator = AccessTokenClaimsValidator(Duration.ofSeconds(30), Clock.fixed(now, ZoneOffset.UTC))
        val result = validator.validate(buildJwt(issuedAt = now.plusSeconds(30)))

        result.hasErrors() shouldBe false
    }

    "RequiredAudienceValidator는 필수 aud가 없으면 실패한다" {
        val validator = RequiredAudienceValidator("aandi-gateway")
        val jwt = buildJwt(audience = listOf("another-aud"))

        val result = validator.validate(jwt)
        result.hasErrors() shouldBe true
    }

    "RequiredAudienceValidator는 필수 aud가 있으면 통과한다" {
        val validator = RequiredAudienceValidator("aandi-gateway")
        val jwt = buildJwt(audience = listOf("another-aud", "aandi-gateway"))

        val result = validator.validate(jwt)
        result.hasErrors() shouldBe false
    }
}) {
    companion object {
        private fun buildJwt(
            tokenType: String = "ACCESS",
            role: String = "USER",
            subject: String = UUID.randomUUID().toString(),
            jti: String = "jti-default",
            issuedAt: Instant? = Instant.parse("2026-03-05T00:00:00Z"),
            audience: List<String> = listOf("aandi-gateway"),
        ): Jwt {
            val builder = Jwt.withTokenValue("token-value")
                .header("alg", "HS256")
                .claim("token_type", tokenType)
                .claim("role", role)
                .claim("sub", subject)
                .claim("jti", jti)
                .claim("aud", audience)
            issuedAt?.let {
                builder.issuedAt(it)
                builder.expiresAt(it.plusSeconds(3600))
            }
            return builder.build()
        }
    }
}
