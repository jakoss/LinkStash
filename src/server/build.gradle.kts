import java.io.File
import java.util.Properties
import org.gradle.api.tasks.testing.Test

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

    testImplementation(libs.ktor.server.testHost)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
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

tasks.withType<Test>().configureEach {
    useJUnit()

    val configuredPath = providers.gradleProperty("apiTestEnvFile")
        .orNull
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: ".env.api-test"
    val envFile = project.file(configuredPath)
    val env = loadEnvFile(envFile)
    if (env.isEmpty()) {
        logger.lifecycle("No API test env vars found in {}.", envFile)
    } else {
        logger.lifecycle("Loaded {} API test env var(s) from {}.", env.size, envFile)
        environment(env)

        env["API_TEST_RAINDROP_TOKEN"]
            ?.takeIf { it.isNotBlank() }
            ?.let { systemProperty("api.test.raindrop.token", it) }
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

fun loadEnvFile(envFile: File): Map<String, String> {
    if (!envFile.exists()) return emptyMap()

    return envFile.readLines()
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val separatorIndex = line.indexOf('=')
            if (separatorIndex <= 0) return@mapNotNull null
            val key = line.substring(0, separatorIndex).trim()
            if (key.isEmpty()) return@mapNotNull null
            val rawValue = line.substring(separatorIndex + 1).trim()
            val unquoted = rawValue
                .removeSurrounding("\"")
                .removeSurrounding("'")
            key to unquoted
        }
        .toMap()
}
