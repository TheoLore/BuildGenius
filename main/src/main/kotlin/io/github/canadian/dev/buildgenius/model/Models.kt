package io.github.canadian.dev.buildgenius.model

import java.io.File

/**
 * Core data models for Build Genius.
 * Designed to be lightweight and serializable.
 */

enum class IssueSeverity {
    CRITICAL,   // Blocks build completely
    HIGH,       // Likely to fail soon or causes major problems
    MEDIUM,     // Should be fixed
    LOW,        // Nice to have / style
    INFO        // Informational only
}

enum class IssueCategory {
    GRADLE_PLUGIN,
    DEPENDENCY,
    SDK_CONFIG,
    JDK,
    KOTLIN,
    AGP,
    PROPERTIES,
    STRUCTURE,
    OTHER
}

data class BuildIssue(
    val id: String,
    val title: String,
    val description: String,
    val severity: IssueSeverity,
    val category: IssueCategory,
    val filePath: String? = null,
    val suggestedFix: String? = null,
    val autoFixable: Boolean = false,
    val fixActionId: String? = null,
    val confidence: Float = 0.8f
)

data class ProjectSnapshot(
    val projectDir: File?,
    val isGradleProject: Boolean,
    val isAndroidProject: Boolean,
    val isFlutterProject: Boolean,
    val agpVersion: String?,
    val kotlinVersion: String?,
    val compileSdk: Int?,
    val minSdk: Int?,
    val targetSdk: Int?,
    val jdkVersion: String?,
    val gradleVersion: String?,
    val hasKts: Boolean,
    val modules: List<String>,
    val issues: List<BuildIssue>,
    val lastAnalyzed: Long = System.currentTimeMillis()
) {
    val healthScore: Int
        get() {
            if (issues.isEmpty()) return 100
            val penalty = issues.sumOf {
                when (it.severity) {
                    IssueSeverity.CRITICAL -> 25
                    IssueSeverity.HIGH -> 15
                    IssueSeverity.MEDIUM -> 8
                    IssueSeverity.LOW -> 3
                    IssueSeverity.INFO -> 0
                }
            }
            return (100 - penalty).coerceIn(0, 100)
        }

    val criticalCount get() = issues.count { it.severity == IssueSeverity.CRITICAL }
    val highCount get() = issues.count { it.severity == IssueSeverity.HIGH }
}

data class FixResult(
    val success: Boolean,
    val message: String,
    val appliedChanges: List<FileChange> = emptyList(),
    val remainingIssues: List<BuildIssue> = emptyList()
)

data class FileChange(
    val file: File,
    val originalContent: String,
    val newContent: String,
    val description: String
)

data class DashboardState(
    val isLoading: Boolean = true,
    val snapshot: ProjectSnapshot? = null,
    val selectedIssue: BuildIssue? = null,
    val pendingChange: FileChange? = null,
    val lastFixResult: FixResult? = null,
    val errorMessage: String? = null
)
