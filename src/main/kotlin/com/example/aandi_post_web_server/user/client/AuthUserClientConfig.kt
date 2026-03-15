package com.example.aandi_post_web_server.user.client

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

@Configuration
@EnableConfigurationProperties(AuthUserClientProperties::class)
class AuthUserClientConfig {
    @Bean
    fun authUserWebClient(properties: AuthUserClientProperties): WebClient =
        WebClient.builder()
            .baseUrl(properties.baseUrl)
            .build()
}

@Component
class WebClientAuthUserClient(
    private val authUserWebClient: WebClient,
) : AuthUserClient {
    override fun findByPublicCode(publicCode: String): Mono<AuthUserLookupPayload> {
        val responseType = object : ParameterizedTypeReference<AuthApiResponse<AuthUserLookupPayload>>() {}
        return authUserWebClient.get()
            .uri { builder ->
                builder.path("/v1/users/lookup")
                    .queryParam("code", publicCode)
                    .build()
            }
            .retrieve()
            .onStatus({ status -> status == HttpStatus.NOT_FOUND }) {
                Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "auth 서버에서 사용자를 찾을 수 없습니다: $publicCode"))
            }
            .bodyToMono(responseType)
            .flatMap { response ->
                if (response.success && response.data != null) {
                    Mono.just(response.data)
                } else {
                    val message = response.error?.message ?: "auth 서버에서 사용자를 찾을 수 없습니다: $publicCode"
                    Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, message))
                }
            }
    }
}
