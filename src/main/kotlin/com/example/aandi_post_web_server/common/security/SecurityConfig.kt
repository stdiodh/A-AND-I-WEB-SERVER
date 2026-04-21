package com.example.aandi_post_web_server.common.security

import com.example.aandi_post_web_server.common.error.ErrorResponseFactory
import com.example.aandi_post_web_server.common.error.RequestIdSupport
import com.example.aandi_post_web_server.common.openapi.ApiEnvelope
import com.example.aandi_post_web_server.common.api.factory.V2ApiResponseFactory
import com.example.aandi_post_web_server.common.error.v2.V2ErrorCode
import com.example.aandi_post_web_server.common.error.v2.V2ExceptionMapper
import com.example.aandi_post_web_server.common.security.v2.V2AuthenticateHeaderBridgeFilter
import com.example.aandi_post_web_server.common.security.v2.V2HeaderValidationFilter
import com.example.aandi_post_web_server.common.security.v2.V2PathMatcher
import com.example.aandi_post_web_server.common.security.v2.V2SecurityProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.web.cors.reactive.CorsConfigurationSource
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets
import java.time.Duration
import javax.crypto.spec.SecretKeySpec

@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(JwtPolicyProperties::class, V2SecurityProperties::class)
class SecurityConfig {

    @Bean
    fun securityWebFilterChain(
        http: ServerHttpSecurity,
        corsConfigurationSource: CorsConfigurationSource,
        jwtDecoder: ReactiveJwtDecoder,
        errorResponseFactory: ErrorResponseFactory,
        objectMapper: ObjectMapper,
        v2SecurityProperties: V2SecurityProperties,
    ): SecurityWebFilterChain =
        http
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource) }
            .authorizeExchange {
                it.pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                it.pathMatchers(
                    "/v3/api-docs/**",
                    "/swagger/report/**",
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/swagger-ui/index.html",
                ).permitAll()
                it.pathMatchers("/v2/admin/report/**", "/v2/admin/courses/**").hasRole("ADMIN")
                it.pathMatchers("/v1/admin/**").hasRole("ADMIN")
                it.pathMatchers("/v1/report/**", "/v1/courses/**")
                    .hasAnyRole("USER", "ORGANIZER", "ADMIN")
                it.pathMatchers("/v2/**")
                    .hasAnyRole("USER", "ORGANIZER", "ADMIN")
                it.anyExchange().denyAll()
            }
            .exceptionHandling { exceptions ->
                exceptions.authenticationEntryPoint { exchange, authException ->
                    if (V2PathMatcher.isV2Path(exchange.request.path.pathWithinApplication().value())) {
                        val result = V2ExceptionMapper.fromThrowable(authException)
                        writeV2ErrorResponse(exchange, objectMapper, result)
                    } else {
                        val result = errorResponseFactory.unauthorized(exchange, authException.message)
                        writeErrorResponse(exchange, objectMapper, result)
                    }
                }
                exceptions.accessDeniedHandler { exchange, accessDeniedException ->
                    if (V2PathMatcher.isV2Path(exchange.request.path.pathWithinApplication().value())) {
                        val result = V2ExceptionMapper.fromThrowable(accessDeniedException)
                        writeV2ErrorResponse(exchange, objectMapper, result)
                    } else {
                        val result = errorResponseFactory.forbidden(exchange, accessDeniedException.message)
                        writeErrorResponse(exchange, objectMapper, result)
                    }
                }
            }
            .addFilterBefore(V2AuthenticateHeaderBridgeFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
            .addFilterAfter(
                V2HeaderValidationFilter(v2SecurityProperties.saltSecret),
                SecurityWebFiltersOrder.AUTHENTICATION,
            )
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.jwtDecoder(jwtDecoder)
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                }
            }
            .build()

    @Bean
    fun jwtDecoder(jwtPolicy: JwtPolicyProperties): ReactiveJwtDecoder {
        val secret = jwtPolicy.secret
        require(secret.toByteArray(StandardCharsets.UTF_8).size >= 32) {
            "security.jwt.secret must be at least 32 bytes"
        }

        val secretKey = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        val decoder = NimbusReactiveJwtDecoder.withSecretKey(secretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build()

        val timestampValidator = JwtTimestampValidator(Duration.ofSeconds(jwtPolicy.clockSkewSeconds))
        val issuerValidator = JwtIssuerValidator(jwtPolicy.issuer)
        val audienceValidator = RequiredAudienceValidator(jwtPolicy.audience)
        val claimsValidator = AccessTokenClaimsValidator(Duration.ofSeconds(jwtPolicy.clockSkewSeconds))

        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(timestampValidator, issuerValidator, audienceValidator, claimsValidator),
        )
        return decoder
    }

    private fun jwtAuthenticationConverter(): Converter<Jwt, Mono<AbstractAuthenticationToken>> =
        Converter { jwt ->
            val role = UserRole.fromClaim(jwt.getClaimAsString("role"))
            val authorities = role?.grantedAuthorities() ?: UserRole.USER.grantedAuthorities()
            Mono.just(JwtAuthenticationToken(jwt, authorities, jwt.subject))
        }

    private fun writeErrorResponse(
        exchange: ServerWebExchange,
        objectMapper: ObjectMapper,
        result: com.example.aandi_post_web_server.common.error.ApiErrorResult,
    ): Mono<Void> {
        val response = exchange.response
        response.statusCode = HttpStatus.valueOf(result.status.value())
        response.headers.contentType = MediaType.APPLICATION_JSON
        response.headers.set(RequestIdSupport.HEADER_NAME, RequestIdSupport.resolveRequestId(exchange))
        val payload = runCatching { objectMapper.writeValueAsBytes(result.body) }
            .getOrElse { objectMapper.writeValueAsBytes(ApiEnvelope.failure("INTERNAL_ERROR", "서버 내부 오류가 발생했습니다.")) }
        return response.writeWith(Mono.just(response.bufferFactory().wrap(payload)))
    }

    private fun writeV2ErrorResponse(
        exchange: ServerWebExchange,
        objectMapper: ObjectMapper,
        result: V2ExceptionMapper.V2ErrorResult,
    ): Mono<Void> {
        val response = exchange.response
        response.statusCode = result.status
        response.headers.contentType = MediaType.APPLICATION_JSON
        response.headers.set(RequestIdSupport.HEADER_NAME, RequestIdSupport.resolveRequestId(exchange))
        val payload = runCatching {
            objectMapper.writeValueAsBytes(V2ApiResponseFactory.failure(result.errorCode, result.message))
        }.getOrElse {
            objectMapper.writeValueAsBytes(
                V2ApiResponseFactory.failure(V2ErrorCode.INTERNAL_ERROR, V2ErrorCode.INTERNAL_ERROR.messageTemplate)
            )
        }
        return response.writeWith(Mono.just(response.bufferFactory().wrap(payload)))
    }
}
