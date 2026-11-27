import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

val desktopPlatform = providers.gradleProperty("desktopPlatform").orElse("currentOs")

dependencies {
    implementation(project(":shared"))
    implementation(
        when (desktopPlatform.get()) {
            "windows-x64" -> compose.desktop.windows_x64
            "linux-x64" -> compose.desktop.linux_x64
            "macos-x64" -> compose.desktop.macos_x64
            "macos-arm64" -> compose.desktop.macos_arm64
            else -> compose.desktop.currentOs
        }
    )
    implementation(libs.kotlinx.coroutines.swing)
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

compose.desktop {
    application {
        mainClass = "com.aniapp.pokerdemo.desktop.MainKt"
        nativeDistributions {
            packageName = "LSTPoker"
            packageVersion = "1.0.0"
            description = "LSTPoker 单挑德州扑克"
            vendor = "LSTPoker"
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg)
            windows {
                shortcut = true
                menu = true
                dirChooser = true
                perUserInstall = true
            }
        }
    }
}
