// Host-only compile/test harness for Continuum (no Android SDK available in this environment).
plugins { kotlin("jvm") version "2.4.0" }
val repo = file(providers.gradleProperty("repo").get())
val libs = file(providers.gradleProperty("libs").get())
val mainList = file(providers.gradleProperty("mainList").get()).readLines().filter { it.isNotBlank() }
val testList = file(providers.gradleProperty("testList").get()).readLines().filter { it.isNotBlank() }
kotlin { jvmToolchain(21); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17); freeCompilerArgs.addAll("-nowarn", "-Xnullability-annotations=@android.annotation:ignore", "-Xnullability-annotations=@androidx.annotation:ignore") } }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
sourceSets {
  main {
    kotlin.setSrcDirs(emptyList<Any>()); kotlin.srcDir(repo.resolve("app/src/main/java")); kotlin.srcDir(file("stubs"))
    kotlin.include(mainList.map { it.removePrefix("app/src/main/java/") } + listOf("at/heinz/deviceai/HostStubs.kt", "at/heinz/deviceai/IDeviceShell.java", "androidx/**", "com/google/ai/**", "at/heinz/deviceai/localmodel/LiteRtLmHostRuntime.kt"))
    java.setSrcDirs(listOf(file("stubs")))
  }
  test { kotlin.setSrcDirs(emptyList<Any>()); kotlin.srcDir(repo.resolve("app/src/test/java")); kotlin.include(testList.map { it.removePrefix("app/src/test/java/") })
         resources.setSrcDirs(listOf(repo.resolve("app/src/test/resources"))) }
}
val androidAll = files(libs.resolve("android-all.jar"))
val libJars = fileTree(libs) { include("*.jar"); exclude("android-all.jar", "*.bak") }
dependencies {
  compileOnly(androidAll); compileOnly(libJars)
  testCompileOnly(androidAll); testImplementation(libJars)
  implementation("org.json:json:20240303")
  testImplementation("junit:junit:4.13.2")
}
tasks.test { useJUnit(); workingDir = repo.resolve("app"); maxHeapSize = "3g"; ignoreFailures = true
  reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-xml"))
  testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT } }
