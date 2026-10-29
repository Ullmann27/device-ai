plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Optional desktop widget QA; ordinary Android/Termux unit/build tasks keep their existing dependencies.
val continuumUiWidgetTests = providers.gradleProperty("continuumUiWidgetTests").orNull == "true"
val uiTestAndroidSdk by configurations.creating { isTransitive = false }
val uiTestResourceSdk by configurations.creating { isTransitive = false }
val prepareUiTestSdk = tasks.register<Sync>("prepareUiTestSdk") {
    from(uiTestAndroidSdk, uiTestResourceSdk)
    into(layout.buildDirectory.dir("ui-test-sdk"))
}
if (continuumUiWidgetTests) tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    dependsOn(prepareUiTestSdk)
    doFirst {
        systemProperty("robolectric.offline", "true")
        systemProperty("robolectric.dependency.dir", layout.buildDirectory.dir("ui-test-sdk").get().asFile.absolutePath)
        providers.gradleProperty("continuumUiEvidenceDir").orNull?.let {
            systemProperty("continuum.ui.evidenceDir", it)
        }
    }
}

val mentorSigningPath = providers.environmentVariable("CONTINUUM_MENTOR_SIGNING_FILE").orNull
val mentorSigningPassword = providers.environmentVariable("CONTINUUM_MENTOR_SIGNING_PASSWORD").orNull
// A FULL local main-app, independently signed and side-by-side installable.
// It never reuses the owner's historic Mentor Pair package or private signer.
val continuumLocalStandalone = providers.gradleProperty("continuumLocalStandalone").orNull == "true"
// Opt-in QA for new Continuum Lokal: debug package gets .qa suffix and never
// consumes the owner's original signing key. The signed release lane is unchanged.
val continuumLocalStandaloneQa = providers.gradleProperty("continuumLocalStandaloneQa").orNull == "true"
val continuumLocalRenderUnsigned = providers.gradleProperty("continuumLocalRenderUnsigned").orNull == "true"
require(!(continuumLocalStandalone && continuumLocalStandaloneQa)) {
    "Standalone owner release and disposable QA cannot be enabled together."
}
val continuumLocalSigningPath = providers.environmentVariable("CONTINUUM_LOCAL_SIGNING_FILE").orNull
val continuumLocalSigningPassword = providers.environmentVariable("CONTINUUM_LOCAL_SIGNING_PASSWORD").orNull
val continuumStandaloneVersionText = providers.gradleProperty("continuumStandaloneVersionCode").orNull
val continuumStandaloneVersion = continuumStandaloneVersionText?.toIntOrNull()
require(continuumStandaloneVersionText == null ||
    ((continuumLocalStandalone || continuumLocalStandaloneQa) && continuumStandaloneVersion != null &&
        continuumStandaloneVersion in 26..9999 &&
        continuumStandaloneVersionText == continuumStandaloneVersion.toString())) {
    "Standalone main-app versionCode must be 26..9999 and specified only in local mode."
}

