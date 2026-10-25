plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.tgmedia.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tgmedia.tv"
        minSdk = 26
        targetSdk = 35
        versionCode = 45
        versionName = "1.0.44"

        val envApiId = System.getenv("TG_API_ID")?.trim()
        val tgApiId = if (!envApiId.isNullOrEmpty() && envApiId.toIntOrNull() != null) envApiId else "279933"

        val envApiHash = System.getenv("TG_API_HASH")?.trim()
        val tgApiHash = if (!envApiHash.isNullOrEmpty()) envApiHash else "1c32273df61c79fc3568bceb0bfb73a9"

        buildConfigField("int", "TG_API_ID", tgApiId)
        buildConfigField("String", "TG_API_HASH", "\"$tgApiHash\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val envStoreFile = System.getenv("KEYSTORE_FILE")?.trim()
            storeFile = if (!envStoreFile.isNullOrEmpty()) file(envStoreFile) else file("release.keystore")
            val envStorePass = System.getenv("KEYSTORE_PASSWORD")?.trim()
            storePassword = if (!envStorePass.isNullOrEmpty()) envStorePass else "cybermasters2026"
            val envKeyAlias = System.getenv("KEY_ALIAS")?.trim()
            keyAlias = if (!envKeyAlias.isNullOrEmpty()) envKeyAlias else "cmtgtv"
            val envKeyPass = System.getenv("KEY_PASSWORD")?.trim()
            keyPassword = if (!envKeyPass.isNullOrEmpty()) envKeyPass else "cybermasters2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    sourceSets {
        getByName("main") {
            // Директория скомпилированных C++ библиотек TDLib (libtdjni.so)
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // Core & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose & Compose for Android TV
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.tv.foundation)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Media3 ExoPlayer for TV 10-foot UI
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)

    // Image loading & QR Code generation
    implementation(libs.coil.compose)
    implementation(libs.zxing.core)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Debug tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
}
