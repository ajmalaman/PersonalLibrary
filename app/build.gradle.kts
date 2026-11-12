plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.personallibrary"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.ajmal.personallibrary"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Existing (version catalog)
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material) // Material Design 3

    // Room (Java uses annotationProcessor, not kapt/ksp)
    implementation("androidx.room:room-runtime:2.8.4")
    annotationProcessor("androidx.room:room-compiler:2.8.4")

    // Lifecycle: ViewModel + LiveData so the UI updates automatically from Room
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.9.4")
    implementation("androidx.lifecycle:lifecycle-livedata:2.9.4")

    // Modern list UI
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.cardview:cardview:1.0.0")

    // Book cover images
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Splash screen API (clean dark launch experience)
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}