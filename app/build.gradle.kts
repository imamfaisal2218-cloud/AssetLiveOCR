plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android { namespace = "com.imam.assetliveocr"; compileSdk = 36
    defaultConfig { applicationId = "com.imam.assetliveocr"; minSdk = 23; targetSdk = 36; versionCode = 2; versionName = "0.2" }
}

dependencies {
    val camera = "1.6.2"
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.camera:camera-camera2:$camera")
    implementation("androidx.camera:camera-core:$camera")
    implementation("androidx.camera:camera-lifecycle:$camera")
    implementation("androidx.camera:camera-view:$camera")
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
