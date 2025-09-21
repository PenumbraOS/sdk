plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.systemjars)
}

android {
    namespace = "com.penumbraos.bridge.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 32
    }

    sourceSets {
        named("main") {
            java {
                srcDir("java")
            }
            aidl {
                srcDir("aidl")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        aidl = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
}