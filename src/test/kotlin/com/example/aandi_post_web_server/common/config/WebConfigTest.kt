package com.example.aandi_post_web_server.common.config

import com.example.aandi_post_web_server.common.error.RequestIdSupport
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.http.HttpHeaders
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

class WebConfigTest : StringSpec({

    "comma separated CORS 설정에서 admin origin을 허용한다" {
        val webConfig = WebConfig(
            configuredOriginPatterns = "https://admin.aandiclub.com,https://aandiclub.com,https://api.aandiclub.com",
            configuredMethods = "GET,POST,PUT,PATCH,DELETE,OPTIONS",
            configuredHeaders = "Authorization,Content-Type,Accept,Origin,X-Requested-With",
            configuredExposedHeaders = "",
            allowCredentials = false,
            maxAgeSeconds = 3600,
        )

        val source = webConfig.corsConfigurationSource() as UrlBasedCorsConfigurationSource
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.options("/v1/admin/courses")
                .header(HttpHeaders.ORIGIN, "https://admin.aandiclub.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .build(),
        )

        val config = source.getCorsConfiguration(exchange)

        config shouldNotBe null
        val resolvedConfig = config!!
        resolvedConfig.checkOrigin("https://admin.aandiclub.com") shouldBe "https://admin.aandiclub.com"
        resolvedConfig.allowedMethods!! shouldContain "OPTIONS"
        resolvedConfig.allowedHeaders!! shouldContain "Authorization"
        resolvedConfig.exposedHeaders!! shouldContain RequestIdSupport.HEADER_NAME
        resolvedConfig.maxAge shouldBe 3600
    }
})
