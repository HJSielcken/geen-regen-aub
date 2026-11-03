import java.time.Duration
import java.time.Instant
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.sentry.android)
}

// App signing. One fixed key signs every build, so a new APK installs over the old one.
// CI reads it from repository secrets (see .github/workflows/android.yml); locally, put the
// same values in android/local.properties. Without a key, builds fall back to the debug key.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(property: String, env: String): String? =
    localProperties.getProperty(property)?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable(env).orNull?.takeIf { it.isNotBlank() }

val keystorePath = signingValue("geenregen.signing.storeFile", "SIGNING_KEYSTORE_PATH")
val hasSigningKey = keystorePath != null && file(keystorePath).exists()

// Crash reports go to the Sentry project theharmen/geen-regen-aub. The DSN comes from the
// SENTRY_DSN secret (or geenregen.sentryDsn in local.properties); without it Sentry stays off.
val sentryDsn = signingValue("geenregen.sentryDsn", "SENTRY_DSN") ?: ""
val sentryAuthToken = signingValue("geenregen.sentryAuthToken", "SENTRY_AUTH_TOKEN")

// One number for every CI build, debug or release: minutes since 2026-01-01. Builds from the
// Android and Release workflows then always install over older ones, whichever made them.
// VERSION_CODE overrides it; local builds use 1.
val ciVersionCode: Int? = providers.environmentVariable("GITHUB_ACTIONS").orNull?.let {
    Duration.between(Instant.parse("2026-01-01T00:00:00Z"), Instant.now()).toMinutes().toInt()
}
val appVersionCode = providers.environmentVariable("VERSION_CODE").orNull?.toIntOrNull() ?: ciVersionCode ?: 1
// The Release workflow passes the tag without its "v", e.g. 1.2.0.
val appVersionName = providers.environmentVariable("VERSION_NAME").orNull?.takeIf { it.isNotBlank() } ?: "0.1.0"

// MapLibre's native code is most of the APK, once per CPU type. Phones are arm64, so only that
// is packed. Build with -Pgeenregen.allAbis=true for an emulator or an old 32-bit phone.
val allAbis = providers.gradleProperty("geenregen.allAbis").orNull == "true"

android {
    namespace = "org.theharmen.geenregenaub"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.theharmen.geenregenaub"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"")

        if (!allAbis) {
            ndk { abiFilters += "arm64-v8a" }
        }
    }

    // The app is Dutch only; drop the dozens of translations that AndroidX libraries bring along.
    androidResources {
        localeFilters += listOf("nl", "en")
    }

    signingConfigs {
        if (hasSigningKey) {
            create("geenregen") {
                storeFile = file(keystorePath!!)
                storePassword = signingValue("geenregen.signing.storePassword", "SIGNING_KEYSTORE_PASSWORD")
                keyAlias = signingValue("geenregen.signing.keyAlias", "SIGNING_KEY_ALIAS")
                keyPassword = signingValue("geenregen.signing.keyPassword", "SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            if (hasSigningKey) signingConfig = signingConfigs.getByName("geenregen")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigningKey) signingConfig = signingConfigs.getByName("geenregen")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            // Build metadata that nothing reads at runtime.
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin", "/kotlin-tooling-metadata.json")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkDependencies = true
        // New library versions appear weekly; Dependabot handles upgrades.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

sentry {
    org.set("theharmen")
    projectName.set("geen-regen-aub")
    authToken.set(sentryAuthToken ?: "")
    // Upload the R8 mapping so release stack traces are readable, but only when a token is set.
    includeProguardMapping.set(sentryAuthToken != null)
    autoUploadProguardMapping.set(sentryAuthToken != null)
    ignoredBuildTypes.set(setOf("debug"))
    // We add the SDK ourselves (core only) and want no bytecode rewriting or extras.
    autoInstallation { enabled.set(false) }
    tracingInstrumentation { enabled.set(false) }
    includeSourceContext.set(false)
    uploadNativeSymbols.set(false)
    includeDependenciesReport.set(false)
    telemetry.set(false)
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:home"))
    implementation(project(":feature:alerts"))
    implementation(project(":feature:forecast"))
    implementation(project(":feature:map"))
    implementation(project(":widget"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.sentry.android.core)
}
