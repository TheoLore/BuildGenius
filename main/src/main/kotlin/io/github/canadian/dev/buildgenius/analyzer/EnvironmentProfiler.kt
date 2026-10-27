package io.github.canadian.dev.buildgenius.analyzer

import android.util.Log
import com.nullij.androidcodestudio.plugins.api.PluginApi
import java.io.File

/**
 * Reads the REAL ACSIDE environment via PluginApi.environment.
 */
class EnvironmentProfiler {

    companion object {
        private const val TAG = "BuildGenius.Env"
    }

    data class SdkPlatform(val api: Int, val path: File)
    data class BuildTools(val version: String, val path: File)

    data class Profile(
        val initialized: Boolean,
        val openProjectDir: File?,
        val projectsDir: File?,
        val androidSdkDir: File?,
        val flutterDir: File?,
        val homeDir: File?,
        val localDir: File?,
        val tmpDir: File?,
        val rootfsAndroidSdkPath: String?,
        val rootfsJavaHome: String?,
        val installedPlatforms: List<SdkPlatform>,
        val installedBuildTools: List<BuildTools>,
        val highestPlatformApi: Int?,
        val preferredCompileSdk: Int?,
        val preferredBuildTools: String?,
        val javaHome: String?,
        val javaVersionHint: String?,
        val hasFlutter: Boolean,
        val envMap: Map<String, String>
    )

    fun profile(): Profile {
        val env = PluginApi.environment

        if (!env.isInitialized()) {
            Log.w(TAG, "Environment not initialized yet")
            return emptyProfile()
        }

        val androidSdk = env.androidSdkDir
        val flutter = env.flutterDir
        val platforms = scanPlatforms(androidSdk)
        val buildTools = scanBuildTools(androidSdk)
        val highestApi = platforms.maxOfOrNull { it.api }
        val preferredCompile = highestApi
        val preferredBt = buildTools.maxByOrNull { parseVersionKey(it.version) }?.version

        val javaHome = env.rootfsJavaHome ?: env.getEnvironment()["JAVA_HOME"]
        val javaHint = detectJavaVersion(javaHome, env.getEnvironment())

        val envMap = try {
            env.getEnvironment()
        } catch (e: Exception) {
            emptyMap()
        }

        Log.i(TAG, "SDK=${androidSdk?.absolutePath} platforms=${platforms.map { it.api }} build-tools=${buildTools.map { it.version }}")

        return Profile(
            initialized = true,
            openProjectDir = env.openProjectDir,
            projectsDir = env.projectsDir,
            androidSdkDir = androidSdk,
            flutterDir = flutter,
            homeDir = env.homeDir,
            localDir = env.localDir,
            tmpDir = env.tmpDir,
            rootfsAndroidSdkPath = env.rootfsAndroidSdkPath,
            rootfsJavaHome = env.rootfsJavaHome,
            installedPlatforms = platforms,
            installedBuildTools = buildTools,
            highestPlatformApi = highestApi,
            preferredCompileSdk = preferredCompile,
            preferredBuildTools = preferredBt,
            javaHome = javaHome,
            javaVersionHint = javaHint,
            hasFlutter = flutter != null && flutter.exists() && File(flutter, "bin/flutter").exists(),
            envMap = envMap
        )
    }

    private fun scanPlatforms(sdk: File?): List<SdkPlatform> {
        if (sdk == null || !sdk.exists()) return emptyList()
        val platformsDir = File(sdk, "platforms")
        if (!platformsDir.isDirectory) return emptyList()
        return platformsDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("android-") }
            ?.mapNotNull { dir ->
                val api = dir.name.removePrefix("android-").toIntOrNull() ?: return@mapNotNull null
                if (!File(dir, "android.jar").exists()) return@mapNotNull null
                SdkPlatform(api, dir)
            }
            ?.sortedBy { it.api }
            ?: emptyList()
    }

    private fun scanBuildTools(sdk: File?): List<BuildTools> {
        if (sdk == null || !sdk.exists()) return emptyList()
        val btDir = File(sdk, "build-tools")
        if (!btDir.isDirectory) return emptyList()
        return btDir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { BuildTools(it.name, it) }
            ?.sortedBy { parseVersionKey(it.version) }
            ?: emptyList()
    }

    private fun parseVersionKey(v: String): Long {
        val parts = v.split('.', '-').mapNotNull { it.toLongOrNull() }
        var key = 0L
        for (i in 0 until 4) {
            key = key * 1000 + (parts.getOrNull(i) ?: 0)
        }
        return key
    }

    private fun detectJavaVersion(javaHome: String?, envMap: Map<String, String>): String? {
        envMap["JAVA_VERSION"]?.let { return it }
        if (javaHome.isNullOrBlank()) return null
        val m = Regex("""(?:jdk|jre|openjdk)[_-]?(\d+)""", RegexOption.IGNORE_CASE).find(javaHome)
        return m?.groupValues?.getOrNull(1)?.let { "JDK $it" }
    }

    private fun emptyProfile() = Profile(
        initialized = false,
        openProjectDir = null,
        projectsDir = null,
        androidSdkDir = null,
        flutterDir = null,
        homeDir = null,
        localDir = null,
        tmpDir = null,
        rootfsAndroidSdkPath = null,
        rootfsJavaHome = null,
        installedPlatforms = emptyList(),
        installedBuildTools = emptyList(),
        highestPlatformApi = null,
        preferredCompileSdk = null,
        preferredBuildTools = null,
        javaHome = null,
        javaVersionHint = null,
        hasFlutter = false,
        envMap = emptyMap()
    )
}
