plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val buildNummer = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "de.hamster82.pvdashboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.hamster82.pvdashboard"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNummer
        versionName = "1.0.$buildNummer"
    }

    // Fester Signierschlüssel, damit sich neue Versionen über die alte installieren lassen (Verlauf bleibt erhalten)
    signingConfigs {
        create("fest") {
            storeFile = file("pvdashboard.keystore")
            storePassword = "pvdashboard"
            keyAlias = "pvdashboard"
            keyPassword = "pvdashboard"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fest")
        }
        debug {
            signingConfig = signingConfigs.getByName("fest")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.04.00")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
