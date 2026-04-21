package com.example.aandi_post_web_server.user.infrastructure.event

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class SnsEnvelope(
    @JsonProperty("Type")
    val type: String? = null,
    @JsonProperty("Message")
    val message: String? = null,
)
