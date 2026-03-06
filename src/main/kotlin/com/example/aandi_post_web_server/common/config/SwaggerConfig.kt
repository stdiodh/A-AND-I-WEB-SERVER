package com.example.aandi_post_web_server.common.config

import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.method.HandlerMethod

@Configuration
class SwaggerConfig(
    @Value("\${swagger.server.url}") private val serverUrl: String,
) {
    @Bean
    fun openApi(): OpenAPI {
        return OpenAPI()
            .addServersItem(Server().url(serverUrl))
            .components(
                Components().addSecuritySchemes(
                    "bearerAuth",
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"),
                ),
            )
            .info(swaggerInfo())
    }

    private fun swaggerInfo(): Info = Info()
        .title("Report Service API")
        .description("코스/과제 운영 API 문서")
        .version("v1")
        .license(License().name("Proprietary"))

    @Bean
    fun globalErrorResponseCustomizer(): OperationCustomizer {
        return OperationCustomizer { operation: Operation, _: HandlerMethod ->
            val responses = operation.responses ?: ApiResponses().also { operation.responses = it }
            addErrorResponseIfAbsent(responses, "400", "Bad Request")
            addErrorResponseIfAbsent(responses, "401", "Unauthorized")
            addErrorResponseIfAbsent(responses, "403", "Forbidden")
            addErrorResponseIfAbsent(responses, "404", "Not Found")
            addErrorResponseIfAbsent(responses, "500", "Internal Server Error")
            operation
        }
    }

    private fun addErrorResponseIfAbsent(responses: ApiResponses, statusCode: String, description: String) {
        if (responses[statusCode] != null) {
            return
        }
        responses.addApiResponse(
            statusCode,
            ApiResponse()
                .description(description)
                .content(
                    Content().addMediaType(
                        APPLICATION_JSON_VALUE,
                        MediaType().schema(Schema<Any>().`$ref`("#/components/schemas/ApiEnvelope"))
                    )
                )
        )
    }
}
