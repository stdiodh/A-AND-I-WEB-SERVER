package com.example.aandi_post_web_server.common.config

import com.example.aandi_post_web_server.common.error.RequestIdSupport
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsWebFilter
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

@Configuration
class WebConfig(
    @Value("\${app.cors.allowed-origin-patterns}") private val configuredOriginPatterns: List<String>,
    @Value("\${app.cors.allowed-methods}") private val configuredMethods: List<String>,
    @Value("\${app.cors.allowed-headers}") private val configuredHeaders: List<String>,
    @Value("\${app.cors.exposed-headers:}") private val configuredExposedHeaders: List<String>,
    @Value("\${app.cors.allow-credentials:false}") private val allowCredentials: Boolean,
    @Value("\${app.cors.max-age-seconds:3600}") private val maxAgeSeconds: Long,
) {

    @Bean
    fun corsWebFilter(): CorsWebFilter {
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

        return CorsWebFilter(source)
    }

    private fun List<String>.toNormalizedValues(): List<String> =
        map { it.trim() }.filter { it.isNotBlank() }
}
