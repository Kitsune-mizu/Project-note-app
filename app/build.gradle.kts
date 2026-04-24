import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.android.alpha"
    compileSdk = 36 // Target Android 16 (currently Android U/UpsideDownCake or latest available)

    defaultConfig {
        applicationId = "com.android.alpha"
        minSdk = 24 // Android 7.0 (Nougat)
        targetSdk = 36 // Target the latest SDK (36)
        versionCode = 1
        // Vesion code di ubah kalau rilis di playstore setiap update
        versionName = "4.3.7.26"
        // Major = 1.0.0 > 2.0.0 perubahan total
        // Minor = 1.0.0 > 1.1.0 penambahan fitur
        // Patch = 1.0.0 > 1.0.1 perbaikan kecil
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // GeminiAI
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")

        if (localPropertiesFile.exists()) {
            localProperties.load(FileInputStream(localPropertiesFile))
        }

        val geminiApiKey = localProperties.getProperty("GEMINI_API_KEY") ?: ""

        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false // Disabling obfuscation for now
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

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {

    // --- Core Android ---
    implementation(libs.androidx.core.ktx.v1131)
    implementation(libs.appcompat)
    implementation(libs.material.v1120)
    implementation(libs.constraintlayout)

    // RecyclerView
    implementation(libs.androidx.recyclerview)

    // --- Lifecycle & LiveData / ViewModel ---
    implementation(libs.androidx.lifecycle.viewmodel.ktx.v270)
    implementation(libs.androidx.lifecycle.livedata.ktx.v270)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // --- Navigation ---
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Activity & Fragment KTX
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)

    // --- ROOM Database ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    annotationProcessor(libs.androidx.room.compiler.v261)

    // --- DataStore ---
    implementation(libs.androidx.datastore.preferences)

    // --- Google Services ---
    implementation(libs.play.services.location.v2101)
    implementation(libs.play.services.maps.v1820)

    // --- Third-party Libraries ---
    implementation(libs.lottie.v660)
    implementation(libs.glide.v4151)
    annotationProcessor(libs.compiler.v4151)

    implementation(libs.okhttp)
    implementation(libs.gson.v2101)

    // Biometrics
    implementation(libs.androidx.biometric)

    // ---- UI Helpers ---
    implementation(libs.androidx.gridlayout)
    implementation(libs.androidx.swiperefreshlayout)

    // --- OSMDroid Maps ---
    implementation(libs.osmdroid.android)
    implementation(libs.osmdroid.wms.v6110)

    // --- Testing ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit.v121)
    androidTestImplementation(libs.androidx.espresso.core.v361)

    implementation(libs.shimmer)
    implementation(libs.yalantis.ucrop)

    implementation(libs.yukuku.ambilwarna)

    // --- GeminiAI ---
    // DrawerLayout
    implementation(libs.androidx.drawerlayout)
    // CardView
    implementation(libs.androidx.cardview)
}