package com.example.aandi_post_web_server.common.security.v2

object V2PathMatcher {
    fun isV2Path(path: String): Boolean =
        path == "/v2" || path.startsWith("/v2/")
}
