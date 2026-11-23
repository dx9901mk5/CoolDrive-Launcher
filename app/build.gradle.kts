plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.cooldrive.launcher"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.cooldrive.launcher"
    minSdk = 28
    targetSdk = 35
    versionCode = 5
    versionName = "1.2.1"
  }

  signingConfigs {
    create("cooldrive") {
      storeFile = file("signing/cooldrive.p12")
      storePassword = "cooldrive2026"
      keyAlias = "cooldrive"
      keyPassword = "cooldrive2026"
    }
  }

  buildTypes {
    debug {
      signingConfig = signingConfigs.getByName("cooldrive")
    }
    release {
      isMinifyEnabled = false
      signingConfig = signingConfigs.getByName("cooldrive")
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
      excludes += "META-INF/DEPENDENCIES"
    }
  }

  testOptions {
    unitTests.all {
      it.jvmArgs("-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8")
    }
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.lifecycle.service)
  implementation(libs.androidx.savedstate.ktx)
  implementation(libs.androidx.activity.compose)

  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  debugImplementation(libs.androidx.compose.ui.tooling)

  // Hardware-accelerated OpenGL MapLibre Engine (Zero API Key)
  implementation(libs.maplibre.android.opengl)

  // Networking & Image loading
  implementation(libs.okhttp)
  implementation(libs.coil.compose)

  // Coroutines & JSON Serialization (for Bluetooth SPP Companion Protocol & Preferences)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
