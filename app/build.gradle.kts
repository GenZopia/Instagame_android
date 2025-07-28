plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.genzopia.Instagame"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.genzopia.Instagame"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
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

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
}

dependencies {
    implementation(platform(libs.firebase.bom)) // Firebase BOM centralized

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // --- CAMERA X DEPENDENCIES (add if not already present) ---
    implementation ("androidx.camera:camera-core:1.4.2")
    implementation ("androidx.camera:camera-camera2:1.4.2")
    implementation ("androidx.camera:camera-lifecycle:1.4.2")
    implementation ("androidx.camera:camera-video:1.2.2")
    implementation ("androidx.camera:camera-view:1.4.2")
    implementation ("androidx.camera:camera-extensions:1.4.2")
    // ---------------------------------------------------------

    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.database)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage.ktx)
    implementation(libs.firebase.analytics)

    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    implementation(libs.circleimageview)
    implementation(libs.subsampling.scale.image.view)
    implementation(libs.play.services.auth)

    val mozillaComponentsVersion = "138.0.4"
    val geckoVersionStable = "112.2.0"

    implementation("org.mozilla.components:browser-state:$mozillaComponentsVersion")
    implementation("org.mozilla.components:concept-engine:$mozillaComponentsVersion")
    implementation("org.mozilla.components:browser-engine-gecko:$geckoVersionStable")
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    implementation(libs.glide)
    annotationProcessor("com.github.bumptech.glide:compiler:4.15.0")

    implementation("com.facebook.shimmer:shimmer:0.5.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.work:work-runtime:2.9.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.airbnb.android:lottie:6.4.0")
}
