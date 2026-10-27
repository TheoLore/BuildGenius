package io.github.canadian.dev.buildgenius.analyzer

import android.util.Log
import com.nullij.androidcodestudio.plugins.api.PluginApi
import java.io.File

/**
 * Reads ACSIDE environment via PluginApi.environment.
 * All optional/runtime methods are wrapped in Throwable catches because
 * AbstractMethodError (API mismatch) is an Error, not an Exception.
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
        return try {
            profileInternal()
        } catch (t: Throwable) {
            Log.e(TAG, "Environment profile failed: ${t.javaClass.simpleName}: ${t.message}", t)
            emptyProfile()
        }
    }

    private fun profileInternal(): Profile {
        val env = PluginApi.environment

        val initialized = try {
            env.isInitialized()
        } catch (t: Throwable) {
            Log.w(TAG, "isInitialized failed", t)
            false
        }
        if (!initialized) {
            Log.w(TAG, "Environment not initialized yet")
            return emptyProfile()
        }

        val androidSdk = safeFile { env.androidSdkDir }
        val flutter = safeFile { env.flutterDir }
        val platforms = scanPlatforms(androidSdk)
        val buildTools = scanBuildTools(androidSdk)
        val highestApi = platforms.maxOfOrNull { it.api }
        val preferredBt = buildTools.maxByOrNull { parseVersionKey(it.version) }?.version

        val envMap = safeEnvMap()
        val rootfsJava = safeString { env.rootfsJavaHome }
        val javaHome = rootfsJava ?: envMap["JAVA_HOME"]
        val javaHint = detectJavaVersion(javaHome, envMap)

        Log.i(TAG, "SDK=${androidSdk?.absolutePath} platforms=${platforms.map { it.api }}")

        return Profile(
            initialized = true,
            openProjectDir = safeFile { env.openProjectDir },
            projectsDir = safeFile { env.projectsDir },
            androidSdkDir = androidSdk,
            flutterDir = flutter,
            homeDir = safeFile { env.homeDir },
            localDir = safeFile { env.localDir },
            tmpDir = safeFile { env.tmpDir },
            rootfsAndroidSdkPath = safeString { env.rootfsAndroidSdkPath },
            rootfsJavaHome = rootfsJava,
            installedPlatforms = platforms,
            installedBuildTools = buildTools,
            highestPlatformApi = highestApi,
            preferredCompileSdk = highestApi,
            preferredBuildTools = preferredBt,
            javaHome = javaHome,
            javaVersionHint = javaHint,
            hasFlutter = flutter != null && flutter.exists() && File(flutter, "bin/flutter").exists(),
            envMap = envMap
        )
    }

    private fun safeEnvMap(): Map<String, String> {
        return try {
            val env = PluginApi.environment
            val raw = env.getEnvironment()
            raw?.mapNotNull { (k, v) ->
                val key = k?.toString() ?: return@mapNotNull null
                val value = v?.toString() ?: return@mapNotNull null
                key to value
            }?.toMap() ?: emptyMap()
        } catch (t: Throwable) {
            Log.w(TAG, "getEnvironment unavailable (${t.javaClass.simpleName}), using empty map")
            emptyMap()
        }
    }

    private fun safeFile(block: () -> File?): File? {
        return try {
            block()
        } catch (t: Throwable) {
            Log.w(TAG, "path accessor failed: ${t.message}")
            null
        }
    }

    private fun safeString(block: () -> String?): String? {
        return try {
            block()
        } catch (t: Throwable) {
            null
        }
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
        for (i in 0 until 4) key = key * 1000 + (parts.getOrNull(i) ?: 0)
        return key
    }

    private fun detectJavaVersion(javaHome: String?, envMap: Map<String, String>): String? {
        envMap["JAVA_VERSION"]?.let { return it }
        if (javaHome.isNullOrBlank()) return null
        val m = Regex("""(?:jdk|jre|openjdk)[_-]?(\\d+)""", RegexOption.IGNORE_CASE).find(javaHome)
        return m?.groupValues?.getOrNull(1)?.let { "JDK $it" }
    }

    private fun emptyProfile() = Profile(
        false, null, null, null, null, null, null, null, null, null,
        emptyList(), emptyList(), null, null, null, null, null, false, emptyMap()
    )
}
