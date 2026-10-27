package io.github.canadian.dev.buildgenius

import android.content.Context
import android.util.Log
import com.nullij.androidcodestudio.plugins.api.PluginApi
import io.github.canadian.dev.buildgenius.analyzer.ProjectAnalyzer
import io.github.canadian.dev.buildgenius.fixer.BuildFixer
import io.github.canadian.dev.buildgenius.model.BuildIssue
import io.github.canadian.dev.buildgenius.model.FixResult
import io.github.canadian.dev.buildgenius.model.ProjectSnapshot
import io.github.canadian.dev.buildgenius.ui.BuildGeniusScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Build Genius – Intelligent build doctor for Android Code Studio.
 *
 * Entry points:
 *  - setContext(Context)  → opens the full dashboard overlay
 *  - quickFix()           → runs high-confidence automatic fixes silently
 */
object BuildGeniusPlugin {

    private const val TAG = "BuildGenius"

    private val analyzer = ProjectAnalyzer()
    private val fixer = BuildFixer()

    /**
     * Called by ACSIDE when the user triggers the "Build Genius" action.
     * Shows the full interactive dashboard.
     */
    @JvmStatic
    fun setContext(context: Context) {
        Log.i(TAG, "Opening Build Genius dashboard")
        openDashboard()
    }

    /**
     * Called by ACSIDE for the "Quick Fix Build" action.
     * Applies only high-confidence automatic fixes and logs the result.
     */
    @JvmStatic
    fun quickFix(context: Context? = null) {
        Log.i(TAG, "Running Quick Fix")
        Thread({
            try {
                val snapshot = analyzer.analyze()
                val result = fixer.applyAllSafeFixes(snapshot)
                Log.i(TAG, "Quick Fix result: ${result.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Quick Fix failed", e)
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
                        val result = fixer.applyFix(issue, snapshot)
                        result
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
