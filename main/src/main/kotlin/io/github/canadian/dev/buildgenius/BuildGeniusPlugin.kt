package io.github.canadian.dev.buildgenius

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.nullij.androidcodestudio.plugins.api.PluginApi
import io.github.canadian.dev.buildgenius.analyzer.ProjectAnalyzer
import io.github.canadian.dev.buildgenius.fixer.BuildFixer
import io.github.canadian.dev.buildgenius.model.ProjectSnapshot
import io.github.canadian.dev.buildgenius.ui.BuildGeniusScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Entry points ACSIDE can call from the home screen or the editor.
 * Every entry gives visible feedback (Toast) because PluginApi.ui is null on the home screen.
 */
object BuildGeniusPlugin {

    private const val TAG = "BuildGenius"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val analyzer = ProjectAnalyzer()
    private val fixer = BuildFixer()

    @JvmStatic
    fun setContext(context: Context) = start(context, openUi = true)

    @JvmStatic
    fun getContext() = start(null, openUi = true)

    @JvmStatic
    fun open(context: Context) = start(context, openUi = true)

    @JvmStatic
    fun quickFix(context: Context) = start(context, openUi = false)

    @JvmStatic
    fun quickFix() = start(null, openUi = false)

    /** Gear / plugin settings. ACSIDE may call either name. */
    @JvmStatic
    fun settings(context: Context) = start(context, openUi = true)

    @JvmStatic
    fun settings() = start(null, openUi = true)

    @JvmStatic
    fun configure(context: Context) = start(context, openUi = true)

    @JvmStatic
    fun configure() = start(null, openUi = true)

    private fun start(context: Context?, openUi: Boolean) {
        toast(context, "Build Genius: reading the last Gradle log…")
        Thread({
            val message = try {
                diagnoseAndFix()
            } catch (t: Throwable) {
                Log.e(TAG, "diagnose failed", t)
                "Build Genius failed: ${t.javaClass.simpleName}: ${t.message ?: "unknown"}"
            }
            mainHandler.post {
                toast(context, message)
                if (openUi) openDashboard()
            }
        }, "build-genius").start()
    }

    private fun diagnoseAndFix(): String {
        val project = resolveProject()
            ?: return "Build Genius: no Gradle project found. Open one first."
        val log = readLatestDaemonLog()
        val snapshot = analyzer.analyze(log).let { snap ->
            if (snap.projectDir == null) snap.copy(projectDir = project) else snap
        }
        val forced = mutableListOf<String>()
        if (log.contains("Library.getSrcJars")) {
            val result = fixer.applyFix(
                io.github.canadian.dev.buildgenius.model.BuildIssue(
                    id = "gradle_srcjars",
                    title = "Library.getSrcJars",
                    description = "",
                    severity = io.github.canadian.dev.buildgenius.model.IssueSeverity.CRITICAL,
                    category = io.github.canadian.dev.buildgenius.model.IssueCategory.GRADLE_PLUGIN,
                    autoFixable = true,
                    fixActionId = "upgrade_gradle_wrapper",
                    confidence = 1f
                ),
                snapshot.copy(projectDir = project)
            )
            forced += result.message
        }
        if (log.contains("Watching the file system is not supported")) {
            val result = fixer.applyFix(
                io.github.canadian.dev.buildgenius.model.BuildIssue(
                    id = "vfs_watch_unsupported",
                    title = "vfs",
                    description = "",
                    severity = io.github.canadian.dev.buildgenius.model.IssueSeverity.LOW,
                    category = io.github.canadian.dev.buildgenius.model.IssueCategory.PROPERTIES,
                    autoFixable = true,
                    fixActionId = "disable_vfs_watch",
                    confidence = 1f
                ),
                snapshot.copy(projectDir = project)
            )
            forced += result.message
        }
        val batch = fixer.applyAllSafeFixes(snapshot.copy(projectDir = project))
        val bits = (forced + batch.message).filter { it.isNotBlank() }.distinct()
        val head = project.name
        return if (bits.isEmpty()) {
            "Build Genius ($head): nothing to change."
        } else {
            "Build Genius ($head): " + bits.joinToString(" ")
        }
    }

