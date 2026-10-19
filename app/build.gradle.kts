plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Installable QA variant: isolated package avoids debug-signature conflicts with an existing app.
val deviceAiPreview = providers.gradleProperty("deviceAiPreview").orNull == "true"

android {
    namespace = "at.heinz.deviceai"
    compileSdk = 36

    defaultConfig {
        applicationId = "at.heinz.deviceai"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.3.1"
        testInstrumentationRunner = "at.heinz.deviceai.ContinuumVisualInstrumentation"
        manifestPlaceholders["appLabel"] = if (deviceAiPreview) "Device AI Preview" else "Device AI"
    }

    buildTypes {
        getByName("debug") {
            if (deviceAiPreview) applicationIdSuffix = ".preview"
        }
    }

    buildFeatures {
        aidl = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // CI gate (android.yml): an API newer than minSdk 26 without a version check
        // crashes on older devices (e.g. InputStream.readNBytes needs API 33).
        checkOnly += setOf("NewApi")
        abortOnError = true
        checkReleaseBuilds = false
    }

}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
}

