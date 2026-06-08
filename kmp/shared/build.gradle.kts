import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
}

kotlin {
    // JVM target = fast common-code compile check without the Kotlin/Native
    // toolchain; also the future androidApp can depend on this.
    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    // iOS targets. iosX64 = Intel-Mac simulator (this build host); the arm64
    // targets are for Apple-silicon simulator and real devices.
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val coroutines = "1.9.0"
        val serialization = "1.7.3"
        val ktor = "3.0.3"

        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization")
            implementation("io.ktor:ktor-client-core:$ktor")
            implementation("io.ktor:ktor-client-content-negotiation:$ktor")
            implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:$ktor")
        }
        jvmMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:$ktor")
        }
    }
}