// Installable QA variant: isolated package avoids debug-signature conflicts with an existing app.
val deviceAiPreview = providers.gradleProperty("deviceAiPreview").orNull == "true"
val deviceAiDesignPreview = providers.gradleProperty("deviceAiDesignPreview").orNull == "true"
val deviceAiFeaturePreview = providers.gradleProperty("deviceAiFeaturePreview").orNull == "true"
val deviceAiMentorPreview = providers.gradleProperty("deviceAiMentorPreview").orNull == "true"
// Owner-requested in-place update of the EXISTING signed Mentor Pair app. Never used by disposable previews.
val deviceAiOwnerSignedUpdate = providers.gradleProperty("deviceAiOwnerSignedUpdate").orNull == "true"
// Source-identical same-package MAIN APP QA only. Uses Android's disposable
// debug certificate; its APK is never an installable owner update.
val continuumOwnerLocalQa = providers.gradleProperty("continuumOwnerLocalQa").orNull == "true"
require(!continuumLocalStandaloneQa ||
    (deviceAiMentorPreview && !deviceAiOwnerSignedUpdate &&
        !continuumLocalStandalone && !continuumOwnerLocalQa &&
        mentorSigningPath.isNullOrBlank() && mentorSigningPassword.isNullOrBlank() &&
        continuumLocalSigningPath.isNullOrBlank() && continuumLocalSigningPassword.isNullOrBlank() &&
        continuumStandaloneVersion != null)) {
    "Disposable Continuum Lokal QA needs mentor features, a version and no owner signer."
}
// Fail closed before task execution: the QA package may only compile/test/lint.
// It must never be used to assemble any debug/release APK or bundle.
require(!continuumLocalStandaloneQa ||
    (gradle.startParameter.taskNames.isNotEmpty() &&
        gradle.startParameter.taskNames.all { requested ->
            requested.substringAfterLast(':') in
                setOf("testDebugUnitTest", "lintDebug", "compileDebugKotlin")
        })) {
    "Disposable Continuum Lokal QA allows only debug unit tests, lint and compilation."
}
require(!continuumLocalStandalone ||
    (deviceAiMentorPreview && !deviceAiOwnerSignedUpdate &&
        !continuumOwnerLocalQa &&
        mentorSigningPath.isNullOrBlank() && mentorSigningPassword.isNullOrBlank() &&
        !continuumLocalSigningPath.isNullOrBlank() && !continuumLocalSigningPassword.isNullOrBlank() &&
        continuumStandaloneVersion != null)) {
    "Standalone local main-app requires its own protected signer and isolated Mentor Pair feature set."
}

require(!continuumOwnerLocalQa ||
    (deviceAiMentorPreview && !deviceAiOwnerSignedUpdate &&
        mentorSigningPath.isNullOrBlank() && mentorSigningPassword.isNullOrBlank())) {
    "Owner local QA requires mentorpaired without owner credentials, never the signing/release lane."
}
// Persistent owner-local builds can increase versionCode for each genuine
// updated source while retaining the original package and owner certificate.
val continuumOwnerLocalVersionText = providers.gradleProperty("continuumOwnerLocalVersionCode").orNull
val continuumOwnerLocalVersion = continuumOwnerLocalVersionText?.toIntOrNull()
require(continuumOwnerLocalVersionText == null ||
    (continuumOwnerLocalVersion != null && continuumOwnerLocalVersion in 26..9999 &&
        continuumOwnerLocalVersionText == continuumOwnerLocalVersion.toString() &&
        (deviceAiOwnerSignedUpdate || continuumOwnerLocalQa))) {
    "Owner-local versionCode must be a canonical integer 26..9999 with original owner signing."
}
require(!continuumOwnerLocalQa || continuumOwnerLocalVersion != null) {
    "Main-app QA requires explicit source-candidate versionCode; no accidental old APK."
}
// Only the owner's Termux ARM64 preview build bypasses the Linux-x86 AIDL compiler.
val deviceAiMobileAidlFallback = providers.gradleProperty("deviceAiMobileAidlFallback").orNull == "true"
// Preserve the original two-variant invariant and enforce mutual exclusion for all previews.
require(!(deviceAiPreview && deviceAiMentorPreview)) {
    "Use only one preview package ID at a time."
}
require(listOf(deviceAiPreview, deviceAiMentorPreview, deviceAiDesignPreview, deviceAiFeaturePreview).count { it } <= 1) {
    "Use only one preview package ID at a time."
}
require(!deviceAiOwnerSignedUpdate || (deviceAiMentorPreview && !mentorSigningPath.isNullOrBlank() && !mentorSigningPassword.isNullOrBlank())) {
    "Owner signed update requires the existing Mentor Pair package and persistent signing credentials."
}

