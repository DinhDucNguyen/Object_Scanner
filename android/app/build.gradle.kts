import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.duc.objectlanguage"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.duc.objectlanguage"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Build config fields for server configuration
        buildConfigField("String", "SERVER_IP", "\"${localProperties.getProperty("SERVER_IP", "192.168.1.100")}\"")
        buildConfigField("String", "SERVER_PORT", "\"${localProperties.getProperty("SERVER_PORT", "8000")}\"")
    }

    buildTypes {
        debug {
            manifestPlaceholders["usesCleartextTraffic"] = "true"
            buildConfigField("String", "SERVER_SCHEME", "\"${localProperties.getProperty("SERVER_SCHEME", "http")}\"")
        }
        release {
            isMinifyEnabled = true
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            buildConfigField("String", "SERVER_SCHEME", "\"${localProperties.getProperty("SERVER_SCHEME", "https")}\"")
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

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    androidResources {
        noCompress.add("tflite")
    }

}

dependencies {
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.6")

    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    implementation("com.google.mlkit:object-detection:17.0.1")
    implementation("com.google.mlkit:image-labeling:17.0.8")


    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Glide (image loading)
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Lottie animations
    implementation("com.airbnb.android:lottie:6.3.0")

    // MPAndroidChart (Wave 3 - Visual Analytics) 
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // DataStore Preferences (Wave 5 - Streaks)
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // WorkManager (Wave 5 - Background Notifications)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Konfetti (Wave 5 - Celebration Animations)
    implementation("nl.dionsegijn:konfetti-xml:2.0.4")

    // UCrop - Image cropping
    implementation("com.github.yalantis:ucrop:2.2.8")

    // Shimmer loading effect
    implementation("com.facebook.shimmer:shimmer:0.5.0")

    // Encrypted SharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
