package com.example.aandi_post_web_server.support

import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object TestJwtFactory {
    fun createAccessToken(
        subject: String,
        role: String,
    ): String {
        val now = Instant.now().epochSecond
        val headerJson = """{"alg":"HS256","typ":"JWT"}"""
        val payloadJson = """
            {
              "iss":"http://localhost:9000",
              "sub":"$subject",
              "aud":["aandi-gateway"],
              "role":"$role",
              "token_type":"ACCESS",
              "jti":"${UUID.randomUUID()}",
              "iat":$now,
              "exp":${now + 3600}
            }
        """.trimIndent().replace("\n", "").replace("  ", "")

        val encoder = Base64.getUrlEncoder().withoutPadding()
        val encodedHeader = encoder.encodeToString(headerJson.toByteArray())
        val encodedPayload = encoder.encodeToString(payloadJson.toByteArray())
        val signatureInput = "$encodedHeader.$encodedPayload"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec("local-dev-jwt-secret-must-be-at-least-32-bytes".toByteArray(), "HmacSHA256"))
        val signature = encoder.encodeToString(mac.doFinal(signatureInput.toByteArray()))
        return "$signatureInput.$signature"
    }
}
