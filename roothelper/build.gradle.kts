plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.dxcar.roothelper"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.dxcar.roothelper"
    minSdk = 28
    targetSdk = 36
    versionCode = 1
    versionName = "1.0.0"
  }

  signingConfigs {
    create("dxcar") {
      storeFile = file("../app/signing/dxcar.p12")
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
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
  }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.okhttp)
}
