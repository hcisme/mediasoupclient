import java.util.Properties
import kotlin.apply

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val debugBaseUrl: String by lazy {
    localProperties.getProperty("debug.base.url", System.getenv("DEBUG_BASE_URL"))
}
val releaseBaseUrl: String by lazy {
    localProperties.getProperty("release.base.url", System.getenv("RELEASE_BASE_URL"))
}

android {
    namespace = "io.github.hcisme.mediasoupclient"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "io.github.hcisme.mediasoupclient"
        minSdk = 24
        targetSdk = 36
        versionCode = 4
        versionName = "1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("RELEASE_STORE_FILE")
            storePath?.let { path ->
                storeFile = file(path)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", debugBaseUrl)
        }

        release {
            buildConfigField("String", "BASE_URL", releaseBaseUrl)
            if (signingConfigs.getByName("release").storeFile?.exists() == true) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.accompanist.permissions)
    implementation(libs.socket.io.client)
    implementation(libs.kotlinx.serialization.json.jvm)
    implementation(libs.libmediasoup.android)
    implementation(libs.libwebrtc.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}