import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The app's own CHANGELOG, compiled into the binary rather than fetched: the
// history page has to work offline and must never contact our infrastructure.
// Emitted as chunked string literals because a single Kotlin/JVM string
// constant tops out at 64 KB.
val changelogSourceDir = layout.buildDirectory.dir("generated/changelog/kotlin")

val generateChangelogSource = tasks.register("generateChangelogSource") {
    val en = rootProject.file("CHANGELOG.md")
    val ru = rootProject.file("CHANGELOG.ru.md")
    inputs.files(en, ru)
    outputs.dir(changelogSourceDir)
    doLast {
        fun chunks(file: File): String {
            if (!file.exists()) return "emptyList()"
            val parts = mutableListOf<StringBuilder>()
            var cur = StringBuilder()
            file.readText().lineSequence().forEach { line ->
                if (cur.length > 2000) { parts.add(cur); cur = StringBuilder() }
                cur.append(line).append('\n')
            }
            parts.add(cur)
            return parts.joinToString(",\n        ", prefix = "listOf(\n        ", postfix = ",\n    )") { part ->
                val escaped = part.toString()
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("$", "\${'$'}")
                    .replace("\n", "\\n")
                "\"" + escaped + "\""
            }
        }
        val out = changelogSourceDir.get().file("ChangelogSource.kt").asFile
        out.parentFile.mkdirs()
        out.writeText(
            buildString {
                appendLine("package net.yukh.xui.app")
                appendLine()
                appendLine("internal object ChangelogSource {")
                appendLine("    val en: String get() = EN.joinToString(\"\")")
                appendLine("    val ru: String get() = RU.joinToString(\"\")")
                appendLine()
                appendLine("    private val EN: List<String> = " + chunks(en))
                appendLine()
                appendLine("    private val RU: List<String> = " + chunks(ru))
                appendLine("}")
            },
        )
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    dependsOn(generateChangelogSource)
}

kotlin {
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    jvm("desktop")

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(changelogSourceDir)
        }
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            implementation("io.github.alexzhirkevich:qrose:1.0.1")
            // LocalLifecycleOwner / lifecycle observer (re-lock on resume) — bundled
            // for iOS via Compose MP but needs an explicit dep for the JVM target.
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
        }
        val desktopMain by getting
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
        }
    }
}

compose.desktop {
    application {
        mainClass = "net.yukh.xui.app.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Pkg)
            packageName = "3X-UI Manager"
            // macOS dmg/pkg require MAJOR > 0; the in-app version (About) is
            // 0.3.23 (appVersionName), synced with the Android + iOS builds.
            packageVersion = "1.0.0"
            macOS {
                bundleID = "net.yukh.xui.desktop"
                // 3X monogram app icon, rendered from the Android adaptive icon
                // (icons/AppIcon.svg → .icns). Without this jpackage uses the
                // generic Java icon.
                iconFile.set(project.file("icons/AppIcon.icns"))
            }
        }
    }
}
