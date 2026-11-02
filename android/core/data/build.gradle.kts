import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

// Relay settings come from android/local.properties (never committed),
// a Gradle property, or an environment variable (used in CI), in that order.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun relaySetting(key: String, env: String, default: String): String =
    localProperties.getProperty(key)
        ?: providers.gradleProperty(key).orNull
        ?: providers.environmentVariable(env).orNull
        ?: default

android {
    namespace = "org.theharmen.geenregenaub.data"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        val baseUrl = relaySetting("geenregen.relayUrl", "GEENREGEN_RELAY_URL", "https://geenregenaub.theharmen.org")
        val secret = relaySetting("geenregen.relaySecret", "GEENREGEN_RELAY_SECRET", "")
        buildConfigField("String", "RELAY_BASE_URL", "\"$baseUrl\"")
        buildConfigField("String", "RELAY_SECRET", "\"$secret\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:domain"))
    api(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
