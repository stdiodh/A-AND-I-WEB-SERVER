package com.example.aandi_post_web_server.common.security.v2

object V2PathMatcher {
    private val defaultIncludePathPrefixes = listOf("/v2", "/api/v2")
    private val defaultExcludePathPrefixes = listOf(
        "/actuator",
        "/swagger-ui",
        "/v3/api-docs",
        "/favicon.ico",
        "/static",
        "/assets",
        "/webjars",
    )

    fun isV2Path(path: String): Boolean =
        isV2Path(
            path = path,
            includePathPrefixes = defaultIncludePathPrefixes,
            excludePathPrefixes = defaultExcludePathPrefixes,
        )

    fun isV2Path(
        path: String,
        includePathPrefixes: Collection<String>,
        excludePathPrefixes: Collection<String> = defaultExcludePathPrefixes,
    ): Boolean {
        val normalizedPath = normalizePath(path)
        if (matchesAnyPrefix(normalizedPath, excludePathPrefixes)) {
            return false
        }
        return matchesAnyPrefix(normalizedPath, includePathPrefixes)
    }

    private fun matchesAnyPrefix(path: String, prefixes: Collection<String>): Boolean =
        prefixes.asSequence()
            .map(::normalizePath)
            .filter { it.isNotBlank() }
            .any { prefix -> path == prefix || path.startsWith("$prefix/") }

    private fun normalizePath(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isBlank() || trimmed == "/") {
            return "/"
        }
        val withLeadingSlash = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        return withLeadingSlash.trimEnd('/').ifBlank { "/" }
    }
}
