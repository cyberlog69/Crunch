import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.legacy.kapt)
}

// Release signing is optional. Values come from keystore.properties (ignored by
// git) or from CRUNCH_* environment variables, so CI can sign without a file
// and a fresh clone can still run `assembleRelease` unsigned.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun signingValue(property: String, environment: String): String? =
    (System.getenv(environment) ?: keystoreProperties.getProperty(property))
        ?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "CRUNCH_KEYSTORE_FILE")
val releaseStorePassword = signingValue("storePassword", "CRUNCH_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "CRUNCH_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "CRUNCH_KEY_PASSWORD")
val hasReleaseSigning = releaseStoreFile != null

android {
    namespace = "com.kappi.crunch"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.kappi.crunch"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.0.3"

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"))
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    // Per-ABI APKs keep the download small: each bundle of native binaries
    // (yt-dlp/Python, FFmpeg, aria2c) is only shipped for one architecture.
    // The universal APK is still built for adb install / sideloading.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
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

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.icons.extended)

  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Room (local DB for download history)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  kapt(libs.androidx.room.compiler)
  kapt("org.jetbrains.kotlin:kotlin-metadata-jvm:${libs.versions.kotlin.get()}")

  // WorkManager (background download tasks)
  implementation(libs.androidx.work.runtime.ktx)

  // Coil (thumbnail loading)
  implementation(libs.coil.compose)

  // Gson
  implementation(libs.gson)

  // Kotlinx Serialization
  implementation(libs.kotlinx.serialization.json)

  // yt-dlp native Android engine + FFmpeg + Aria2
  implementation(libs.youtubedl.library)
  implementation(libs.youtubedl.ffmpeg)
  implementation(libs.youtubedl.aria2c)
}

// Keep KAPT/Room temp files inside the project instead of the system temp
// directory. Derived from the project location, so the build stays portable.
val gradleTmpDir = project.rootDir.resolve(".gradle_tmp").absolutePath.replace('\\', '/')
gradleTmpDir.let { tmp ->
    System.setProperty("java.io.tmpdir", tmp)
    System.setProperty("org.sqlite.tmpdir", tmp)
}

tasks.withType<org.jetbrains.kotlin.gradle.internal.KaptWithoutKotlincTask>().configureEach {
    kaptProcessJvmArgs.add("-Djava.io.tmpdir=$gradleTmpDir")
    kaptProcessJvmArgs.add("-Dorg.sqlite.tmpdir=$gradleTmpDir")
}
