import java.util.Properties
import java.util.regex.Pattern

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.ksp)
  alias(libs.plugins.aboutlibraries.android)
}

android {

  namespace = "com.wanderwildwood.mimidoku"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.wanderwildwood.mimidoku"
    // The Kompakt runs 28; nothing here needs anything newer.
    minSdk = 28
    targetSdk = 36
    versionCode = 192
    versionName = "1.3.62"
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
      // The one thing that differs between a release built here and the one GitHub
      // publishes: AGP stamps the git revision into META-INF, and the build box works
      // from an rsync with no .git, so it writes NO_SUPPORTED_VCS_FOUND where the CI
      // runner writes the commit. Off, so the two have identical contents.
      vcsInfo {
        include = false
      }

      // Shrunk: unshrunk, the code alone was 28 MB of a 30 MB APK. A release APK that
      // assembles proves nothing about one that runs, so a shrunk build is heard playing a
      // book on a Kompakt before it is released.
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    named("test") {
      kotlin.srcDir("src/test/kotlin")
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
  implementation(libs.mmd)
  implementation(libs.compose.ui.tooling.preview)
  debugImplementation(libs.compose.ui.tooling)

  implementation(libs.media3.exoplayer)
  implementation(libs.media3.session)
  implementation(libs.media3.common)

  implementation(libs.room.runtime)
  implementation(libs.room.ktx)
  ksp(libs.room.compiler)

  implementation(libs.okhttp)

  testImplementation(libs.junit)
  // Android's org.json is a stub under unit tests; the places backup is JSON.
  testImplementation("org.json:json:20240303")
  testImplementation(libs.okhttp.mockwebserver)
}

// The Licenses page lists what the build actually packs: this plugin walks each variant's runtime
// dependencies and writes res/raw/aboutlibraries.json (one entry per artifact, with its POM's
// license). What Gradle cannot see (fonts, data sets, native code, desugaring) and license texts
// of our own choosing are in config/libraries and config/licenses. Offline: the build fetches
// nothing; the full texts come from res/raw and config/licenses. The app reads the file with
// shared-licences' LicencesJson.kt (no AboutLibraries runtime library).
aboutLibraries {
  offlineMode.set(true)
  collect {
    configPath.set(rootProject.file("config"))
    fetchRemoteLicense.set(false)
    fetchRemoteFunding.set(false)
    includePlatform.set(false)
  }
  export {
    excludeFields.addAll("funding")
  }
  library {
    mergePlatformArtifacts.set(true)
    // The page lists what the release APK carries. These come in only through
    // debugImplementation (Compose ui-tooling) and are not in the release runtime classpath.
    exclusionPatterns.addAll(
      listOf(
        "androidx.compose.ui:ui-tooling", "androidx.compose.ui:ui-tooling-data",
        "androidx.compose.material:material",
      ).map { Pattern.compile("^" + Regex.escape(it) + "$") },
    )
  }
}

// The build fails when a bundled library has no license, or a license with no full text to show
// (neither its own text from config/licenses nor a family text in res/raw). Mudita review, 4 Nov 2026.
val licenceRawTexts: List<String> = file("src/main/res/raw").listFiles().orEmpty().map { it.name.lowercase() }
tasks.matching { it.name.startsWith("prepareLibraryDefinitions") }.configureEach {
  val raw = licenceRawTexts
  inputs.property("licenceRawTexts", raw) // so the check reruns when a text is added or removed
  doLast {
    fun family(n: String): String? {
      val l = n.lowercase()
      return when {
        "classpath" in l -> "classpath"; "lgpl" in l || "lesser general" in l -> "lgpl"
        "gpl" in l || "general public licen" in l -> "gpl"; "unicode" in l -> "unicode"; "apache" in l -> "apache"
        "mpl" in l || "mozilla" in l -> "mpl"; "mit" in l.split(Regex("[^a-z]+")) -> "mit"; "bsd" in l -> "bsd"
        "ofl" in l || "open font" in l -> "ofl"; "cc-by" in l || "cc by" in l || "cc0" in l || "public domain" in l -> "none"
        else -> null
      }
    }
    val bad = mutableListOf<String>()
    outputs.files.asFileTree.matching { include("**/aboutlibraries.json") }.forEach { f ->
      @Suppress("UNCHECKED_CAST")
      val root = groovy.json.JsonSlurper().parse(f) as MutableMap<String, Any?>
      val lics = (root["licenses"] as? Map<String, Map<String, Any?>>).orEmpty()
      // A library's own license file (config/licenses, with its copyright line) stands in for
      // the bare family entry its POM names: the plugin merges config entries in, it never
      // takes one out, so a library would otherwise list "MIT" twice, once with no text.
      var changed = false
      (root["libraries"] as? List<MutableMap<String, Any?>>).orEmpty().forEach { lib ->
        val keys = (lib["licenses"] as? List<String>).orEmpty()
        fun fam(k: String) = family((lics[k]?.get("spdxId") ?: lics[k]?.get("name") ?: k).toString())
        fun hasText(k: String) = !(lics[k]?.get("content") as? String).isNullOrBlank()
        val own = keys.filter { hasText(it) }.mapNotNull { fam(it) }.toSet()
        val kept = keys.filterNot { !hasText(it) && fam(it) in own }
        if (kept != keys) { lib["licenses"] = kept; changed = true }
      }
      if (changed) f.writeText(groovy.json.JsonOutput.toJson(root))
      (root["libraries"] as? List<Map<String, Any?>>).orEmpty().forEach { lib ->
        val keys = (lib["licenses"] as? List<String>).orEmpty()
        if (keys.isEmpty()) bad += "${lib["uniqueId"]}: no license"
        keys.forEach { k ->
          val e = lics[k].orEmpty()
          if ((e["content"] as? String).isNullOrBlank()) {
            val fam = family((e["spdxId"] ?: e["name"] ?: k).toString())
            if (fam == null || (fam != "none" && raw.none { fam in it }))
              bad += "${lib["uniqueId"]}: no text for ${e["name"] ?: k}"
          }
        }
      }
    }
    if (bad.isNotEmpty()) throw GradleException("Licenses page: add the text in config/licenses or res/raw:\n  " + bad.distinct().joinToString("\n  "))
  }
}
