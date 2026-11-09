plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingKeystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
val signingStorePassword = System.getenv("SIGNING_STORE_PASSWORD")
val signingKeyAlias = System.getenv("SIGNING_KEY_ALIAS")
val signingKeyPassword = System.getenv("SIGNING_KEY_PASSWORD")
val releaseSigningAvailable = listOf(
    signingKeystorePath,
    signingStorePassword,
    signingKeyAlias,
    signingKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.royalplus.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.royalplus.launcher"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file(signingKeystorePath ?: "missing-release-keystore.jks")
            storePassword = signingStorePassword.orEmpty()
            keyAlias = signingKeyAlias.orEmpty()
            keyPassword = signingKeyPassword.orEmpty()
        }
    }

    buildTypes {
        getByName("release") {
            // PR/manual validation can use the debug certificate; main releases
            // always use the repository's private signing key.
            signingConfig = signingConfigs.getByName(
                if (releaseSigningAvailable) "release" else "debug"
            )
            if (!releaseSigningAvailable) {
                // Keep PR test installs side-by-side with the user's production app.
                applicationIdSuffix = ".test"
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    buildFeatures {
        compose = true
    }

    splits {
        abi {
            isEnable = project.findProperty("enableAbiSplits")?.toString() == "true"
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation(files("libs/sherpa-onnx-2.0.1.aar"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation-core")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
