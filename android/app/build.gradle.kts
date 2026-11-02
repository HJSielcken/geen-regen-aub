import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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

android {
    namespace = "org.theharmen.geenregenaub"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.theharmen.geenregenaub"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // CI numbers each build so every new APK counts as an update.
        versionCode = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull() ?: 1
        versionName = "0.1.0"
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

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:home"))
    implementation(project(":feature:alerts"))
    implementation(project(":feature:forecast"))
    implementation(project(":feature:map"))
    implementation(project(":widget"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.work.runtime.ktx)
}
