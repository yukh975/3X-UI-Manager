import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// ---------- Versioning ----------------------------------------------------
//
// versionCode and versionName are derived at configuration time:
//
//   versionCode  ← CI_PIPELINE_IID  (monotonic per project; set by GitLab)
//                  fallback: `git rev-list --count HEAD` for local builds
//                  fallback: 1
//
//   versionName  ← CI_COMMIT_TAG    (e.g. "v0.2.0" → "0.2.0")
//                  fallback: `git describe --tags --abbrev=0 --match v0.*`
//                  (v0.* = the app's release tags; excludes the upstream panel
//                   tags like v3.3.0 that the fork carries after the merge.
//                   Extend the glob when the app crosses to v1.x.)
//                  fallback: "0.1.0-<short_sha>" so artifacts from untagged
//                            commits still have a unique identifier.

// providers.exec defers process execution and is configuration-cache safe,
// unlike the legacy Project.exec which Gradle 8+ forbids at config time.
fun runGit(vararg args: String): String? = try {
    val output = providers.exec {
        commandLine("git", *args)
        workingDir = rootDir
        isIgnoreExitValue = true
    }
    val exit = output.result.get().exitValue
    if (exit == 0) output.standardOutput.asText.get().trim().ifEmpty { null } else null
} catch (_: Throwable) {
    null
}

/**
 * Map a semver tag to a deterministic versionCode.
 *
 *  v1.2.3  →  1_020_300
 *  v0.3.0  →    30_000
 *  v0.2.0  →    20_000
 *
 * Scheme: major*1_000_000 + minor*10_000 + patch*100. Leaves 99 slots per
 * patch level and 99 patches per minor for dev/branch builds to occupy
 * without ever colliding with a future release tag.
 */
fun parseSemverVersionCode(tag: String?): Int? {
    val v = tag?.removePrefix("v") ?: return null
    val parts = v.split(".")
    if (parts.size != 3) return null
    val major = parts[0].toIntOrNull() ?: return null
    val minor = parts[1].toIntOrNull() ?: return null
    val patch = parts[2].toIntOrNull() ?: return null
    return major * 1_000_000 + minor * 10_000 + patch * 100
}

val resolvedVersionCode: Int =
    // Tagged release → version code derived from the tag (stable across
    // rebuilds, predictable for users).
    parseSemverVersionCode(System.getenv("CI_COMMIT_TAG"))
        // Branch CI build → pipeline IID. Monotonic per build, far below
        // any realistic tag code, so never collides. Must come BEFORE the
        // git-describe fallback because runners do a shallow clone that
        // still reaches recent tags — every branch build between v0.2.0
        // and v0.3.0 would otherwise be stuck at 20_000.
        ?: System.getenv("CI_PIPELINE_IID")?.toIntOrNull()
        // Local builds with a reachable tag get a meaningful code; pure
        // local builds without one fall through to 1.
        ?: parseSemverVersionCode(runGit("describe", "--tags", "--abbrev=0", "--match", "v0.*"))
        ?: 1

val resolvedVersionName: String = run {
    val tag = System.getenv("CI_COMMIT_TAG")?.takeIf { it.isNotBlank() }
        ?: runGit("describe", "--tags", "--abbrev=0", "--match", "v0.*")
    if (tag != null) {
        tag.removePrefix("v")
    } else {
        val sha = System.getenv("CI_COMMIT_SHORT_SHA")
            ?: runGit("rev-parse", "--short=8", "HEAD")
            ?: "dev"
        "0.1.0-$sha"
    }
}

// ---------- Release signing -----------------------------------------------
//
// In CI: `RELEASE_KEYSTORE_FILE` points at a path decoded from the masked
// `RELEASE_KEYSTORE_B64` CI variable; `RELEASE_KEYSTORE_PASSWORD`,
// `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` come from masked variables.
//
// Locally: the same keystore lives at
//   ~/.config/3x-ui-android-keystore/release.p12
// and passwords live in release.properties next to it (chmod 600).
//
// If neither source is wired up, the release build type silently falls back
// to no signing — the APK still builds but isn't installable until signed.

val homeDir: String = System.getProperty("user.home")
val localKeystoreDir = file("$homeDir/.config/3x-ui-android-keystore")
val localKeystoreFile = localKeystoreDir.resolve("release.p12")
val localKeystoreProps = localKeystoreDir.resolve("release.properties")

val localPasswords: Properties = Properties().also { p ->
    if (localKeystoreProps.exists()) localKeystoreProps.inputStream().use { p.load(it) }
}

val signingStoreFile: File? = System.getenv("RELEASE_KEYSTORE_FILE")?.let(::file)
    ?: localKeystoreFile.takeIf { it.exists() }
val signingStorePassword: String? = System.getenv("RELEASE_KEYSTORE_PASSWORD")
    ?: localPasswords.getProperty("keystore_password")
val signingKeyAlias: String = System.getenv("RELEASE_KEY_ALIAS")
    ?: localPasswords.getProperty("key_alias")
    ?: "xui-release"
val signingKeyPassword: String? = System.getenv("RELEASE_KEY_PASSWORD")
    ?: localPasswords.getProperty("key_password")
    ?: signingStorePassword

// APK base name — the output is "<archivesName>-<buildType>.apk", so the
// release APK becomes "3x-ui-manager-release.apk" (instead of the default
// "app-release.apk"), matching the project rather than the Gradle module.
base {
    archivesName.set("3x-ui-manager")
}

android {
    namespace = "net.yukh.xui"
    compileSdk = 35

    defaultConfig {
        applicationId = "net.yukh.xui"
        minSdk = 24
        targetSdk = 35
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (signingStoreFile != null && signingStoreFile.exists() && signingStorePassword != null) {
                storeFile = signingStoreFile
                storeType = "PKCS12"
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")
                ?.takeIf { it.storeFile != null }
                ?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
        )
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
