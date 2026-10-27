package io.github.canadian.dev.buildgenius.analyzer

import android.util.Log
import com.nullij.androidcodestudio.plugins.api.PluginApi
import io.github.canadian.dev.buildgenius.fixer.TurboOptimizer
import io.github.canadian.dev.buildgenius.model.*
import java.io.File
import java.util.regex.Pattern

class ProjectAnalyzer {

    companion object {
        private const val TAG = "BuildGenius.Analyzer"
        private const val MIN_RECOMMENDED_AGP_MAJOR = 8
    }

    private val errorParser = ErrorParser()

    fun analyze(buildLog: String? = null): ProjectSnapshot {
        val env = PluginApi.environment
        val projectDir = env.openProjectDir
        if (projectDir == null || !projectDir.exists()) {
            return emptySnapshot()
        }

        val rootBuildGradle = findBuildFile(projectDir, "build.gradle")
        val rootBuildKts = findBuildFile(projectDir, "build.gradle.kts")
        val settingsGradle = findBuildFile(projectDir, "settings.gradle")
        val settingsKts = findBuildFile(projectDir, "settings.gradle.kts")
        val gradleProps = File(projectDir, "gradle.properties")
        val localProps = File(projectDir, "local.properties")

        val hasKts = rootBuildKts != null || settingsKts != null
        val isGradle = rootBuildGradle != null || rootBuildKts != null || settingsGradle != null || settingsKts != null
        val isFlutter = File(projectDir, "pubspec.yaml").exists()
        val isAndroid = isGradle && (hasAndroidPlugin(rootBuildGradle) || hasAndroidPlugin(rootBuildKts) ||
                File(projectDir, "app").exists() || File(projectDir, "android").exists())

        val agpVersion = extractAgpVersion(rootBuildGradle) ?: extractAgpVersion(rootBuildKts)
        val kotlinVersion = extractKotlinVersion(rootBuildGradle) ?: extractKotlinVersion(rootBuildKts)
        val gradleVersion = extractGradleWrapperVersion(projectDir)
        val appBuild = findAppBuildFile(projectDir)
        val compileSdk = extractInt(appBuild, "compileSdk") ?: extractInt(appBuild, "compileSdkVersion")
        val minSdk = extractInt(appBuild, "minSdk") ?: extractInt(appBuild, "minSdkVersion")
        val targetSdk = extractInt(appBuild, "targetSdk") ?: extractInt(appBuild, "targetSdkVersion")
        val jdkVersion = extractJdkFromProperties(gradleProps) ?: "unknown"
        val modules = discoverModules(settingsGradle ?: settingsKts, projectDir)

        val envProfile = EnvironmentProfiler().profile()
        val issues = mutableListOf<BuildIssue>()

        if (envProfile.initialized) {
            if (compileSdk != null && envProfile.highestPlatformApi != null) {
                if (compileSdk > envProfile.highestPlatformApi!!) {
                    issues += BuildIssue(
                        id = "compile_sdk_above_env",
                        title = "compileSdk ($compileSdk) above installed platforms",
                        description = "Highest installed API: ${envProfile.highestPlatformApi}",
                        severity = IssueSeverity.CRITICAL,
                        category = IssueCategory.SDK_CONFIG,
                        filePath = appBuild?.absolutePath,
                        suggestedFix = "Lower to ${envProfile.highestPlatformApi}",
                        autoFixable = true,
                        fixActionId = "align_compile_sdk_to_env",
                        confidence = 0.97f
                    )
                }
            }
            if (isAndroid && envProfile.androidSdkDir != null) {
                val expected = envProfile.androidSdkDir!!.absolutePath
                if (!localProps.exists()) {
                    issues += BuildIssue(
                        id = "missing_local_props_sdk",
                        title = "local.properties missing",
                        description = "Create with sdk.dir=$expected",
                        severity = IssueSeverity.HIGH,
                        category = IssueCategory.SDK_CONFIG,
                        filePath = localProps.absolutePath,
                        autoFixable = true,
                        fixActionId = "align_sdk_dir",
                        confidence = 0.95f
                    )
                }
            }
            if (isFlutter && !envProfile.hasFlutter) {
                issues += BuildIssue(
                    id = "flutter_sdk_missing_env",
                    title = "Flutter SDK not found in ACSIDE",
                    description = "Install Flutter via ACSIDE SDK Manager",
                    severity = IssueSeverity.CRITICAL,
                    category = IssueCategory.SDK_CONFIG,
                    autoFixable = false,
                    confidence = 0.95f
                )
            }
        }

        if (agpVersion != null) {
            val major = agpVersion.substringBefore('.').toIntOrNull() ?: 0
            if (major < MIN_RECOMMENDED_AGP_MAJOR) {
                issues += BuildIssue(
                    id = "agp_too_old",
                    title = "AGP outdated ($agpVersion)",
                    description = "Upgrade to AGP 8.x+",
                    severity = IssueSeverity.CRITICAL,
                    category = IssueCategory.AGP,
                    filePath = (rootBuildKts ?: rootBuildGradle)?.absolutePath,
                    autoFixable = true,
                    fixActionId = "upgrade_agp",
                    confidence = 0.95f
                )
            }
        }

        if (isGradle) {
            val snap = ProjectSnapshot(
                projectDir, isGradle, isAndroid, isFlutter, agpVersion, kotlinVersion,
                compileSdk, minSdk, targetSdk, jdkVersion, gradleVersion, hasKts, modules, emptyList()
            )
            val missing = TurboOptimizer().missingTurboFlags(snap)
            if (missing.isNotEmpty()) {
                issues += BuildIssue(
                    id = "turbo_not_applied",
                    title = "Not turbo-optimized for ACSIDE",
                    description = "Missing: ${missing.joinToString()}",
                    severity = IssueSeverity.MEDIUM,
                    category = IssueCategory.PROPERTIES,
                    filePath = File(projectDir, "gradle.properties").absolutePath,
                    autoFixable = true,
                    fixActionId = "apply_turbo",
                    confidence = 0.93f
                )
            }
        }

        if (!buildLog.isNullOrBlank()) {
            val existingIds = issues.map { it.id }.toSet()
            errorParser.parse(buildLog).forEach { li ->
                if (li.id !in existingIds) issues += li
            }
        }

        val sorted = issues.sortedBy {
            when (it.severity) {
                IssueSeverity.CRITICAL -> 0
                IssueSeverity.HIGH -> 1
                IssueSeverity.MEDIUM -> 2
                IssueSeverity.LOW -> 3
                IssueSeverity.INFO -> 4
            }
        }

        return ProjectSnapshot(
            projectDir, isGradle, isAndroid, isFlutter, agpVersion, kotlinVersion,
            compileSdk, minSdk, targetSdk, jdkVersion, gradleVersion, hasKts, modules, sorted
        )
    }

