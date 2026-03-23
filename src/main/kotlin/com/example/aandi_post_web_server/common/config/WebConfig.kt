package com.example.aandi_post_web_server.common.config

import com.example.aandi_post_web_server.common.error.RequestIdSupport
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsConfigurationSource
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

@Configuration
class WebConfig(
    @Value("\${app.cors.allowed-origin-patterns}") private val configuredOriginPatterns: String,
    @Value("\${app.cors.allowed-methods}") private val configuredMethods: String,
    @Value("\${app.cors.allowed-headers}") private val configuredHeaders: String,
    @Value("\${app.cors.exposed-headers:}") private val configuredExposedHeaders: String,
    @Value("\${app.cors.allow-credentials:false}") private val allowCredentials: Boolean,
    @Value("\${app.cors.max-age-seconds:3600}") private val maxAgeSeconds: Long,
) {

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            this.allowedOriginPatterns = configuredOriginPatterns.toNormalizedValues()
            this.allowedMethods = configuredMethods.toNormalizedValues()
            this.allowedHeaders = configuredHeaders.toNormalizedValues()
            val normalizedExposedHeaders = (configuredExposedHeaders.toNormalizedValues() + RequestIdSupport.HEADER_NAME).distinct()
            if (normalizedExposedHeaders.isNotEmpty()) {
                this.exposedHeaders = normalizedExposedHeaders
            }
            this.allowCredentials = allowCredentials
            this.maxAge = maxAgeSeconds
        }

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }

    private fun String.toNormalizedValues(): List<String> =
        split(',').map { it.trim() }.filter { it.isNotBlank() }
}
