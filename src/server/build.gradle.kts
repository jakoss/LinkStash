import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

kotlin {
    jvmToolchain(25)
}

application {
    mainClass = "pl.jsyty.linkstash.server.MainKt"
}

dependencies {
    implementation(projects.contracts)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.callLogging)
    implementation(libs.ktor.server.callId)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.sessions)
    implementation(libs.ktor.server.statusPages)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.contentNegotiation)
    implementation(libs.ktor.client.cio)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.sqlite.jdbc)

    implementation(libs.logback.classic)
}

tasks.register<JavaExec>("runServerLocal") {
    group = "application"
    description = "Runs the server with environment variables loaded from local.properties."
    dependsOn("classes")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    standardInput = System.`in`

    doFirst {
        val env = loadServerEnvFromLocalProperties(rootProject.file("local.properties"))
        if (env.isEmpty()) {
            logger.lifecycle("No server env vars found in local.properties; running with current shell environment only.")
        } else {
            logger.lifecycle("Loaded {} env var(s) from local.properties for runServerLocal.", env.size)
            environment(env)
        }
    }
}

fun loadServerEnvFromLocalProperties(localPropertiesFile: File): Map<String, String> {
    if (!localPropertiesFile.exists()) return emptyMap()

    val properties = Properties()
    localPropertiesFile.inputStream().use(properties::load)
    val envNameRegex = Regex("[A-Z][A-Z0-9_]*")

    val directEnv = properties.stringPropertyNames()
        .filter { it.matches(envNameRegex) }
        .associateWith { properties.getProperty(it).trim() }
        .filterValues { it.isNotEmpty() }

    val prefixedEnv = properties.stringPropertyNames()
        .filter { it.startsWith("server.env.") }
        .associate { key ->
            key.removePrefix("server.env.") to properties.getProperty(key).trim()
        }
        .filter { (name, value) -> name.matches(envNameRegex) && value.isNotEmpty() }

    return directEnv + prefixedEnv
}