    private fun emptySnapshot() = ProjectSnapshot(
        null, false, false, false, null, null, null, null, null, null, null, false, emptyList(),
        listOf(
            BuildIssue(
                id = "no_project",
                title = "No project open",
                description = "Open a project first",
                severity = IssueSeverity.INFO,
                category = IssueCategory.OTHER,
                autoFixable = false
            )
        )
    )

    private fun findBuildFile(dir: File, name: String): File? =
        File(dir, name).takeIf { it.exists() }

    private fun findAppBuildFile(projectDir: File): File? =
        listOf("app/build.gradle.kts", "app/build.gradle", "android/app/build.gradle.kts", "android/app/build.gradle")
            .map { File(projectDir, it) }.firstOrNull { it.exists() }

    private fun hasAndroidPlugin(file: File?): Boolean {
        if (file == null) return false
        val c = file.readText()
        return c.contains("com.android.application") || c.contains("com.android.library") ||
                c.contains("com.android.tools.build:gradle")
    }

    private fun extractAgpVersion(file: File?): String? {
        if (file == null) return null
        val c = file.readText()
        listOf(
            Pattern.compile("com\\.android\\.tools\\.build:gradle:([0-9.]+)"),
            Pattern.compile("agp\\s*=\\s*[\"']([0-9.]+)[\"']")
        ).forEach { p ->
            val m = p.matcher(c)
            if (m.find()) return m.group(1)
        }
        return null
    }

    private fun extractKotlinVersion(file: File?): String? {
        if (file == null) return null
        val c = file.readText()
        listOf(
            Pattern.compile("org\\.jetbrains\\.kotlin:kotlin-gradle-plugin:([0-9.]+)"),
            Pattern.compile("kotlin\\s*=\\s*[\"']([0-9.]+)[\"']")
        ).forEach { p ->
            val m = p.matcher(c)
            if (m.find()) return m.group(1)
        }
        return null
    }

    private fun extractGradleWrapperVersion(projectDir: File): String? {
        val props = File(projectDir, "gradle/wrapper/gradle-wrapper.properties")
        if (!props.exists()) return null
        val m = Pattern.compile("gradle-([0-9.]+)-").matcher(props.readText())
        return if (m.find()) m.group(1) else null
    }

    private fun extractInt(file: File?, key: String): Int? {
        if (file == null) return null
        val m = Pattern.compile("$key\\s*=?\\s*([0-9]+)").matcher(file.readText())
        return if (m.find()) m.group(1).toIntOrNull() else null
    }

    private fun extractJdkFromProperties(file: File): String? {
        if (!file.exists()) return null
        val m = Pattern.compile("org\\.gradle\\.java\\.home\\s*=\\s*(.+)").matcher(file.readText())
        return if (m.find()) m.group(1).trim() else null
    }

    private fun discoverModules(settingsFile: File?, projectDir: File): List<String> {
        val modules = mutableListOf<String>()
        if (settingsFile != null && settingsFile.exists()) {
            val m = Pattern.compile("include\\s*\\(?\\s*['\"]:([^'\"]+)['\"]").matcher(settingsFile.readText())
            while (m.find()) modules += m.group(1)
        }
        if (modules.isEmpty()) {
            listOf("app", "android", "lib", "core").forEach {
                if (File(projectDir, it).isDirectory) modules += it
            }
        }
        return modules.distinct()
    }
}
