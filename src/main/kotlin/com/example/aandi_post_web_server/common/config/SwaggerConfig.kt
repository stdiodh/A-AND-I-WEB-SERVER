package com.example.aandi_post_web_server.common.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig(
    @Value("\${swagger.server.url}") private val serverUrl: String,
) {
    @Bean
    fun openApi(): OpenAPI {
        return OpenAPI()
            .addServersItem(Server().url(serverUrl))
            .components(
                Components()
                    .addSecuritySchemes(
                        "bearerAuth",
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("Authorization 헤더에 `Bearer {accessToken}` 형식으로 전달합니다."),
                    )
                    .addSecuritySchemes(
                        "reportV2Authenticate",
                        SecurityScheme()
                            .type(SecurityScheme.Type.APIKEY)
                            .`in`(SecurityScheme.In.HEADER)
                            .name("Authenticate")
                            .description("A&I v2 통신 규약 헤더입니다. `Bearer {accessToken}` 형식으로 전달합니다."),
                    ),
            )
            .info(
                Info()
                    .title("A&I Report API")
                    .description("버전별 그룹 문서에서 실제 v1/v2 정보를 확인합니다.")
                    .version("docs")
                    .license(License().name("Proprietary"))
            )
    }
}