android {
    signingConfigs {
        if (continuumLocalStandalone) {
            create("continuumLocal") {
                storeFile = file(requireNotNull(continuumLocalSigningPath))
                storePassword = requireNotNull(continuumLocalSigningPassword)
                keyAlias = "continuum-local"
                keyPassword = requireNotNull(continuumLocalSigningPassword)
                storeType = "PKCS12"
            }
        }
        if (deviceAiMentorPreview && !continuumOwnerLocalQa &&
            !mentorSigningPath.isNullOrBlank() && !mentorSigningPassword.isNullOrBlank()
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
        applicationId = if (continuumLocalStandalone || continuumLocalStandaloneQa) "at.heinz.deviceai.continuumlocal" else "at.heinz.deviceai"
        minSdk = 26
        targetSdk = 36
        // Preview variants only: ensure Samsung recognises the new artifact
        // as an update. Main/production keeps its existing version.
        versionCode = if (deviceAiFeaturePreview) 9 else if (deviceAiDesignPreview) 7 else if (deviceAiMentorPreview) 15 else if (deviceAiPreview) 6 else 4
        versionName = if (deviceAiFeaturePreview) "0.3.14-dev" else if (deviceAiDesignPreview) "0.3.13-ui" else if (deviceAiMentorPreview) "0.3.12" else if (deviceAiPreview) "0.3.3" else "0.3.1"
        if (deviceAiOwnerSignedUpdate || continuumOwnerLocalQa) {
            // Same-package source QA and owner updates share exact version identity.
            // Only the owner update ever uses the original signing certificate.
            versionCode = continuumOwnerLocalVersion ?: 25
            versionName = if (continuumOwnerLocalVersion != null) "0.3.${continuumOwnerLocalVersion - 1}" else "0.3.24"
        }
        if (continuumLocalStandalone || continuumLocalStandaloneQa) {
            versionCode = requireNotNull(continuumStandaloneVersion)
            versionName = "0.3.${requireNotNull(continuumStandaloneVersion) - 1}"
        }
        testInstrumentationRunner = "at.heinz.deviceai.ContinuumVisualInstrumentation"
        manifestPlaceholders["mobileBuilderEnabled"] = deviceAiPreview || deviceAiMentorPreview || deviceAiDesignPreview || deviceAiFeaturePreview
        manifestPlaceholders["appLabel"] = when {
            deviceAiFeaturePreview -> "Device AI Continuum Test"
            deviceAiDesignPreview -> "Device AI Design"
            continuumOwnerLocalQa -> "Continuum QA NICHT INSTALLIEREN"
            continuumLocalStandaloneQa -> "Continuum Lokal QA NICHT INSTALLIEREN"
            continuumLocalStandalone -> "Continuum Lokal"
            deviceAiOwnerSignedUpdate -> "Continuum"
            deviceAiMentorPreview -> "Device AI Mentor Pair"
            deviceAiPreview -> "Device AI Preview"
            else -> "Device AI"
        }
    }

    buildTypes {
        getByName("debug") {
            if (continuumLocalStandalone) {
                signingConfig = signingConfigs.getByName("continuumLocal")
            } else if (continuumLocalStandaloneQa) {
                // Distinct application ID and default debug certificate: never an owner update.
                applicationIdSuffix = ".qa"
            } else if (deviceAiFeaturePreview) {
                applicationIdSuffix = ".featurepreview"
            } else if (deviceAiDesignPreview) {
                // Fresh, independent installation; never alters owner or earlier preview data.
                applicationIdSuffix = ".designpreview"
            } else if (deviceAiMentorPreview) {
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

    buildTypes.getByName("release") {
        if (continuumLocalStandalone) {
            signingConfig = signingConfigs.getByName("continuumLocal")
        }
    }

    sourceSets {
        getByName("main") {
            if (deviceAiMobileAidlFallback) {
                java.srcDir("src/termux/java")
            }
        }
    }

    buildFeatures {
        // Regular variants retain the canonical AIDL generator and Shizuku ABI.
        aidl = !deviceAiMobileAidlFallback
        buildConfig = true
    }

    testOptions { unitTests.isIncludeAndroidResources = continuumUiWidgetTests }
    if (continuumUiWidgetTests) sourceSets.getByName("test").java.srcDir("src/uiTest/java")

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
    if (continuumUiWidgetTests) {
        // Runtime API 28 and Robolectric 4.14.1's API 35 resource platform, resolved via Gradle's proxy/cache.
        uiTestAndroidSdk("org.robolectric:android-all-instrumented:9-robolectric-4913185-2-i7")
        uiTestResourceSdk("org.robolectric:android-all-instrumented:15-robolectric-12650502-i7")
        testImplementation("org.robolectric:robolectric:4.14.1")
    }
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303") // Android JSON API implementation for actual JVM fetch tests.
}

