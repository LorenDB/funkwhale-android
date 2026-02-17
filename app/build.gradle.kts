import org.jetbrains.kotlin.konan.properties.hasProperty
import java.io.FileInputStream
import java.util.Properties

plugins {
  id("com.android.application")
  id("kotlin-android")
  id("androidx.navigation.safeargs.kotlin")
  id("kotlin-parcelize")
  id("kotlin-kapt")

  id("org.jlleitschuh.gradle.ktlint") version "11.2.0"
  id("com.gladed.androidgitversion") version "0.4.14"
  id("com.github.triplet.play") version "3.8.1"
  id("de.mobilej.unmock")
  id("com.github.ben-manes.versions")
  id("org.jetbrains.kotlin.android")
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
    version = "0.8.7"
  }

  kotlinOptions {
    jvmTarget = JavaVersion.VERSION_17.toString()
  }

  buildFeatures {
    viewBinding = true
    dataBinding = true
  }

  packagingOptions {
    resources.excludes.add("META-INF/LICENSE.md")
    resources.excludes.add("META-INF/LICENSE-notice.md")
  }

  lint {
    disable += listOf("MissingTranslation", "ExtraTranslation")
  }

  compileSdk = 33

  defaultConfig {

    applicationId = "audio.funkwhale.ffa"

    versionCode = androidGitVersion.code()
    versionName = androidGitVersion.name()

    minSdk = 24
    targetSdk = 33

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

      isTestCoverageEnabled = true

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
  enabled.set(props.hasProperty("play.credentials"))

  if (enabled.get()) {
    serviceAccountCredentials.set(file(props.getProperty("play.credentials")))
    defaultToAppBundles.set(true)
    track.set("beta")
  }
}

dependencies {
  val navVersion: String by rootProject.extra
  val lifecycleVersion: String by rootProject.extra

  implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))

  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")

  implementation("androidx.appcompat:appcompat:1.6.1")
  implementation("androidx.core:core-ktx:1.9.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
  implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycleVersion")
  implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
  implementation("androidx.preference:preference-ktx:1.2.1")
  implementation("androidx.recyclerview:recyclerview:1.2.1")
  implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
  implementation("com.google.android.material:material:1.9.0") {
    exclude("androidx.constraintlayout")
  }
  implementation("androidx.palette:palette:1.0.0")

  implementation("com.google.android.exoplayer:exoplayer-core:2.18.1")
  implementation("com.google.android.exoplayer:exoplayer-ui:2.18.1")
  implementation("com.google.android.exoplayer:extension-mediasession:2.18.1")

  implementation("io.insert-koin:koin-core:3.5.3")
  implementation("io.insert-koin:koin-android:3.5.3")
  testImplementation("io.insert-koin:koin-test:3.5.3")

  implementation("com.github.PaulWoitaschek.ExoPlayer-Extensions:extension-opus:789a4f83169cff5c7a91655bb828fde2cfde671a") {
    isTransitive = false
  }
  implementation("com.github.PaulWoitaschek.ExoPlayer-Extensions:extension-flac:789a4f83169cff5c7a91655bb828fde2cfde671a") {
    isTransitive = false
  }

  implementation("com.github.AliAsadi:PowerPreference:2.1.1")
  implementation("com.github.kittinunf.fuel:fuel:2.3.1")
  implementation("com.github.kittinunf.fuel:fuel-coroutines:2.3.1")
  implementation("com.github.kittinunf.fuel:fuel-android:2.3.1")
  implementation("com.github.kittinunf.fuel:fuel-gson:2.3.1")
  implementation("com.google.code.gson:gson:2.10.1")
  implementation("com.squareup.picasso:picasso:2.71828")
  implementation("jp.wasabeef:picasso-transformations:2.4.0")
  implementation("net.openid:appauth:0.11.1")

  implementation("androidx.navigation:navigation-fragment-ktx:$navVersion")
  implementation("androidx.navigation:navigation-ui-ktx:$navVersion")

  testImplementation("junit:junit:4.13.2")
  testImplementation("io.mockk:mockk:1.13.4")
  testImplementation("androidx.test:core:1.5.0")
  testImplementation("io.strikt:strikt-core:0.34.1")
  testImplementation("org.robolectric:robolectric:4.9.2")
  debugImplementation("io.sentry:sentry-android:6.17.0")

  androidTestImplementation("io.mockk:mockk-android:1.13.4")
  androidTestImplementation("androidx.navigation:navigation-testing:$navVersion")
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

    val debugTree = fileTree("${project.buildDir}/tmp/kotlin-classes/debug") {
      setExcludes(fileFilter)
    }
    val mainSrc = "${project.projectDir}/src/main/java"

    sourceDirectories.setFrom(files(listOf(mainSrc)))
    classDirectories.setFrom(files(listOf(debugTree)))

    executionData.setFrom(
      fileTree(project.buildDir) {
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
