import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.ksp)
}

android {

  namespace = "com.wanderwildwood.mimidoku"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.wanderwildwood.mimidoku"
    // The Kompakt runs 28; nothing here needs anything newer.
    minSdk = 28
    targetSdk = 36
    versionCode = 101
    versionName = "1.0.1"
  }

  // A real keystore in signing/ signs every build type when it is present, so the
  // very first install is already release-signed and a later update can never hit
  // INSTALL_FAILED_UPDATE_INCOMPATIBLE. It is gitignored. Nothing is checked in as
  // a fallback: this repository has no remotes, so there is nobody to clone it.
  val signingPropertiesFile = rootProject.file("signing/signing.properties")
  val realSigningConfig = if (signingPropertiesFile.isFile) {
    val signingProperties = Properties().apply {
      signingPropertiesFile.inputStream().use(::load)
    }
    signingConfigs.create("real") {
      storeFile = rootProject.file("signing/signing.keystore")
      storePassword = signingProperties.getProperty("STORE_PASSWORD")
      keyAlias = signingProperties.getProperty("KEY_ALIAS")
      keyPassword = signingProperties.getProperty("KEY_PASSWORD")
    }
  } else {
    null
  }

  buildTypes {
    getByName("debug") {
      isMinifyEnabled = false
      realSigningConfig?.let { signingConfig = it }
    }
    getByName("release") {
      // Off on purpose. R8 cannot be signed off from this desk - a release APK that
      // assembles proves nothing about one that runs. Turn it on when there is a
      // Kompakt to install the result on and hear it play a book.
      isMinifyEnabled = false
      realSigningConfig?.let { signingConfig = it }
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlin {
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
  }

  buildFeatures {
    compose = true
    // The About box has to be able to say which build it is.
    buildConfig = true
  }

  sourceSets {
    named("main") {
      kotlin.srcDir("src/main/kotlin")
    }
  }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime)
  implementation(libs.androidx.activity.compose)

  implementation(platform(libs.compose.bom))
  implementation(libs.compose.ui)
  implementation(libs.compose.material3)
  implementation(libs.compose.ui.tooling.preview)
  debugImplementation(libs.compose.ui.tooling)

  implementation(libs.media3.exoplayer)
  implementation(libs.media3.session)
  implementation(libs.media3.common)

  implementation(libs.room.runtime)
  implementation(libs.room.ktx)
  ksp(libs.room.compiler)

  testImplementation(libs.junit)
}
