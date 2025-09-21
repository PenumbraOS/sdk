plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.systemjars)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "com.penumbraos.sdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 32

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        resources.excludes += "META-INF/DEPENDENCIES"
    }

    buildFeatures {
        aidl = true
    }
}

configurations.all {
    exclude(group = "com.google.guava", module = "listenablefuture")
}

dependencies {
    implementation(project(":bridge-shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.okhttp)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.kotlinx.datetime)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation(libs.kotlinx.coroutines.test)
    testImplementation(kotlin("test"))
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "com.penumbraos"
            artifactId = "sdk"
            version = "0.1.0"

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}