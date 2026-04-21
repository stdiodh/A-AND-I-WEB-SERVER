package com.example.aandi_post_web_server.common.architecture

import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.relativeTo
import kotlin.test.Test
import kotlin.test.assertTrue

class LayerDependencyRegressionTest {
    private val sourceRoot = Path("src/main/kotlin/com/example/aandi_post_web_server")
    private val basePackage = "com.example.aandi_post_web_server"

    @Test
    fun `feature application does not depend on api controllers`() {
        val violations = scanMainSourceImports { packageName, importName, relativePath ->
            val feature = featureName(packageName) ?: return@scanMainSourceImports null
            if (!packageName.contains(".application.")) return@scanMainSourceImports null
            if (!importName.startsWith("$basePackage.$feature.api.")) return@scanMainSourceImports null
            if (!importName.contains(".controller.")) return@scanMainSourceImports null

            "$relativePath -> application depends on api: $importName"
        }

        assertNoViolations("Application layer dependencies are broken", violations)
    }

    @Test
    fun `feature infrastructure does not depend on api controllers`() {
        val violations = scanMainSourceImports { packageName, importName, relativePath ->
            val feature = featureName(packageName) ?: return@scanMainSourceImports null
            if (!packageName.contains(".infrastructure.")) return@scanMainSourceImports null
            if (!importName.startsWith("$basePackage.$feature.api.")) return@scanMainSourceImports null
            if (!importName.contains(".controller.")) return@scanMainSourceImports null

            "$relativePath -> infrastructure depends on api: $importName"
        }

        assertNoViolations("Infrastructure layer dependencies are broken", violations)
    }

    @Test
    fun `common runtime packages do not depend on feature packages`() {
        val featurePrefixes = listOf("assignment", "course", "report", "user")
            .map { "$basePackage.$it." }

        val violations = scanMainSourceImports { packageName, importName, relativePath ->
            if (!packageName.startsWith("$basePackage.common.")) return@scanMainSourceImports null
            if (isSameOrChildPackage(packageName, "$basePackage.common.openapi")) return@scanMainSourceImports null
            if (isSameOrChildPackage(packageName, "$basePackage.common.config")) return@scanMainSourceImports null

            val forbiddenFeature = featurePrefixes.firstOrNull { importName.startsWith(it) } ?: return@scanMainSourceImports null
            "$relativePath -> common depends on feature package: $forbiddenFeature"
        }

        assertNoViolations("Common runtime package dependencies are broken", violations)
    }

    private fun scanMainSourceImports(
        onImport: (packageName: String, importName: String, relativePath: String) -> String?,
    ): List<String> {
        if (!sourceRoot.exists()) return emptyList()

        val violations = mutableListOf<String>()
        Files.walk(sourceRoot).use { paths ->
            paths
                .filter { it.toString().endsWith(".kt") }
                .forEach { path ->
                    val lines = path.readLines().map { it.trim() }
                    val packageName = lines.firstOrNull { it.startsWith("package ") }
                        ?.removePrefix("package ")
                        ?.trim()
                        ?: return@forEach
                    val relativePath = path.relativeTo(sourceRoot).toString()

                    lines.asSequence()
                        .filter { it.startsWith("import ") }
                        .map { it.removePrefix("import ").trim() }
                        .mapNotNull { importName -> onImport(packageName, importName, relativePath) }
                        .forEach(violations::add)
                }
        }
        return violations
    }

    private fun featureName(packageName: String): String? {
        return packageName.removePrefix("$basePackage.")
            .substringBefore('.')
            .takeIf { it in setOf("assignment", "course", "report", "user") }
    }

    private fun isSameOrChildPackage(packageName: String, expected: String): Boolean {
        return packageName == expected || packageName.startsWith("$expected.")
    }

    private fun assertNoViolations(title: String, violations: List<String>) {
        assertTrue(
            actual = violations.isEmpty(),
            message = buildString {
                appendLine(title)
                violations.forEach { appendLine(it) }
            },
        )
    }
}
