plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)

    alias(libs.plugins.systemjars)
}

android {
    namespace = "com.penumbraos.bridge_system"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.penumbraos.bridge_system"
        minSdk = 32
        targetSdk = 35
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = project.findProperty("versionName") as String? ?: "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    buildFeatures {
        aidl = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    sourceSets {
        named("main") {
            java {
                srcDir("${project.rootDir}/bridge-shared/java")
            }
            aidl {
                srcDir("${project.rootDir}/bridge-shared/aidl")
            }
        }
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
    implementation(libs.okhttp)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.appprocessmocks)
    implementation(libs.dnsjava)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}