    private fun resolveProject(): File? {
        try {
            val open = PluginApi.environment.openProjectDir
            if (open != null && open.exists() && looksLikeGradle(open)) return open
        } catch (t: Throwable) {
            Log.w(TAG, "openProjectDir: ${t.message}")
        }
        val roots = mutableListOf<File>()
        try {
            PluginApi.environment.projectsDir?.let { roots += it }
        } catch (t: Throwable) {
            Log.w(TAG, "projectsDir: ${t.message}")
        }
        try {
            PluginApi.environment.acsRootProjects?.let { roots += it }
        } catch (t: Throwable) {
            Log.w(TAG, "acsRoot: ${t.message}")
        }
        roots += File("/storage/emulated/0/AndroidCSProjects")
        return roots
            .filter { it.isDirectory }
            .flatMap { root -> root.listFiles()?.toList().orEmpty() }
            .filter { it.isDirectory && looksLikeGradle(it) }
            .maxByOrNull { newestStamp(it) }
    }

    private fun looksLikeGradle(dir: File): Boolean {
        return File(dir, "settings.gradle.kts").exists() ||
            File(dir, "settings.gradle").exists() ||
            File(dir, "gradle/wrapper/gradle-wrapper.properties").exists()
    }

    private fun newestStamp(dir: File): Long {
        val candidates = listOf(
            File(dir, "gradle/wrapper/gradle-wrapper.properties"),
            File(dir, "build.gradle.kts"),
            File(dir, "app/build.gradle.kts"),
            dir
        )
        return candidates.maxOf { if (it.exists()) it.lastModified() else 0L }
    }

    private fun readLatestDaemonLog(): String {
        val homes = mutableListOf<File>()
        try {
            PluginApi.environment.homeDir?.let { homes += it }
        } catch (t: Throwable) {
            Log.w(TAG, "homeDir: ${t.message}")
        }
        homes += File("/data/data/com.acside/files/home")
        val logs = homes
            .map { File(it, ".gradle/daemon") }
            .filter { it.isDirectory }
            .flatMap { root ->
                root.listFiles()?.flatMap { ver ->
                    ver.listFiles()?.filter { f ->
                        f.isFile && f.name.startsWith("daemon-") && f.name.endsWith(".out.log")
                    }.orEmpty()
                }.orEmpty()
            }
        val latest = logs.maxByOrNull { it.lastModified() } ?: return ""
        return try {
            val text = latest.readText()
            if (text.length > 180_000) text.takeLast(180_000) else text
        } catch (t: Throwable) {
            Log.w(TAG, "log read ${latest.path}: ${t.message}")
            ""
        }
    }

    private fun toast(context: Context?, message: String) {
        val text = message.take(280)
        Log.i(TAG, text)
        if (context == null) return
        try {
            Toast.makeText(context.applicationContext ?: context, text, Toast.LENGTH_LONG).show()
        } catch (t: Throwable) {
            Log.w(TAG, "toast failed: ${t.message}")
        }
    }

    private fun openDashboard() {
        try {
            val ui = PluginApi.ui ?: run {
                Log.i(TAG, "No editor UI (home screen). Toast already shown.")
                return
            }
            ui.showOverlay { handle ->
                BuildGeniusScreen(
                    onDismiss = { handle.dismiss() },
                    onAnalyze = {
                        withContext(Dispatchers.IO) {
                            val log = readLatestDaemonLog()
                            val snap = analyzer.analyze(log)
                            if (snap.projectDir == null) {
                                val project = resolveProject()
                                if (project != null) snap.copy(projectDir = project) else snap
                            } else snap
                        }
                    },
                    onApplyFix = { issue, snapshot ->
                        withContext(Dispatchers.IO) { fixer.applyFix(issue, snapshot) }
                    },
                    onApplyAllSafe = { snapshot ->
                        withContext(Dispatchers.IO) { fixer.applyAllSafeFixes(snapshot) }
                    }
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "openDashboard failed", t)
        }
    }
}
