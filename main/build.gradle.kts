import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("io.github.nullij.acside-gradle-plugin").version("0.2.0")
}

val pluginVersion = "1.0.0"
val pluginName = "BuildGenius"
val packageName = "io.github.canadian.dev.buildgenius"

acpPlugin {
    metaFolderPath = "meta"
    outputFileName = "${pluginName}.acp"
}

android {
    namespace = packageName
    compileSdk = 35

    defaultConfig {
        applicationId = packageName
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = pluginVersion
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.acaide.plugin.api)
    implementation(libs.google.gson)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.material)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.activity.compose)
}

tasks.register("updatePluginInf") {
    doLast {
        val pluginJsonFile = file("meta/plugin.json")
        if (pluginJsonFile.exists()) {
            val gson = GsonBuilder().setPrettyPrinting().create()
            val jsonObject = gson.fromJson(pluginJsonFile.readText(), JsonObject::class.java)
            jsonObject.addProperty("version", pluginVersion)
            jsonObject.addProperty("name", pluginName)
            pluginJsonFile.writeText(gson.toJson(jsonObject))
            println("Updated plugin.json → version=$pluginVersion, name=$pluginName")
        }
    }
}

tasks.named("preBuild") {
    dependsOn("updatePluginInf")
}

tasks.matching { it.name == "assembleRelease" || it.name == "assembleDebug" }.configureEach {
    finalizedBy("updateRepositoryJson")
}

tasks.register("updateRepositoryJson") {
    description = "Updates repository.json with checksum, size, version and timestamp"
    group = "publishing"

    doLast {
        val acpFile = file("build/${pluginName}.acp")
        val rootDir = rootProject.projectDir
        val repoFile = rootDir.resolve("repository.json")
        val destAcp = rootDir.resolve("${pluginName}.acp")

        if (!acpFile.exists()) {
            println("Warning: ${acpFile.path} not found — skipping.")
            return@doLast
        }

        acpFile.copyTo(destAcp, overwrite = true)
        println("Copied ${acpFile.name} → ${destAcp.absolutePath}")

        if (!repoFile.exists()) {
            println("repository.json not found — skipping")
            return@doLast
        }

        val gson = GsonBuilder().setPrettyPrinting().create()
        val json = gson.fromJson(repoFile.readText(), JsonObject::class.java)

        json.addProperty("latestVersion", pluginVersion)
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
        json.addProperty("lastUpdated", timestamp)

        val bytes = destAcp.readBytes()
        val digest = MessageDigest.getInstance("SHA-256")
        val checksum = digest.digest(bytes).joinToString("") { "%02x".format(it) }
        json.addProperty("checksum", checksum)
        json.addProperty("size", bytes.size.toLong())

        repoFile.writeText(gson.toJson(json))
        println("Updated repository.json\n  version: $pluginVersion\n  checksum: $checksum\n  size: ${bytes.size}")
    }
}
