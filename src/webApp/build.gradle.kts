import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootExtension

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

val ensureWasmCustomFormatters by tasks.registering {
    val outputFile = rootProject.layout.buildDirectory.file(
        "wasm/packages/LinkStash-webApp/kotlin/custom-formatters.js"
    )

    outputs.file(outputFile)

    doLast {
        val targetFile = outputFile.get().asFile
        targetFile.parentFile.mkdirs()
        if (!targetFile.exists()) {
            targetFile.writeText(
                """
                // Production webpack expects this module next to the generated wasm entrypoint.
                export {};
                """.trimIndent()
            )
        }
    }
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "linkstash-web.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    port = 8081
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(projects.contracts)
            implementation(compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.landscapist.image)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.js)
        }
    }
}

// Kotlin 2.3.10 hardcodes Node 25 for Wasm, but Yarn 1.22.17 crashes with it in the server image build.
rootProject.extensions.findByType(WasmNodeJsRootExtension::class.java)?.let { wasmNodeJs ->
    @Suppress("DEPRECATION_ERROR")
    wasmNodeJs.version = "24.9.0"
}

tasks.matching { task ->
    task.name in setOf(
        "wasmJsBrowserProductionWebpack",
        "wasmJsBrowserDistribution"
    )
}.configureEach {
    dependsOn(ensureWasmCustomFormatters)
}
