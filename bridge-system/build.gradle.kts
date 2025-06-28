plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

    id("im.agg.android12-system-jars") version "1.0.2"
}

android {
    namespace = "com.penumbraos.bridge_system"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.penumbraos.bridge_system"
        minSdk = 32
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}