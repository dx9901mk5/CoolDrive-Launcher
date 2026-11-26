plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.dxcar.launcher"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.dxcar.launcher"
    minSdk = 28
    targetSdk = 36
    versionCode = 42
    versionName = "1.8.3"
  }

  bundle {
    language {
      enableSplit = false
    }
  }

  flavorDimensions += "channel"
  productFlavors {
    create("direct") {
      dimension = "channel"
      buildConfigField("boolean", "ENABLE_GITHUB_AUTO_UPDATE", "true")
    }
    create("play") {
      dimension = "channel"
      buildConfigField("boolean", "ENABLE_GITHUB_AUTO_UPDATE", "false")
    }
  }

  signingConfigs {
    create("dxcar") {
      storeFile = file("signing/dxcar.p12")
      storePassword = "dxcar2026"
      keyAlias = "dxcar"
      keyPassword = "dxcar2026"
    }
  }

  buildTypes {
    debug {
      signingConfig = signingConfigs.getByName("dxcar")
    }
    release {
      isMinifyEnabled = false
      signingConfig = signingConfigs.getByName("dxcar")
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      ndk {
        debugSymbolLevel = "SYMBOL_TABLE"
      }
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
  implementation(libs.androidx.fragment.ktx)

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

  // Google Play Billing (7-day full-feature trial + NT$500 one-time lifetime unlock)
  implementation(libs.play.billing.ktx)

  // Coroutines & JSON Serialization (for Bluetooth SPP Companion Protocol & Preferences)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
