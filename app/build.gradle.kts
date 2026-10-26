plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.offlineimagegen"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.offlineimagegen"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.documentfile:documentfile:1.0.1")

    // MediaPipe Image Generator - fix protobuf conflict
    implementation("com.google.mediapipe:tasks-vision-image-generator:0.10.26.1") {
        exclude(group = "com.google.protobuf", module = "protobuf-javalite")
    }
    implementation("com.google.protobuf:protobuf-java:3.25.3")
}
