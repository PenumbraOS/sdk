plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.penumbraos.esim"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.penumbraos.esim"
        minSdk = 32
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    packaging {
        jniLibs {
            // Required for binary to be accessible
            useLegacyPackaging = true

            keepDebugSymbols += "**/libgadget.config.so"
            keepDebugSymbols += "**/libgadget.script.so"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.kotlinx.cli)
    implementation(libs.kotlinx.serialization.json)
    implementation("com.penumbraos:sdk:0.1.0")
}