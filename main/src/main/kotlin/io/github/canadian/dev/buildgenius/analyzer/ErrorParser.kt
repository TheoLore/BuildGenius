package io.github.canadian.dev.buildgenius.analyzer

import android.util.Log
import io.github.canadian.dev.buildgenius.model.BuildIssue
import io.github.canadian.dev.buildgenius.model.IssueCategory
import io.github.canadian.dev.buildgenius.model.IssueSeverity
import java.util.regex.Pattern

class ErrorParser {

    companion object {
        private const val TAG = "BuildGenius.Parser"
    }

    fun parse(log: String): List<BuildIssue> {
        if (log.isBlank()) return emptyList()
        return try {
            parseInternal(log)
        } catch (t: Throwable) {
            Log.e(TAG, "parse failed: ${t.message}", t)
            emptyList()
        }
    }

    private fun parseInternal(log: String): List<BuildIssue> {
        val issues = mutableListOf<BuildIssue>()
        val seen = mutableSetOf<String>()
        fun add(issue: BuildIssue) {
            if (issue.id !in seen) {
                seen += issue.id
                issues += issue
            }
        }

        val missingDep = Pattern.compile(
            "(?i)(?:Could not find|Failed to resolve|Could not resolve)[:\\s]+([a-zA-Z0-9._-]+:[a-zA-Z0-9._-]+(?::[0-9.A-Za-z+-]+)?)"
        )
        val m1 = missingDep.matcher(log)
        while (m1.find()) {
            val coord = m1.group(1).trim()
            add(
                BuildIssue(
                    id = "missing_dep_${coord.hashCode()}",
                    title = "Missing dependency: $coord",
                    description = "Gradle cannot resolve \"$coord\".",
                    severity = IssueSeverity.CRITICAL,
                    category = IssueCategory.DEPENDENCY,
                    suggestedFix = "Add implementation(\"$coord\")",
                    autoFixable = true,
                    fixActionId = "add_dependency:$coord",
                    confidence = 0.92f
                )
            )
        }

        val unresolved = Pattern.compile(
            "(?i)(?:Unresolved reference|cannot find symbol(?: class| method| variable)?)\\s*:?\\s*([A-Za-z_][A-Za-z0-9_.]*)"
        )
        val m2 = unresolved.matcher(log)
        while (m2.find()) {
            val symbol = m2.group(1)
            val suggestion = DependencyCatalog.suggestForSymbol(symbol)
            if (suggestion != null) {
                add(
                    BuildIssue(
                        id = "unresolved_$symbol",
                        title = "Unresolved reference: $symbol",
                        description = "Likely missing library: ${suggestion.reason}",
                        severity = IssueSeverity.CRITICAL,
                        category = IssueCategory.DEPENDENCY,
                        suggestedFix = "Add ${suggestion.coordinate}",
                        autoFixable = true,
                        fixActionId = "add_dependency:${suggestion.coordinate}",
                        confidence = suggestion.confidence
                    )
                )
            }
        }


        if (log.contains("Library.getSrcJars") || log.contains("Unsupported method: Library.getSrcJars")) {
            add(
                BuildIssue(
                    id = "gradle_srcjars",
                    title = "ACSIDE sync: Library.getSrcJars()",
                    description = "Project Gradle is older than the IDE tooling API. Wrapper must be 8.11.1+.",
                    severity = IssueSeverity.CRITICAL,
                    category = IssueCategory.GRADLE_PLUGIN,
                    suggestedFix = "Set Gradle wrapper to 8.11.1",
                    autoFixable = true,
                    fixActionId = "upgrade_gradle_wrapper",
                    confidence = 0.99f
                )
            )
        }

        if (log.contains("Watching the file system is not supported", true)) {
            add(
                BuildIssue(
                    id = "vfs_watch_unsupported",
                    title = "File watching unsupported on device",
                    description = "org.gradle.vfs.watch should be false on ACSIDE.",
                    severity = IssueSeverity.LOW,
                    category = IssueCategory.PROPERTIES,
                    autoFixable = true,
                    fixActionId = "disable_vfs_watch",
                    confidence = 0.9f
                )
            )
        }

        if (log.contains("Java heap space", true) || log.contains("OutOfMemoryError", true)) {
            add(
                BuildIssue(
                    id = "oom",
                    title = "Gradle ran out of memory",
                    description = "Increase org.gradle.jvmargs heap.",
                    severity = IssueSeverity.HIGH,
                    category = IssueCategory.PROPERTIES,
                    suggestedFix = "Increase heap to 1024m+",
                    autoFixable = true,
                    fixActionId = "increase_heap",
                    confidence = 0.95f
                )
            )
        }

        if (log.contains("Manifest merger failed", true) || log.contains("Duplicate class", true)) {
            add(
                BuildIssue(
                    id = "androidx_conflict",
                    title = "Support / AndroidX conflict",
                    description = "Enable AndroidX and Jetifier.",
                    severity = IssueSeverity.CRITICAL,
                    category = IssueCategory.DEPENDENCY,
                    autoFixable = true,
                    fixActionId = "enable_androidx",
                    confidence = 0.9f
                )
            )
        }

        return issues.sortedBy {
            when (it.severity) {
                IssueSeverity.CRITICAL -> 0
                IssueSeverity.HIGH -> 1
                IssueSeverity.MEDIUM -> 2
                IssueSeverity.LOW -> 3
                IssueSeverity.INFO -> 4
            }
        }
    }
}

object DependencyCatalog {
    data class Suggestion(val coordinate: String, val reason: String, val confidence: Float = 0.9f)

    private val map = mapOf(
        "ViewModel" to Suggestion("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7", "Lifecycle ViewModel"),
        "LiveData" to Suggestion("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7", "LiveData"),
        "AppCompatActivity" to Suggestion("androidx.appcompat:appcompat:1.7.0", "AppCompat"),
        "Fragment" to Suggestion("androidx.fragment:fragment-ktx:1.8.5", "Fragment"),
        "RecyclerView" to Suggestion("androidx.recyclerview:recyclerview:1.3.2", "RecyclerView"),
        "Composable" to Suggestion("androidx.compose.runtime:runtime:1.7.6", "Compose"),
        "setContent" to Suggestion("androidx.activity:activity-compose:1.9.3", "Activity Compose"),
        "Retrofit" to Suggestion("com.squareup.retrofit2:retrofit:2.11.0", "Retrofit"),
        "OkHttpClient" to Suggestion("com.squareup.okhttp3:okhttp:4.12.0", "OkHttp"),
        "Gson" to Suggestion("com.google.code.gson:gson:2.11.0", "Gson"),
        "RoomDatabase" to Suggestion("androidx.room:room-runtime:2.6.1", "Room"),
        "CoroutineScope" to Suggestion("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0", "Coroutines"),
        "viewModelScope" to Suggestion("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7", "ViewModel KTX"),
        "Hilt" to Suggestion("com.google.dagger:hilt-android:2.53.1", "Hilt"),
        "Glide" to Suggestion("com.github.bumptech.glide:glide:4.16.0", "Glide"),
        "Coil" to Suggestion("io.coil-kt:coil:2.7.0", "Coil")
    )

    fun suggestForSymbol(symbol: String): Suggestion? {
        map[symbol]?.let { return it }
        return map[symbol.substringAfterLast('.')]
    }
}
