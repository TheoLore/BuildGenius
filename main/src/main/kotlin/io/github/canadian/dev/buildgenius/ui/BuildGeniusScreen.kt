package io.github.canadian.dev.buildgenius.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.canadian.dev.buildgenius.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildGeniusScreen(
    onDismiss: () -> Unit,
    onAnalyze: suspend () -> ProjectSnapshot,
    onApplyFix: suspend (BuildIssue, ProjectSnapshot) -> FixResult,
    onApplyAllSafe: suspend (ProjectSnapshot) -> FixResult
) {
    var state by remember { mutableStateOf(DashboardState()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        state = state.copy(isLoading = true)
        try {
            val snapshot = withContext(Dispatchers.IO) { onAnalyze() }
            state = state.copy(isLoading = false, snapshot = snapshot)
        } catch (e: Throwable) {
            state = state.copy(isLoading = false, errorMessage = (e.message ?: e.javaClass.simpleName).take(200))
        }
    }

    fun refresh() {
        scope.launch {
            state = state.copy(isLoading = true, lastFixResult = null, errorMessage = null)
            try {
                val snapshot = withContext(Dispatchers.IO) { onAnalyze() }
                state = state.copy(isLoading = false, snapshot = snapshot)
            } catch (e: Throwable) {
                state = state.copy(isLoading = false, errorMessage = (e.message ?: e.javaClass.simpleName).take(200))
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp
    ) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Build Genius", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                },
                actions = {
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.errorMessage != null -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(state.errorMessage ?: "", color = MaterialTheme.colorScheme.error)
                }
                state.snapshot != null -> {
                    val snap = state.snapshot!!
                    Column(Modifier.fillMaxSize()) {
                        val score = snap.healthScore
                        val color = when {
                            score >= 85 -> Color(0xFF4CAF50)
                            score >= 60 -> Color(0xFFFFA000)
                            else -> Color(0xFFE53935)
                        }
                        Card(
                            Modifier.fillMaxWidth().padding(12.dp),
                            colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("$score", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Health Score", style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        listOfNotNull(
                                            snap.agpVersion?.let { "AGP $it" },
                                            snap.kotlinVersion?.let { "Kotlin $it" },
                                            snap.compileSdk?.let { "SDK $it" }
                                        ).joinToString(" • "),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    state = state.copy(isLoading = true)
                                    val issue = BuildIssue(
                                        id = "turbo",
                                        title = "Turbo",
                                        description = "",
                                        severity = IssueSeverity.MEDIUM,
                                        category = IssueCategory.PROPERTIES,
                                        autoFixable = true,
                                        fixActionId = "apply_turbo",
                                        confidence = 1f
                                    )
                                    val result = withContext(Dispatchers.IO) { onApplyFix(issue, snap) }
                                    state = state.copy(isLoading = false, lastFixResult = result)
                                    if (result.success) refresh()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C896))
                        ) {
                            Icon(Icons.Default.Bolt, null)
                            Spacer(Modifier.width(8.dp))
                            Text("TURBO MODE – Max ACSIDE Speed", fontWeight = FontWeight.Bold)
                        }

                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        state = state.copy(isLoading = true)
                                        val result = withContext(Dispatchers.IO) { onApplyAllSafe(snap) }
                                        state = state.copy(isLoading = false, lastFixResult = result)
                                        if (result.success) refresh()
                                    }
                                },
                                enabled = snap.issues.any { it.autoFixable },
                                modifier = Modifier.weight(1f)
                            ) { Text("Fix All Safe") }
                            OutlinedButton(onClick = { refresh() }, modifier = Modifier.weight(1f)) {
                                Text("Re-scan")
                            }
                        }

                        state.lastFixResult?.let { result ->
                            Text(
                                result.message,
                                modifier = Modifier.padding(horizontal = 12.dp),
                                color = if (result.success) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }

                        Text(
                            "Issues (${snap.issues.size})",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.titleMedium
                        )

                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(snap.issues, key = { it.id }) { issue ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(issue.severity.name, style = MaterialTheme.typography.labelSmall)
                                        Text(issue.title, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            issue.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (issue.autoFixable) {
                                            Spacer(Modifier.height(8.dp))
                                            FilledTonalButton(onClick = {
                                                scope.launch {
                                                    state = state.copy(isLoading = true)
                                                    val result = withContext(Dispatchers.IO) {
                                                        onApplyFix(issue, snap)
                                                    }
                                                    state = state.copy(isLoading = false, lastFixResult = result)
                                                    if (result.success) refresh()
                                                }
                                            }) { Text("Apply Fix") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
