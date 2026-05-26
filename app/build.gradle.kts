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

        // Expose API keys from gradle.properties into BuildConfig.
        // gradle.properties is in .gitignore — keys never enter source control.
        val fileUploadApiKey: String = (project.findProperty("file_upload_api_key") as String?) ?: ""
        buildConfigField("String", "FILE_UPLOAD_API_KEY", "\"$fileUploadApiKey\"")

        val videoProcessorApiKey: String = (project.findProperty("video_processor_api_key") as String?) ?: ""
        buildConfigField("String", "VIDEO_PROCESSOR_API_KEY", "\"$videoProcessorApiKey\"")
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(platform(libs.firebase.bom)) // Firebase BOM centralized

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Jetpack Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    
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
    implementation("com.github.bumptech.glide:okhttp3-integration:4.15.0")

    implementation("com.facebook.shimmer:shimmer:0.5.0")
    // Country picker with flags + search
    implementation("com.hbb20:ccp:2.7.3")
    // Google's libphonenumber for parsing/validation/formatting
    implementation("com.googlecode.libphonenumber:libphonenumber:8.13.30")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.work:work-runtime:2.9.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.airbnb.android:lottie:6.4.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")

    // Robolectric for unit tests that need Android context
    testImplementation("org.robolectric:robolectric:4.12.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.5.0")

    // Instrumented (androidTest) dependencies
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test:core-ktx:1.5.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
