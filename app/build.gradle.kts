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
        versionCode = 4
        versionName = "4.0"

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
    implementation(libs.core.splashscreen)

    // Jetpack Compose BOM
    implementation(libs.compose.bom)
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    
    // Paging 3 with Compose
    implementation(libs.paging.runtime.ktx)
    implementation(libs.paging.compose)
    
    // Coil for image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    
    // Media3 ExoPlayer (latest stable version)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.datasource)
    
    // Accompanist for Pager
    implementation(libs.accompanist.pager)

    // --- CAMERA X DEPENDENCIES (add if not already present) ---
    implementation (libs.camera.core)
    implementation (libs.camera.camera2)
    implementation (libs.camera.lifecycle)
    implementation (libs.camera.video)
    implementation (libs.camera.view)
    implementation (libs.camera.extensions)
    // ---------------------------------------------------------

    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.database)
    implementation(libs.firebase.firestore)

    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    implementation(libs.circleimageview)
    implementation(libs.subsampling.scale.image.view)
    implementation(libs.play.services.auth)


    
    // REMOVED: Old ExoPlayer causes conflicts with Media3
    // If you need video playback, use Media3 (already added above)
    // implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    
    implementation(libs.recyclerview)
    implementation(libs.swiperefreshlayout)

    implementation(libs.glide)
    annotationProcessor(libs.compiler)
    implementation(libs.okhttp3.integration)

    implementation(libs.shimmer)
    // Country picker with flags + search
    implementation(libs.ccp)
    // Google's libphonenumber for parsing/validation/formatting
    implementation(libs.libphonenumber)

    implementation(libs.okhttp)
    implementation(libs.work.runtime)
    implementation(libs.work.runtime.ktx)
    implementation(libs.lottie)
    implementation(libs.gson)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)


}
