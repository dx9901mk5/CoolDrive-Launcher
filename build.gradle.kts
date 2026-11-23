// Top-level build file for DX-Car-Launcher
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.serialization) apply false
}

// Redirect build outputs outside synced cloud folders / non-ASCII or space paths on Windows
// to avoid Google Drive sync locks and speed up incremental Gradle builds.
if (rootDir.absolutePath.any { it.code > 127 } || rootDir.absolutePath.contains(' ')) {
  val fastBuildRoot = File(System.getProperty("java.io.tmpdir"), "DX-Car-Launcher-build")
  layout.buildDirectory.set(File(fastBuildRoot, "root"))
  subprojects {
    layout.buildDirectory.set(File(fastBuildRoot, name))
  }
}
