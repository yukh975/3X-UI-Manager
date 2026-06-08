// Plugin versions are declared once here (apply false) so every subproject uses
// the SAME plugin classloader. Declaring `version` in each module's plugins block
// loads the Kotlin Multiplatform plugin multiple times and fails with
// "Cannot have two attributes with the same name ... kotlin.native.bundle.type".
plugins {
    kotlin("multiplatform") version "2.1.0" apply false
    kotlin("plugin.serialization") version "2.1.0" apply false
    id("org.jetbrains.compose") version "1.7.3" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
}
