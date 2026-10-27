package io.github.canadian.dev.buildgenius

import android.content.Context
import android.util.Log
import com.nullij.androidcodestudio.plugins.api.PluginApi
import io.github.canadian.dev.buildgenius.analyzer.ProjectAnalyzer
import io.github.canadian.dev.buildgenius.fixer.BuildFixer
import io.github.canadian.dev.buildgenius.ui.BuildGeniusScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Build Genius – Intelligent build doctor for Android Code Studio.
 */
object BuildGeniusPlugin {

    private const val TAG = "BuildGenius"

    private val analyzer = ProjectAnalyzer()
    private val fixer = BuildFixer()

    @JvmStatic
    fun setContext(context: Context) {
        Log.i(TAG, "Opening Build Genius dashboard")
        try {
            openDashboard()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to open dashboard: ${t.javaClass.name}: ${t.message}", t)
        }
    }

    @JvmStatic
    fun quickFix(context: Context? = null) {
        Log.i(TAG, "Running Quick Fix")
        Thread({
            try {
                val snapshot = analyzer.analyze()
                val result = fixer.applyAllSafeFixes(snapshot)
                Log.i(TAG, "Quick Fix result: ${result.message}")
            } catch (t: Throwable) {
                Log.e(TAG, "Quick Fix failed", t)
            }
        }, "build-genius-quickfix").start()
    }

    private fun openDashboard() {
        val ui = PluginApi.ui
        if (ui == null) {
            Log.e(TAG, "PluginApi.ui is null – not inside EditorActivity")
            return
        }

        ui.showOverlay { handle ->
            BuildGeniusScreen(
                onDismiss = { handle.dismiss() },
                onAnalyze = {
                    withContext(Dispatchers.IO) {
                        analyzer.analyze()
                    }
                },
                onApplyFix = { issue, snapshot ->
                    withContext(Dispatchers.IO) {
                        fixer.applyFix(issue, snapshot)
                    }
                },
                onApplyAllSafe = { snapshot ->
                    withContext(Dispatchers.IO) {
                        fixer.applyAllSafeFixes(snapshot)
                    }
                }
            )
        }
    }
}
