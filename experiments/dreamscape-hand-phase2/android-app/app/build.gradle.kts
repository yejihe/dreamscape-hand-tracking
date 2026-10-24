plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.dreamscape.hand.phase21"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.dreamscape.hand.phase21"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.2.1-spike"
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
