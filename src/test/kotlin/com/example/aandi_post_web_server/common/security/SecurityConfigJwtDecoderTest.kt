package com.example.aandi_post_web_server.common.security

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.security.oauth2.jwt.JwtException
import reactor.test.StepVerifier
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SecurityConfigJwtDecoderTest : StringSpec({
    "jwtDecoder는 유효한 HS256 access token을 검증한다" {
        val now = Instant.now()
        val token = signedJwt(
            issuedAt = now.minusSeconds(1),
            expiresAt = now.plusSeconds(300),
        )

        StepVerifier.create(jwtDecoder().decode(token))
            .assertNext { jwt ->
                jwt.subject shouldBe TOKEN_SUBJECT
                jwt.getClaimAsString("token_type") shouldBe "ACCESS"
            }
            .verifyComplete()
    }

    "jwtDecoder는 malformed compact JWT를 거부한다" {
        StepVerifier.create(jwtDecoder().decode("not-a-compact-jwt"))
            .expectError(JwtException::class.java)
            .verify()
    }

    "jwtDecoder는 변조된 서명을 거부한다" {
        val now = Instant.now()
        val token = signedJwt(
            secret = "different-test-secret-that-is-at-least-32-bytes",
            issuedAt = now.minusSeconds(1),
            expiresAt = now.plusSeconds(300),
        )

        StepVerifier.create(jwtDecoder().decode(token))
            .expectError(JwtException::class.java)
            .verify()
    }

    "jwtDecoder는 clock skew를 넘겨 만료된 token을 거부한다" {
        val now = Instant.now()
        val token = signedJwt(
            issuedAt = now.minusSeconds(300),
            expiresAt = now.minusSeconds(60),
        )

        StepVerifier.create(jwtDecoder().decode(token))
            .expectError(JwtException::class.java)
            .verify()
    }

    "jwtDecoder는 잘못된 issuer를 거부한다" {
        val now = Instant.now()
        val token = signedJwt(
            issuer = "https://unexpected.example.com",
            issuedAt = now.minusSeconds(1),
            expiresAt = now.plusSeconds(300),
        )

        StepVerifier.create(jwtDecoder().decode(token))
            .expectError(JwtException::class.java)
            .verify()
    }

    "jwtDecoder는 audience와 access claim 정책 위반을 거부한다" {
        val now = Instant.now()
        val invalidTokens = listOf(
            signedJwt(
                audience = listOf("another-audience"),
                issuedAt = now.minusSeconds(1),
                expiresAt = now.plusSeconds(300),
            ),
            signedJwt(
                tokenType = "REFRESH",
                issuedAt = now.minusSeconds(1),
                expiresAt = now.plusSeconds(300),
            ),
            signedJwt(
                subject = "not-a-uuid",
                issuedAt = now.minusSeconds(1),
                expiresAt = now.plusSeconds(300),
            ),
        )

        invalidTokens.forEach { token ->
            StepVerifier.create(jwtDecoder().decode(token))
                .expectError(JwtException::class.java)
                .verify()
        }
    }

    "jwtDecoder는 32 byte보다 짧은 secret 설정을 거부한다" {
        shouldThrow<IllegalArgumentException> {
            SecurityConfig().jwtDecoder(
                JwtPolicyProperties(
                    issuer = TOKEN_ISSUER,
                    audience = TOKEN_AUDIENCE,
                    secret = "too-short",
                    clockSkewSeconds = 30,
                )
            )
        }
    }
})

private fun jwtDecoder() =
    SecurityConfig().jwtDecoder(
        JwtPolicyProperties(
            issuer = TOKEN_ISSUER,
            audience = TOKEN_AUDIENCE,
            secret = TOKEN_SECRET,
            clockSkewSeconds = 30,
        )
    )

private fun signedJwt(
    secret: String = TOKEN_SECRET,
    issuer: String = TOKEN_ISSUER,
    audience: List<String> = listOf(TOKEN_AUDIENCE),
    tokenType: String = "ACCESS",
    subject: String = TOKEN_SUBJECT,
    role: String = "USER",
    issuedAt: Instant,
    expiresAt: Instant,
): String {
    val headerJson = """{"alg":"HS256","typ":"JWT"}"""
    val audienceJson = audience.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
    val payloadJson = """{"iss":"$issuer","sub":"$subject","aud":$audienceJson,"role":"$role","token_type":"$tokenType","jti":"${UUID.randomUUID()}","iat":${issuedAt.epochSecond},"exp":${expiresAt.epochSecond}}"""
    val encoder = Base64.getUrlEncoder().withoutPadding()
    val encodedHeader = encoder.encodeToString(headerJson.toByteArray(StandardCharsets.UTF_8))
    val encodedPayload = encoder.encodeToString(payloadJson.toByteArray(StandardCharsets.UTF_8))
    val signingInput = "$encodedHeader.$encodedPayload"
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
    val signature = encoder.encodeToString(mac.doFinal(signingInput.toByteArray(StandardCharsets.UTF_8)))
    return "$signingInput.$signature"
}

private const val TOKEN_SECRET = "local-dev-jwt-secret-must-be-at-least-32-bytes"
private const val TOKEN_ISSUER = "http://localhost:9000"
private const val TOKEN_AUDIENCE = "aandi-gateway"
private const val TOKEN_SUBJECT = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
