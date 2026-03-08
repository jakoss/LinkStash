import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

fun releaseValue(propertyName: String, environmentName: String): String? {
    return providers.gradleProperty(propertyName)
        .orElse(providers.environmentVariable(environmentName))
        .orNull
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

val releaseVersionName = releaseValue("releaseVersionName", "ANDROID_RELEASE_VERSION_NAME") ?: "1.0"
val releaseVersionCode = releaseValue("releaseVersionCode", "ANDROID_RELEASE_VERSION_CODE")
    ?.toIntOrNull()
    ?: 1

val releaseStoreFilePath = releaseValue("androidReleaseStoreFile", "ANDROID_RELEASE_STORE_FILE")
val releaseStorePassword = releaseValue("androidReleaseStorePassword", "ANDROID_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseValue("androidReleaseKeyAlias", "ANDROID_RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseValue("androidReleaseKeyPassword", "ANDROID_RELEASE_KEY_PASSWORD")
val isReleaseSigningConfigured = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { it != null }

kotlin {
    target {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    dependencies {
        implementation(projects.contracts)
        implementation(projects.shared)
        implementation(libs.androidx.core.ktx)
        implementation(libs.androidx.activity.compose)
        implementation(libs.androidx.work.runtime)
        implementation(libs.androidx.lifecycle.runtimeCompose)
        implementation(libs.androidx.lifecycle.viewmodelCompose)
        implementation(libs.compose.runtime)
        implementation(libs.compose.foundation)
        implementation(libs.compose.material3)
        implementation(libs.compose.ui)
        implementation(libs.compose.uiToolingPreview)
    }
}

android {
    namespace = "pl.jsyty.linkstash"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    signingConfigs {
        if (isReleaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    defaultConfig {
        applicationId = "pl.jsyty.linkstash"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (isReleaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    add("testImplementation", libs.junit)
    add("androidTestImplementation", libs.androidx.test.core)
    add("androidTestImplementation", libs.androidx.testExt.junit)
    add("androidTestImplementation", libs.androidx.espresso.core)
    add("androidTestImplementation", libs.androidx.work.testing)
}
