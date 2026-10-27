package io.github.canadian.dev.buildgenius.fixer

import android.util.Log
import io.github.canadian.dev.buildgenius.analyzer.EnvironmentProfiler
import io.github.canadian.dev.buildgenius.model.FileChange
import io.github.canadian.dev.buildgenius.model.FixResult
import io.github.canadian.dev.buildgenius.model.ProjectSnapshot
import java.io.File

/**
 * ACSIDE on-device speed profile (Termux/Bionic).
 * Conservative defaults: avoid configuration-cache (flaky on-device),
 * limited workers, moderate heap.
 */
class TurboOptimizer {

    companion object {
        private const val TAG = "BuildGenius.Turbo"
        private const val HEAP_DEFAULT = "1024m"
        private const val HEAP_HIGH = "1280m"
    }

    fun applyTurbo(snapshot: ProjectSnapshot): FixResult {
        return try {
            applyTurboInternal(snapshot)
        } catch (t: Throwable) {
            Log.e(TAG, "Turbo failed", t)
            FixResult(false, "Turbo failed: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun applyTurboInternal(snapshot: ProjectSnapshot): FixResult {
        val dir = snapshot.projectDir ?: return FixResult(false, "No project open")
        val propsFile = File(dir, "gradle.properties")
        val original = if (propsFile.exists()) {
            try {
                propsFile.readText()
            } catch (t: Throwable) {
                return FixResult(false, "Cannot read gradle.properties")
            }
        } else ""

        val env = EnvironmentProfiler().profile()
        val heap = if (env.installedPlatforms.size >= 3) HEAP_HIGH else HEAP_DEFAULT

        // configuration-cache often breaks first builds / plugin tasks on-device → off by default
        val desired = linkedMapOf(
            "org.gradle.jvmargs" to "-Xmx$heap -XX:+UseParallelGC -Dfile.encoding=UTF-8 -XX:MaxMetaspaceSize=256m",
            "org.gradle.parallel" to "true",
            "org.gradle.workers.max" to "2",
            "org.gradle.caching" to "true",
            "org.gradle.configuration-cache" to "false",
            "org.gradle.daemon" to "true",
            "android.useAndroidX" to "true",
            "android.enableJetifier" to "true",
            "android.nonTransitiveRClass" to "true",
            "android.nonFinalResIds" to "true",
            "kotlin.code.style" to "official",
            "kotlin.incremental" to "true",
            "kotlin.incremental.android" to "true",
            "kotlin.compiler.execution.strategy" to "in-process",
            "org.gradle.console" to "plain"
        )

        val lines = original.lines().toMutableList()
        while (lines.isNotEmpty() && lines.last().isBlank()) lines.removeAt(lines.lastIndex)

        fun upsert(key: String, value: String) {
            val idx = lines.indexOfFirst {
                val t = it.trimStart()
                t.startsWith("$key=") || t.startsWith("$key =")
            }
            val line = "$key=$value"
            if (idx >= 0) lines[idx] = line else lines.add(line)
        }
        desired.forEach { (k, v) -> upsert(k, v) }
        if (lines.none { it.contains("Build Genius Turbo") }) {
            lines.add(0, "# Build Genius Turbo – ACSIDE on-device profile")
        }
        val updated = lines.joinToString("\n").let { if (it.endsWith("\n")) it else "$it\n" }
        propsFile.parentFile?.mkdirs()
        propsFile.writeText(updated)
        Log.i(TAG, "Turbo applied heap=$heap workers=2")
        return FixResult(
            true,
            "Turbo Mode ON (heap=$heap, workers=2). Re-sync to apply.",
            listOf(FileChange(propsFile, original, updated, "Apply ACSIDE Turbo profile"))
        )
    }

    fun missingTurboFlags(snapshot: ProjectSnapshot): List<String> {
        val dir = snapshot.projectDir ?: return listOf("no project")
        val props = File(dir, "gradle.properties")
        if (!props.exists()) return listOf("gradle.properties missing")
        val text = try {
            props.readText()
        } catch (t: Throwable) {
            return listOf("gradle.properties unreadable")
        }
        val required = listOf(
            "org.gradle.parallel",
            "org.gradle.caching",
            "android.nonTransitiveRClass",
            "kotlin.incremental"
        )
        return required.filter { key ->
            !text.lines().any { it.trimStart().startsWith("$key=") }
        }
    }
}
