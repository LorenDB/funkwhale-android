import java.io.FileInputStream
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.navigation.safeargs)
  alias(libs.plugins.kotlin.parcelize)
  alias(libs.plugins.ktlint)
  alias(libs.plugins.androidgitversion)
  alias(libs.plugins.play)
  alias(libs.plugins.unmock)
  alias(libs.plugins.versions)
  jacoco
}

val props = Properties().apply {
  try {
    load(FileInputStream(rootProject.file("local.properties")))
  } catch (e: Exception) {
  }
}

unMock {
  keep = listOf("android.net.Uri")
}

androidGitVersion {
  codeFormat = "MMNNPPBBB" // Keep in sync with version_code() in dist/create_release.sh
  format = "%tag%%-count%%-commit%%-branch%"
}

android {
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  namespace = "audio.funkwhale.ffa"

  testCoverage {
    version = libs.versions.jacoco.get()
  }

  kotlinOptions {
    jvmTarget = JavaVersion.VERSION_17.toString()
  }

  buildFeatures {
    viewBinding = true
  }

  packaging {
    resources.excludes.add("META-INF/LICENSE.md")
    resources.excludes.add("META-INF/LICENSE-notice.md")
  }

  lint {
    disable += listOf("MissingTranslation", "ExtraTranslation")
  }

  compileSdk = 35

  defaultConfig {

    applicationId = "audio.funkwhale.ffa"

    versionCode = androidGitVersion.code()
    versionName = androidGitVersion.name()

    minSdk = 24
    targetSdk = 35

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    manifestPlaceholders["appAuthRedirectScheme"] = "urn"
  }

  signingConfigs {

    create("release") {
      if (project.hasProperty("signing.store")) {
        storeFile = file(project.findProperty("signing.store")!!)
        storePassword = project.findProperty("signing.store_passphrase")!!.toString()
        keyAlias = "ffa"
        keyPassword = project.findProperty("signing.key_passphrase")!!.toString()
      }
    }
    getByName("debug") {
      if (project.hasProperty("signing.store")) {
        storeFile = file(project.findProperty("signing.store")!!)
        storePassword = project.findProperty("signing.store_passphrase")!!.toString()
        keyAlias = "ffa"
        keyPassword = project.findProperty("signing.key_passphrase")!!.toString()
      }
    }
  }

  testOptions {
    execution = "ANDROID_TEST_ORCHESTRATOR"
    animationsDisabled = true
    unitTests {
      isReturnDefaultValues = true
      isIncludeAndroidResources = true
    }
  }

  buildTypes {
    getByName("debug") {
      isDebuggable = true
      applicationIdSuffix = ".dev"

      enableUnitTestCoverage = true

      if (project.hasProperty("signing.store")) {
        signingConfig = signingConfigs.getByName("debug")
      }

      resValue("string", "debug.hostname", props.getProperty("debug.hostname", ""))
      resValue("string", "debug.username", props.getProperty("debug.username", ""))
      resValue("string", "debug.password", props.getProperty("debug.password", ""))
    }

    getByName("release") {

      if (project.hasProperty("signing.store")) {
        signingConfig = signingConfigs.getByName("release")
      }

      resValue("string", "debug.hostname", "")
      resValue("string", "debug.username", "")
      resValue("string", "debug.password", "")

      isMinifyEnabled = true
      isShrinkResources = true

      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }
  }
}

ktlint {
  debug.set(false)
  verbose.set(false)
}

play {
  enabled.set(props.containsKey("play.credentials"))

  if (enabled.get()) {
    serviceAccountCredentials.set(file(props.getProperty("play.credentials")))
    defaultToAppBundles.set(true)
    track.set("beta")
  }
}

dependencies {
  implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))

  implementation(libs.kotlin.stdlib)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)

  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.lifecycle.livedata.ktx)
  implementation(libs.androidx.coordinatorlayout)
  implementation(libs.androidx.preference.ktx)
  implementation(libs.androidx.recyclerview)
  implementation(libs.androidx.swiperefreshlayout)
  implementation(libs.material) {
    exclude("androidx.constraintlayout")
  }
  implementation(libs.androidx.constraintlayout)

  implementation(libs.media3.exoplayer)
  implementation(libs.media3.ui)
  implementation(libs.media3.session)
  implementation(libs.media3.datasource)
  implementation(libs.media3.database)

  implementation(libs.koin.core)
  implementation(libs.koin.android)
  testImplementation(libs.koin.test)

  implementation("com.github.PaulWoitaschek.ExoPlayer-Extensions:extension-opus:789a4f83169cff5c7a91655bb828fde2cfde671a") {
    isTransitive = false
  }
  implementation("com.github.PaulWoitaschek.ExoPlayer-Extensions:extension-flac:789a4f83169cff5c7a91655bb828fde2cfde671a") {
    isTransitive = false
  }

  implementation(libs.powerpreference)
  implementation(libs.fuel)
  implementation(libs.fuel.coroutines)
  implementation(libs.fuel.android)
  implementation(libs.fuel.gson)
  implementation(libs.gson)
  implementation(libs.picasso)
  implementation(libs.picasso.transformations)
  implementation(libs.appauth)

  implementation(libs.androidx.navigation.fragment.ktx)
  implementation(libs.androidx.navigation.ui.ktx)

  testImplementation(libs.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.test.core)
  testImplementation(libs.strikt.core)
  testImplementation(libs.robolectric)
  debugImplementation(libs.sentry.android)

  androidTestImplementation(libs.mockk.android)
  androidTestImplementation(libs.androidx.navigation.testing)
}

project.afterEvaluate {

  tasks.withType<Test> {

    configure<JacocoTaskExtension> {
      isIncludeNoLocationClasses = true
      excludes = listOf("jdk.internal.*")
    }
  }

  tasks.create("jacocoTestReport", type = JacocoReport::class) {
    dependsOn("testDebugUnitTest", "createDebugCoverageReport")

    group = "Verification"
    description = "Creates a Jacoco Coverage report"

    reports {
      xml.required.set(true)
      csv.required.set(true)
      html.required.set(true)
    }

    val fileFilter = listOf(
      "**/R.class",
      "**/R$*.class",
      "**/BuildConfig.*",
      "**/Manifest*.*",
      "**/*Test*.*",
      "android/**/*.*",
      "**/*$[0-9].*"
    )

    val debugTree = fileTree("${project.layout.buildDirectory.get().asFile}/tmp/kotlin-classes/debug") {
      setExcludes(fileFilter)
    }
    val mainSrc = "${project.projectDir}/src/main/java"

    sourceDirectories.setFrom(files(listOf(mainSrc)))
    classDirectories.setFrom(files(listOf(debugTree)))

    executionData.setFrom(
      fileTree(project.layout.buildDirectory.get().asFile) {
        setIncludes(
          listOf(
            "outputs/unit_test_code_coverage/debugUnitTest/*.exec",
            "outputs/code_coverage/debugAndroidTest/connected/**/*.ec"
          )
        )
      }
    )
  }
}
