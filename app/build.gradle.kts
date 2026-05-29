plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.genzopia.Instagame"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.genzopia.Instagame"
        minSdk = 24
        targetSdk = 36
        versionCode = 5
        versionName = "5.0"

        val fileUploadApiKey: String = (project.findProperty("file_upload_api_key") as String?) ?: ""
        buildConfigField("String", "FILE_UPLOAD_API_KEY", "\"$fileUploadApiKey\"")

        val videoProcessorApiKey: String = (project.findProperty("video_processor_api_key") as String?) ?: ""
        buildConfigField("String", "VIDEO_PROCESSOR_API_KEY", "\"$videoProcessorApiKey\"")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
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

    // FIX: kotlinOptions { jvmTarget = "17" } is an error in Kotlin 2.2.0.
    // Must use the compilerOptions DSL instead.
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
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
    // Firebase BOM — manages all Firebase artifact versions
    implementation(platform(libs.firebase.bom))

    // Jetpack Compose BOM — manages all Compose artifact versions.
    // MUST be declared with platform() so Gradle treats it as a BOM,
    // not a regular jar. Without this wrapper the BOM has no effect
    // and every version-less Compose artifact fails to resolve.
    implementation(platform(libs.compose.bom))

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.core.splashscreen)
    implementation(libs.androidx.core.ktx)

    // Jetpack Compose
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material3)
    implementation(libs.material.icons.core)
    implementation(libs.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Paging 3 with Compose
    implementation(libs.paging.runtime.ktx)
    implementation(libs.paging.compose)

    // Coil for image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    // Media3 ExoPlayer
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.datasource)

    // CameraX
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.video)
    implementation(libs.camera.view)
    implementation(libs.camera.extensions)

    // Firebase (versions managed by Firebase BOM above)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.database)
    implementation(libs.firebase.firestore)

    // Lifecycle
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)

    // Navigation
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    implementation(libs.circleimageview)
    implementation(libs.subsampling.scale.image.view)
    implementation(libs.play.services.auth)
    implementation(libs.recyclerview)
    implementation(libs.swiperefreshlayout)

    // Glide
    implementation(libs.glide)
    annotationProcessor(libs.compiler)
    implementation(libs.okhttp3.integration)

    implementation(libs.shimmer)
    implementation(libs.ccp)
    implementation(libs.libphonenumber)
    implementation(libs.okhttp)
    implementation(libs.work.runtime)
    implementation(libs.work.runtime.ktx)
    implementation(libs.lottie)
    implementation(libs.gson)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
}