package io.github.canadian.dev.buildgenius.fixer

import android.util.Log
import io.github.canadian.dev.buildgenius.analyzer.EnvironmentProfiler
import io.github.canadian.dev.buildgenius.model.*
import java.io.File

class BuildFixer {

    companion object {
        private const val TAG = "BuildGenius.Fixer"
        private const val TARGET_AGP = "8.7.3"
        private const val TARGET_KOTLIN = "2.0.21"
        private const val TARGET_COMPILE_SDK = 35
    }

    fun applyFix(issue: BuildIssue, snapshot: ProjectSnapshot): FixResult {
        if (!issue.autoFixable || issue.fixActionId.isNullOrBlank()) {
            return FixResult(false, "This issue cannot be fixed automatically.")
        }
        return try {
            val action = issue.fixActionId!!
            when {
                action == "upgrade_agp" -> upgradeAgp(snapshot)
                action == "upgrade_kotlin" -> upgradeKotlin(snapshot)
                action == "raise_compile_sdk" -> raiseCompileSdk(snapshot)
                action == "enable_androidx" -> enableAndroidX(snapshot)
                action == "increase_heap" -> increaseHeap(snapshot)
                action == "create_gradle_props" -> createGradleProperties(snapshot)
                action == "align_compile_sdk_to_env" -> alignCompileSdkToEnv(snapshot)
                action == "align_sdk_dir" -> alignSdkDir(snapshot)
                action == "apply_turbo" -> TurboOptimizer().applyTurbo(snapshot)
                action.startsWith("add_dependency:") ->
                    addDependency(snapshot, action.removePrefix("add_dependency:"))
                else -> FixResult(false, "Unknown fix action: $action")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Fix failed: ${t.javaClass.simpleName}", t)
            FixResult(false, "Fix failed: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    fun applyAllSafeFixes(snapshot: ProjectSnapshot): FixResult {
        return try {
            val autoFixable = snapshot.issues.filter { it.autoFixable && it.confidence >= 0.8f }
            if (autoFixable.isEmpty()) {
                return FixResult(
                    true,
                    "No high-confidence automatic fixes available.",
                    remainingIssues = snapshot.issues
                )
            }
            val allChanges = mutableListOf<FileChange>()
            var applied = 0
            for (issue in autoFixable) {
                val result = applyFix(issue, snapshot)
                if (result.success) {
                    applied++
                    allChanges += result.appliedChanges
                }
            }
            if (allChanges.isNotEmpty()) {
                FixResult(
                    true,
                    "Applied $applied fix(es), ${allChanges.size} file change(s). Re-sync the project.",
                    appliedChanges = allChanges
                )
            } else {
                FixResult(false, "Could not apply any fixes automatically.")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "applyAllSafeFixes failed", t)
            FixResult(false, "Batch fix failed: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun upgradeAgp(snapshot: ProjectSnapshot): FixResult {
        val root = findRootBuild(snapshot) ?: return FixResult(false, "Root build file not found")
        val original = safeRead(root) ?: return FixResult(false, "Cannot read root build file")
        var updated = original
        updated = updated.replace(
            Regex("""(com\.android\.tools\.build:gradle:)([0-9.]+)"""),
            "$1$TARGET_AGP"
        )
        updated = updated.replace(
            Regex("""(id\\s*\\(\\s*[\"']com\\.android\\.(?:application|library)[\"']\\s*\\)\\s*version\\s*[\"'])([0-9.]+)([\"'])"""),
            "$1$TARGET_AGP$3"
        )
        updated = updated.replace(
            Regex("""(agp\\s*=\\s*[\"'])([0-9.]+)([\"'])"""),
            "$1$TARGET_AGP$3"
        )
        if (updated == original) return FixResult(false, "Could not locate AGP version")
        safeWrite(root, updated)
        return FixResult(true, "Upgraded AGP to $TARGET_AGP", listOf(FileChange(root, original, updated, "Upgrade AGP")))
    }

    private fun upgradeKotlin(snapshot: ProjectSnapshot): FixResult {
        val root = findRootBuild(snapshot) ?: return FixResult(false, "Root build file not found")
        val original = safeRead(root) ?: return FixResult(false, "Cannot read root build file")
        var updated = original
        updated = updated.replace(
            Regex("""(org\.jetbrains\.kotlin:kotlin-gradle-plugin:)([0-9.]+)"""),
            "$1$TARGET_KOTLIN"
        )
        updated = updated.replace(
            Regex("""(id\\s*\\(\\s*[\"']org\\.jetbrains\\.kotlin\\.(?:android|jvm)[\"']\\s*\\)\\s*version\\s*[\"'])([0-9.]+)([\"'])"""),
            "$1$TARGET_KOTLIN$3"
        )
        updated = updated.replace(
            Regex("""(kotlin\\s*=\\s*[\"'])([0-9.]+)([\"'])"""),
            "$1$TARGET_KOTLIN$3"
        )
        if (updated == original) return FixResult(false, "Could not locate Kotlin version")
        safeWrite(root, updated)
        return FixResult(true, "Upgraded Kotlin to $TARGET_KOTLIN", listOf(FileChange(root, original, updated, "Upgrade Kotlin")))
    }

    private fun raiseCompileSdk(snapshot: ProjectSnapshot): FixResult {
        val appBuild = findAppBuild(snapshot) ?: return FixResult(false, "App build file not found")
        val original = safeRead(appBuild) ?: return FixResult(false, "Cannot read app build")
        var updated = original
        updated = updated.replace(Regex("""(compileSdk\\s*=?\\s*)([0-9]+)"""), "$1$TARGET_COMPILE_SDK")
        updated = updated.replace(Regex("""(compileSdkVersion\\s+)([0-9]+)"""), "$1$TARGET_COMPILE_SDK")
        if (updated == original) return FixResult(false, "Could not locate compileSdk")
        safeWrite(appBuild, updated)
        return FixResult(true, "Raised compileSdk to $TARGET_COMPILE_SDK", listOf(FileChange(appBuild, original, updated, "compileSdk")))
    }

    private fun enableAndroidX(snapshot: ProjectSnapshot): FixResult {
        val dir = snapshot.projectDir ?: return FixResult(false, "No project")
        val props = File(dir, "gradle.properties")
        val original = if (props.exists()) (safeRead(props) ?: "") else ""
        val lines = original.lines().toMutableList()
        fun ensure(key: String, value: String) {
            val idx = lines.indexOfFirst {
                val t = it.trimStart()
                t.startsWith("$key=") || t.startsWith("$key =")
            }
            if (idx >= 0) lines[idx] = "$key=$value" else lines.add("$key=$value")
        }
        ensure("android.useAndroidX", "true")
        ensure("android.enableJetifier", "true")
        val updated = lines.joinToString("\n").let { if (it.endsWith("\n")) it else "$it\n" }
        props.parentFile?.mkdirs()
        safeWrite(props, updated)
        return FixResult(true, "Enabled AndroidX + Jetifier", listOf(FileChange(props, original, updated, "AndroidX")))
    }

    private fun increaseHeap(snapshot: ProjectSnapshot): FixResult {
        val dir = snapshot.projectDir ?: return FixResult(false, "No project")
        val props = File(dir, "gradle.properties")
        if (!props.exists()) return FixResult(false, "gradle.properties not found")
        val original = safeRead(props) ?: return FixResult(false, "Cannot read properties")
        val updated = original.replace(
            Regex("""(org\.gradle\.jvmargs\\s*=\\s*.*?-Xmx)([0-9]+)([mM])"""),
            "$11024$3"
        )
        if (updated == original) return FixResult(false, "Could not update jvmargs")
        safeWrite(props, updated)
        return FixResult(true, "Increased Gradle heap to 1024m", listOf(FileChange(props, original, updated, "Heap")))
    }

    private fun createGradleProperties(snapshot: ProjectSnapshot): FixResult {
        val dir = snapshot.projectDir ?: return FixResult(false, "No project")
        val props = File(dir, "gradle.properties")
        if (props.exists()) return FixResult(false, "gradle.properties already exists")
        val content = """
            # Generated by Build Genius
            org.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8
            org.gradle.parallel=true
            org.gradle.caching=true
            android.useAndroidX=true
            android.enableJetifier=true
            kotlin.code.style=official
        """.trimIndent() + "\n"
        safeWrite(props, content)
        return FixResult(true, "Created gradle.properties", listOf(FileChange(props, "", content, "Create props")))
    }

    private fun addDependency(snapshot: ProjectSnapshot, coordinate: String): FixResult {
        if (coordinate.isBlank() || !coordinate.contains(':')) {
            return FixResult(false, "Invalid dependency coordinate")
        }
        val appBuild = findAppBuild(snapshot) ?: findRootBuild(snapshot)
            ?: return FixResult(false, "No build.gradle found")
        val original = safeRead(appBuild) ?: return FixResult(false, "Cannot read build file")
        if (original.contains(coordinate)) {
            return FixResult(true, "Dependency already present: $coordinate")
        }
        val isKts = appBuild.name.endsWith(".kts")
        val line = if (isKts) "    implementation(\"$coordinate\")" else "    implementation '$coordinate'"
        val match = Regex("""dependencies\\s*\\{""").find(original)
        val updated = if (match != null) {
            val insertAt = match.range.last + 1
            original.substring(0, insertAt) + "\n" + line + original.substring(insertAt)
        } else {
            original.trimEnd() + "\n\ndependencies {\n$line\n}\n"
        }
        safeWrite(appBuild, updated)
        return FixResult(true, "Added dependency: $coordinate", listOf(FileChange(appBuild, original, updated, "Add $coordinate")))
    }

    private fun alignCompileSdkToEnv(snapshot: ProjectSnapshot): FixResult {
        val profile = EnvironmentProfiler().profile()
        val targetApi = profile.preferredCompileSdk
            ?: return FixResult(false, "No Android platforms installed in ACSIDE")
        val appBuild = findAppBuild(snapshot) ?: return FixResult(false, "App build not found")
        val original = safeRead(appBuild) ?: return FixResult(false, "Cannot read app build")
        var updated = original
        updated = updated.replace(Regex("""(compileSdk\\s*=?\\s*)([0-9]+)"""), "$1$targetApi")
        updated = updated.replace(Regex("""(compileSdkVersion\\s+)([0-9]+)"""), "$1$targetApi")
        updated = updated.replace(Regex("""(targetSdk\\s*=?\\s*)([0-9]+)"""), "$1$targetApi")
        updated = updated.replace(Regex("""(targetSdkVersion\\s+)([0-9]+)"""), "$1$targetApi")
        if (updated == original) return FixResult(false, "Could not update compileSdk")
        safeWrite(appBuild, updated)
        return FixResult(
            true,
            "Aligned compileSdk/targetSdk to API $targetApi",
            listOf(FileChange(appBuild, original, updated, "Align SDK"))
        )
    }

    private fun alignSdkDir(snapshot: ProjectSnapshot): FixResult {
        val profile = EnvironmentProfiler().profile()
        val sdkPath = profile.androidSdkDir?.absolutePath
            ?: return FixResult(false, "SDK path unknown in ACSIDE environment")
        val dir = snapshot.projectDir ?: return FixResult(false, "No project")
        val props = File(dir, "local.properties")
        val original = if (props.exists()) (safeRead(props) ?: "") else ""
        val lines = original.lines().toMutableList()
        val value = sdkPath.replace("\\", "/")
        val idx = lines.indexOfFirst {
            val t = it.trimStart()
            t.startsWith("sdk.dir=") || t.startsWith("sdk.dir =")
        }
        if (idx >= 0) lines[idx] = "sdk.dir=$value" else lines.add("sdk.dir=$value")
        val updated = lines.joinToString("\n").let { if (it.endsWith("\n")) it else "$it\n" }
        safeWrite(props, updated)
        return FixResult(true, "Set sdk.dir to $value", listOf(FileChange(props, original, updated, "sdk.dir")))
    }

    private fun findRootBuild(snapshot: ProjectSnapshot): File? {
        val dir = snapshot.projectDir ?: return null
        return listOf("build.gradle.kts", "build.gradle")
            .map { File(dir, it) }
            .firstOrNull { it.exists() && it.canRead() }
    }

    private fun findAppBuild(snapshot: ProjectSnapshot): File? {
        val dir = snapshot.projectDir ?: return null
        return listOf(
            "app/build.gradle.kts",
            "app/build.gradle",
            "android/app/build.gradle.kts",
            "android/app/build.gradle"
        ).map { File(dir, it) }.firstOrNull { it.exists() && it.canRead() }
    }

    private fun safeRead(file: File): String? {
        return try {
            if (!file.exists() || !file.canRead()) null else file.readText()
        } catch (t: Throwable) {
            Log.w(TAG, "read failed ${file.path}: ${t.message}")
            null
        }
    }

    private fun safeWrite(file: File, content: String) {
        file.parentFile?.mkdirs()
        file.writeText(content)
    }
}
