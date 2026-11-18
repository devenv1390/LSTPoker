import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.logback.classic)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

application {
    mainClass.set("com.aniapp.pokerdemo.server.ApplicationKt")
}

tasks.register<JavaExec>("e2eClient") {
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.aniapp.pokerdemo.server.E2EClientKt")
    args = (project.findProperty("e2eArgs") as String? ?: "")
        .split(" ").filter { it.isNotEmpty() }
    (project.findProperty("e2eJvmArgs") as String? ?: "")
        .split(" ").filter { it.isNotEmpty() }.forEach { jvmArgs(it) }
}