import java.time.Duration
import java.time.Instant
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

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
        // MapLibre's native library is 12 MB, most of the APK. Compressed, the download is
        // about half; Android unpacks it once on install, so the app takes a bit more storage.
        jniLibs {
            useLegacyPackaging = true
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

// After every APK build, print what takes the space (download size), biggest first.
val reportApkSize = tasks.register("reportApkSize") {
    val apkDir = layout.buildDirectory.dir("outputs/apk")
    doLast {
        fun mb(bytes: Long): String = "%.2f MB".format(bytes / 1_048_576.0)
        fun group(name: String): String = when {
            name.startsWith("lib/") -> "native libraries"
            name.endsWith(".dex") -> "code (dex)"
            name.startsWith("res/") || name == "resources.arsc" -> "resources"
            name.startsWith("assets/") -> "assets"
            else -> "other"
        }
        val apks = apkDir.get().asFile.walk().filter { file -> file.extension == "apk" }.toList()
        for (apk in apks) {
            ZipFile(apk).use { zip ->
                val files: List<ZipEntry> = zip.entries().toList().filter { entry -> !entry.isDirectory }
                println("APK ${apk.name}: ${mb(apk.length())}")
                val groups: Map<String, List<ZipEntry>> = files.groupBy { entry -> group(entry.name) }
                for (name in groups.keys.sortedByDescending { key -> groups.getValue(key).sumOf { e -> e.compressedSize } }) {
                    val list = groups.getValue(name)
                    val packed = list.sumOf { e -> e.compressedSize }
                    val unpacked = list.sumOf { e -> e.size }
                    println("  $name: ${mb(packed)} in the APK, ${mb(unpacked)} unpacked")
                }
                println("  Largest files:")
                for (entry in files.sortedByDescending { e -> e.compressedSize }.take(12)) {
                    val stored = if (entry.method == ZipEntry.STORED) " (stored uncompressed)" else ""
                    println("    ${mb(entry.compressedSize)}  ${entry.name}$stored")
                }
            }
        }
    }
}
tasks.matching { task -> task.name == "assembleRelease" || task.name == "assembleDebug" }.configureEach {
    finalizedBy(reportApkSize)
}

// Classes that are only ever created by name, through reflection. If R8 drops their constructor
// the app crashes on start, before Sentry runs (that is what happened in 0.1.0), so check the R8
// mapping of every release build and fail the build instead.
val verifyReflectionKeeps = tasks.register("verifyReflectionKeeps") {
    val mapping = layout.buildDirectory.file("outputs/mapping/release/mapping.txt")
    doLast {
        val lines = mapping.get().asFile.readLines()
        for (name in listOf("androidx.work.impl.WorkDatabase_Impl")) {
            val start = lines.indexOfFirst { line -> line.startsWith("$name -> ") }
            check(start >= 0) { "R8 removed $name; see app/proguard-rules.pro" }
            val members = lines.drop(start + 1).takeWhile { line -> line.startsWith(" ") || line.startsWith("#") }
            println("R8 mapping for $name:")
            members.filter { line -> line.contains("<init>") }.forEach { line -> println("  ${line.trim()}") }
            check(members.any { line -> line.contains(" <init>() ") || line.trim().startsWith("void <init>()") || line.contains(":void <init>()") }) {
                "R8 removed the constructor of $name; Room creates it by reflection. See app/proguard-rules.pro"
            }
        }
    }
}
tasks.matching { task -> task.name == "assembleRelease" }.configureEach {
    finalizedBy(verifyReflectionKeeps)
}
