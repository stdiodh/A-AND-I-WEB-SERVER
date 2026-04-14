package com.example.aandi_post_web_server.common.v2.security

object V2PathMatcher {
    fun isV2Path(path: String): Boolean =
        path == "/v2" || path.startsWith("/v2/")
}
