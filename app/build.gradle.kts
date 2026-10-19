plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val mentorSigningPath = providers.environmentVariable("CONTINUUM_MENTOR_SIGNING_FILE").orNull
val mentorSigningPassword = providers.environmentVariable("CONTINUUM_MENTOR_SIGNING_PASSWORD").orNull

// Installable QA variant: isolated package avoids debug-signature conflicts with an existing app.
val deviceAiPreview = providers.gradleProperty("deviceAiPreview").orNull == "true"
val deviceAiMentorPreview = providers.gradleProperty("deviceAiMentorPreview").orNull == "true"
require(!(deviceAiPreview && deviceAiMentorPreview)) {
    "Use only one preview package ID at a time."
}

android {
    signingConfigs {
        if (deviceAiMentorPreview && !mentorSigningPath.isNullOrBlank() &&
            !mentorSigningPassword.isNullOrBlank()
        ) {
            create("mentorPreview") {
                storeFile = file(mentorSigningPath)
                storePassword = mentorSigningPassword
                keyAlias = "deviceai-mentor"
                keyPassword = mentorSigningPassword
                storeType = "PKCS12"
            }
        }
    }
    namespace = "at.heinz.deviceai"
    compileSdk = 36

    defaultConfig {
        applicationId = "at.heinz.deviceai"
        minSdk = 26
        targetSdk = 36
        versionCode = if (deviceAiMentorPreview) 6 else 4
        versionName = if (deviceAiMentorPreview) "0.3.3" else "0.3.1"
        testInstrumentationRunner = "at.heinz.deviceai.ContinuumVisualInstrumentation"
        manifestPlaceholders["appLabel"] = when {
            deviceAiMentorPreview -> "Device AI Mentor Pair"
            deviceAiPreview -> "Device AI Preview"
            else -> "Device AI"
        }
    }

    buildTypes {
        getByName("debug") {
            if (deviceAiMentorPreview) {
                // Independent signing and app storage: never overwrite the tested Sol preview.
                applicationIdSuffix = ".mentorpaired"
                if ("mentorPreview" in signingConfigs.names) {
                    signingConfig = signingConfigs.getByName("mentorPreview")
                }
            } else if (deviceAiPreview) {
                applicationIdSuffix = ".preview"
            }
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

