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
// versionCode / versionName are plain literals in defaultConfig below. Bump
// BOTH on every release to match the git tag:
//   v0.8.6  ->  versionName "0.8.6", versionCode 80600
//   (scheme: major*1_000_000 + minor*10_000 + patch*100)
//
// They MUST stay literals: F-Droid builds the app with no CI variables in its
// environment, and its `fdroid checkupdates` scanner reads the versionCode
// straight from this file — a computed value breaks both. A GitLab tag pipeline
// still overrides them from CI_COMMIT_TAG (see defaultConfig) as a convenience,
// which does not disturb the literal the scanner sees.

/** Map a semver tag ("v0.8.6") to the versionCode scheme (80600). */
fun parseSemverVersionCode(tag: String?): Int? {
    val v = tag?.removePrefix("v") ?: return null
    val parts = v.split(".")
    if (parts.size != 3) return null
    val major = parts[0].toIntOrNull() ?: return null
    val minor = parts[1].toIntOrNull() ?: return null
    val patch = parts[2].toIntOrNull() ?: return null
    return major * 1_000_000 + minor * 10_000 + patch * 100
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

    // Don't embed the Play-oriented dependency-metadata block in the APK's
    // signing block: F-Droid's scanner rejects it, and it isn't needed here.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    defaultConfig {
        applicationId = "net.yukh.xui"
        minSdk = 24
        targetSdk = 35
        versionCode = 81000
        versionName = "0.8.10"
        // GitLab tag pipeline overrides the literals above from the tag. These
        // lines don't match F-Droid's versionCode/versionName scanner (no bare
        // literal), so the values above remain what it reads.
        System.getenv("CI_COMMIT_TAG")?.let { tag ->
            versionName = tag.removePrefix("v")
            parseSemverVersionCode(tag)?.let { versionCode = it }
        }
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
            // Don't embed the git commit hash (META-INF/version-control-info.textproto):
            // it varies per checkout and breaks F-Droid's reproducible-build verification.
            vcsInfo { include = false }
        }
    }

    // Distribution flavors. `standard` is the GitLab-released build with the
    // in-app self-updater; `fdroid` strips the updater and the
    // REQUEST_INSTALL_PACKAGES permission (see src/fdroid/AndroidManifest.xml)
    // because F-Droid manages updates itself and forbids apps that fetch and
    // install their own APKs.
    flavorDimensions += "dist"
    productFlavors {
        create("standard") {
            dimension = "dist"
            isDefault = true
            buildConfigField("boolean", "IN_APP_UPDATER", "true")
        }
        create("fdroid") {
            dimension = "dist"
            buildConfigField("boolean", "IN_APP_UPDATER", "false")
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
        buildConfig = true
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
