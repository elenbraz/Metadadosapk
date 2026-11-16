plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val admobAppId = (project.findProperty("admobAppId") as String?)?.takeIf { it.isNotBlank() }
    ?: "ca-app-pub-3940256099942544~3347511713"
val idDoApp = (project.findProperty("appId") as String?)?.takeIf { it.isNotBlank() }
    ?: "com.exemplo.limpador"

android {
    namespace = "com.exemplo.limpador"
    compileSdk = 34
    defaultConfig {
        applicationId = idDoApp
        manifestPlaceholders["admobAppId"] = admobAppId
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.media3:media3-common:1.4.1")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-transformer:1.4.1")
    implementation("androidx.media3:media3-effect:1.4.1")
    implementation("com.google.guava:guava:33.0.0-android")
    implementation("com.google.android.gms:play-services-ads:22.6.0")
}
