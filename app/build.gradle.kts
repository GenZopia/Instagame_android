plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.genzopia.Instagame"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.genzopia.Instagame"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Expose file upload API key from gradle.properties into BuildConfig
        val fileUploadApiKey: String = (project.findProperty("file_upload_api_key") as String?) ?: ""
        buildConfigField("String", "FILE_UPLOAD_API_KEY", "\"$fileUploadApiKey\"")
    }

    buildFeatures {
        viewBinding = true
        // Enable generation of BuildConfig so buildConfigField works
        buildConfig = true
        // Enable Jetpack Compose
        compose = true
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

    // Jetpack Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    
    // Paging 3 with Compose
    implementation("androidx.paging:paging-runtime-ktx:3.2.1")
    implementation("androidx.paging:paging-compose:3.2.1")
    
    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-video:2.5.0")
    
    // Media3 ExoPlayer (latest stable version)
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-datasource:1.3.1")
    
    // Accompanist for Pager
    implementation("com.google.accompanist:accompanist-pager:0.32.0")

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
    
    // REMOVED: Old ExoPlayer causes conflicts with Media3
    // If you need video playback, use Media3 (already added above)
    // implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    implementation(libs.glide)
    annotationProcessor("com.github.bumptech.glide:compiler:4.15.0")

    implementation("com.facebook.shimmer:shimmer:0.5.0")
    // Country picker with flags + search
    implementation("com.hbb20:ccp:2.7.3")
    // Google's libphonenumber for parsing/validation/formatting
    implementation("com.googlecode.libphonenumber:libphonenumber:8.13.30")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    
    // Property-based testing dependencies
    testImplementation("net.jqwik:jqwik:1.8.2")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.robolectric:robolectric:4.11.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.work:work-runtime:2.9.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.airbnb.android:lottie:6.4.0")
    implementation("com.google.code.gson:gson:2.10.1")
}
