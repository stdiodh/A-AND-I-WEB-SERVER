package com.example.aandi_post_web_server.common.controller

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import java.net.URI

@Controller
class ReportSwaggerUiController {
    @GetMapping("/swagger/report/v1")
    fun reportV1SwaggerUi(): ResponseEntity<Void> =
        ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, "/swagger-ui/index.html?urls.primaryName=report-service-v1")
            .build()

    @GetMapping("/swagger/report/v2")
    fun reportV2SwaggerUi(): ResponseEntity<Void> =
        ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create("/swagger-ui/index.html?urls.primaryName=report-service-v2"))
            .build()

    @GetMapping("/v3/api-docs/assignment-v2")
    fun assignmentV2ApiDocs(): ResponseEntity<Void> =
        ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create("/v3/api-docs/report-v2"))
            .build()
}
