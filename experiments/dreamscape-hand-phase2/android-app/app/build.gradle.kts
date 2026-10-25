plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.dreamscape.hand.phase21"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.dreamscape.hand.phase22.depth"
        minSdk = 24
        targetSdk = 35
        versionCode = 5
        versionName = "0.2.2.1-arm64-compact"
        // Vivo S30 is ARM64. Exclude x86/x86_64/32-bit native MediaPipe
        // binaries from a device-specific TEST build to reduce mobile data.
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    buildTypes { getByName("release") { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/handModel"))
}
dependencies {
    implementation("com.google.ar:core:1.56.0")
    implementation("com.google.mediapipe:tasks-vision:0.10.35")
}
// Reuse existing pinned model checked into the same repository. No second download.
val copyHandModel by tasks.registering(Copy::class) {
    from(rootProject.file("../../../vendor/mediapipe/hand_landmarker.task"))
    into(layout.buildDirectory.dir("generated/handModel"))
}
tasks.named("preBuild").configure { dependsOn(copyHandModel) }
