package com.example.aandi_post_web_server.common.architecture

import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.relativeTo
import kotlin.test.Test
import kotlin.test.assertTrue

class PackageArchitectureRegressionTest {
    private val sourceRoot = Path("src/main/kotlin/com/example/aandi_post_web_server")

    @Test
    fun `main sources do not declare legacy packages`() {
        val violations = scanMainSources { relativePath, line ->
            val packageName = line.removePrefix("package ").trim()
            isForbiddenPackageDeclaration(packageName)?.let { forbidden ->
                "$relativePath -> package $packageName matches legacy package $forbidden"
            }
        }

        assertTrue(
            actual = violations.isEmpty(),
            message = buildFailureMessage("Legacy package declarations detected", violations),
        )
    }

    @Test
    fun `main sources do not import legacy packages`() {
        val violations = scanMainSources { relativePath, line ->
            val importName = line.removePrefix("import ").trim()
            isForbiddenImport(importName)?.let { forbidden ->
                "$relativePath -> import $importName matches legacy package $forbidden"
            }
        }

        assertTrue(
            actual = violations.isEmpty(),
            message = buildFailureMessage("Legacy imports detected", violations),
        )
    }

    private fun scanMainSources(
        onRelevantLine: (relativePath: String, line: String) -> String?,
    ): List<String> {
        if (!sourceRoot.exists()) {
            return emptyList()
        }

        val violations = mutableListOf<String>()

        Files.walk(sourceRoot).use { paths ->
            paths
                .filter { path -> path.toString().endsWith(".kt") }
                .forEach { path ->
                    val relativePath = path.relativeTo(sourceRoot).toString()
                    path.readLines()
                        .asSequence()
                        .map { it.trim() }
                        .filter { it.startsWith("package ") || it.startsWith("import ") }
                        .mapNotNull { line -> onRelevantLine(relativePath, line) }
                        .forEach(violations::add)
                }
        }

        return violations
    }

    private fun isForbiddenPackageDeclaration(packageName: String): String? {
        forbiddenPackagePrefixes.firstOrNull { isSameOrChildPackage(packageName, it) }?.let { return it }
        return forbiddenExactPackages.firstOrNull { packageName == it }
    }

    private fun isForbiddenImport(importName: String): String? {
        return forbiddenPackagePrefixes.firstOrNull { isSameOrChildPackage(importName, it) }
    }

    private fun isSameOrChildPackage(candidate: String, expected: String): Boolean {
        return candidate == expected || candidate.startsWith("$expected.")
    }

    private fun buildFailureMessage(title: String, violations: List<String>): String {
        return buildString {
            appendLine(title)
            violations.forEach { appendLine(it) }
        }
    }

    companion object {
        private val forbiddenPackagePrefixes = listOf(
            "com.example.aandi_post_web_server.assignment.dtos",
            "com.example.aandi_post_web_server.assignment.enum",
            "com.example.aandi_post_web_server.assignment.event",
            "com.example.aandi_post_web_server.assignment.jackson",
            "com.example.aandi_post_web_server.assignment.repository",
            "com.example.aandi_post_web_server.assignment.submission.event",
            "com.example.aandi_post_web_server.assignment.submission.repository",
            "com.example.aandi_post_web_server.assignment.submission.service",
            "com.example.aandi_post_web_server.assignment.v2",
            "com.example.aandi_post_web_server.common.v2",
            "com.example.aandi_post_web_server.course.controller",
            "com.example.aandi_post_web_server.course.dtos",
            "com.example.aandi_post_web_server.course.enum",
            "com.example.aandi_post_web_server.course.repository",
            "com.example.aandi_post_web_server.course.service",
            "com.example.aandi_post_web_server.course.v2",
            "com.example.aandi_post_web_server.report.v2",
            "com.example.aandi_post_web_server.user.config",
            "com.example.aandi_post_web_server.user.event",
            "com.example.aandi_post_web_server.user.repository",
            "com.example.aandi_post_web_server.user.service",
        )

        private val forbiddenExactPackages = setOf(
            "com.example.aandi_post_web_server.assignment.domain",
            "com.example.aandi_post_web_server.course.domain",
        )
    }
}
