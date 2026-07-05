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
        versionCode = 10
        versionName = "10.0"

        val fileUploadApiKey: String = (project.findProperty("file_upload_api_key") as String?) ?: ""
        buildConfigField("String", "FILE_UPLOAD_API_KEY", "\"$fileUploadApiKey\"") // kept for legacy; no longer used in APK

        val amplitudeApiKey: String = (project.findProperty("amplitude_api_key") as String?) ?: ""
        buildConfigField("String", "AMPLITUDE_API_KEY", "\"$amplitudeApiKey\"")

        val gatewayBaseUrl: String = (project.findProperty("gateway_base_url") as String?) ?: ""
        buildConfigField("String", "GATEWAY_BASE_URL", "\"$gatewayBaseUrl\"")

        val gatewayApiKey: String = (project.findProperty("gateway_api_key") as String?) ?: ""
        buildConfigField("String", "GATEWAY_API_KEY", "\"$gatewayApiKey\"")

        // Direct Cloudflare file-upload worker URL. Large videos are uploaded straight to
        // this worker (which has no request-size limit) instead of through the gateway,
        // because Cloud Run rejects any request body larger than 32 MiB with HTTP 413.
        val workerUploadUrl: String = (project.findProperty("worker_upload_url") as String?)
            ?: "https://file-uploader.genzopia.workers.dev"
        buildConfigField("String", "WORKER_UPLOAD_URL", "\"$workerUploadUrl\"")

        // Base domain for deep linking - change this to update deep links everywhere
        buildConfigField("String", "BASE_DOMAIN", "\"www.genzopia.com\"")
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Comment out for JUnit 4 tests
                // it.useJUnitPlatform()
            }
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

    // Accompanist Pager (kept for any legacy usages)
    implementation(libs.accompanist.pager)

    // CameraX
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.video)
    implementation(libs.camera.view)
    implementation(libs.camera.extensions)

    // Firebase (versions managed by Firebase BOM above)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.messaging)
    // firebase-config removed: app config now fetched via gateway /app-config endpoint

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Lifecycle
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.process)

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
    implementation(libs.amplitude.analytics)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    
    // JUnit 5 (Jupiter) for property-based testing with jqwik
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    
    // Robolectric JUnit 5 extension
    testImplementation("tech.apter.junit5.jupiter:robolectric-extension:0.8.0")
    
    // jqwik for property-based testing
    testImplementation("net.jqwik:jqwik:1.9.3")
    testImplementation("net.jqwik:jqwik-kotlin:1.9.3")
    
    // JUnit 5 Platform for running jqwik tests
    testImplementation("org.junit.platform:junit-platform-launcher:1.11.4")



}