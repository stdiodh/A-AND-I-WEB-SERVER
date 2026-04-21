package com.example.aandi_post_web_server.common.api.header

data class V2HeaderContext(
    val deviceOS: String,
    val authenticate: String,
    val timestamp: String,
    val salt: String?,
) {
    companion object {
        const val ATTRIBUTE_NAME: String = "aandi.v2.headerContext"
    }
}
