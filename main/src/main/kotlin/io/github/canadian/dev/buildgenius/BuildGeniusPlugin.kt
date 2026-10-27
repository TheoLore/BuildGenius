package io.github.canadian.dev.buildgenius

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.nullij.androidcodestudio.plugins.api.PluginApi
import io.github.canadian.dev.buildgenius.analyzer.ProjectAnalyzer
import io.github.canadian.dev.buildgenius.fixer.BuildFixer
import io.github.canadian.dev.buildgenius.ui.BuildGeniusScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Build Genius – build doctor for Android Code Studio (ACSIDE).
 *
 * Follows official plugin conventions (ClangLanguageServer / BashLanguageServer):
 *  - static setContext(Context) entry from actions.json
 *  - UI via Handler(Looper.getMainLooper()).post + PluginApi.ui?.showOverlay
 *  - environment via path properties only (avoid getEnvironment AbstractMethodError)
 */
object BuildGeniusPlugin {

    private const val TAG = "BuildGenius"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val analyzer = ProjectAnalyzer()
    private val fixer = BuildFixer()

    /**
     * Primary action: open dashboard.
     * ACSIDE invokes this with a Context (sandboxed PluginContext under the hood).
     */
    @JvmStatic
    fun setContext(context: Context) {
        Log.i(TAG, "setContext – opening dashboard")
        openDashboard()
    }

    /** Optional no-arg entry used by some ACSIDE action wiring. */
    @JvmStatic
    fun getContext() {
        Log.i(TAG, "getContext – opening dashboard")
        openDashboard()
    }

    /** Open UI without needing Context (same as configure in official plugins). */
    @JvmStatic
    fun open() {
        openDashboard()
    }

    /**
     * Quick Fix action – runs off main thread, no overlay required.
     */
    @JvmStatic
    fun quickFix(context: Context? = null) {
        Log.i(TAG, "quickFix")
        Thread({
            try {
                val snapshot = analyzer.analyze()
                val result = fixer.applyAllSafeFixes(snapshot)
                Log.i(TAG, "Quick Fix: ${result.message}")
            } catch (t: Throwable) {
                Log.e(TAG, "Quick Fix failed", t)
            }
        }, "build-genius-quickfix").start()
    }

    private fun openDashboard() {
        // Official pattern: always post to main looper before showOverlay
        mainHandler.post {
            try {
                val ui = PluginApi.ui
                if (ui == null) {
                    Log.e(TAG, "PluginApi.ui is null – open a file in the editor first")
                    return@post
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
            } catch (t: Throwable) {
                Log.e(TAG, "openDashboard failed: ${t.javaClass.name}: ${t.message}", t)
            }
        }
    }
}
