import org.jetbrains.kotlin.konan.properties.hasProperty
import java.io.FileInputStream
import java.util.Properties

plugins {
  id("com.android.application")
  id("kotlin-android")

  id("org.jlleitschuh.gradle.ktlint") version "8.1.0"
  id("com.gladed.androidgitversion") version "0.4.14"
  id("com.github.triplet.play") version "2.4.2"
  id("de.mobilej.unmock")
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
  //keepStartingWith("org.")
  //keepStartingWith("libcore.")
}

jacoco {
  toolVersion = "0.8.7"
}

androidGitVersion {
  codeFormat = "MMNNPPBBB"
  format = "%tag%%-count%%-commit%%-branch%"
}

android {
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  kotlinOptions {
    jvmTarget = JavaVersion.VERSION_1_8.toString()
  }

  buildFeatures {
    viewBinding = true
  }

  lintOptions {
    disable("MissingTranslation")
  }

  compileSdkVersion(30)

  defaultConfig {
    applicationId = "audio.funkwhale.ffa"

    minSdkVersion(24)
    targetSdkVersion(30)

    versionCode = androidGitVersion.code()
    versionName = androidGitVersion.name()

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
    unitTests.isReturnDefaultValues = true
  }

  buildTypes {
    getByName("debug") {
      isDebuggable = true
      applicationIdSuffix = ".dev"

      if (project.hasProperty("signing.store")) {
        signingConfig = signingConfigs.getByName("debug")
      }

      isTestCoverageEnabled = true

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
  isEnabled = props.hasProperty("play.credentials")

  if (isEnabled) {
    serviceAccountCredentials = file(props.getProperty("play.credentials"))
    defaultToAppBundles = true
    track = "beta"
  }
}

dependencies {
  implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))

  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.5.21")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.1")

  implementation("androidx.appcompat:appcompat:1.3.0")
  implementation("androidx.core:core-ktx:1.6.0")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.4.0-alpha02")
  implementation("androidx.coordinatorlayout:coordinatorlayout:1.1.0")
  implementation("androidx.preference:preference-ktx:1.1.1")
  implementation("androidx.recyclerview:recyclerview:1.2.1")
  implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
  implementation("com.google.android.material:material:1.4.0")
  implementation("com.android.support.constraint:constraint-layout:2.0.4")

  implementation("com.google.android.exoplayer:exoplayer-core:2.11.8")
  implementation("com.google.android.exoplayer:exoplayer-ui:2.11.8")
  implementation("com.google.android.exoplayer:extension-mediasession:2.11.8")
  implementation("com.github.PaulWoitaschek.ExoPlayer-Extensions:extension-opus:2.11.4") {
    isTransitive = false
  }
  implementation("com.github.PaulWoitaschek.ExoPlayer-Extensions:extension-flac:2.11.4") {
    isTransitive = false
  }

  implementation("com.aliassadi:power-preference-lib:2.0.0")
  implementation("com.github.kittinunf.fuel:fuel:2.3.1")
  implementation("com.github.kittinunf.fuel:fuel-coroutines:2.3.1")
  implementation("com.github.kittinunf.fuel:fuel-android:2.3.1")
  implementation("com.github.kittinunf.fuel:fuel-gson:2.3.1")
  implementation("com.google.code.gson:gson:2.8.7")
  implementation("com.squareup.picasso:picasso:2.71828")
  implementation("jp.wasabeef:picasso-transformations:2.4.0")
  implementation("net.openid:appauth:0.9.1")
  testImplementation("junit:junit:4.13.2")
  testImplementation("io.mockk:mockk:1.12.0")
  androidTestImplementation("io.mockk:mockk-android:1.12.0")
  testImplementation("androidx.test:core:1.4.0")
  testImplementation("io.strikt:strikt-core:0.31.0")
}

project.afterEvaluate {
  android.applicationVariants.forEach { variant ->
    val testTaskName = "test${variant.name.capitalize()}UnitTest"
    tasks.create<JacocoReport>(name = "${testTaskName}Coverage") {

      dependsOn(testTaskName)

      group = "Reporting"
      description = "Generate Jacoco coverage reports after running tests."

      reports {
        xml.required.set(true)
        csv.required.set(true)
        html.required.set(true)
      }

      val excludes = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*"
      )

      val javaClasses =
        fileTree(baseDir = variant.javaCompileProvider.get().destinationDirectory).matching {
          setExcludes(excludes)
        }
      val kotlinClasses =
        fileTree(baseDir = "$buildDir/tmp/kotlin-classes/${variant.name}").matching {
          setExcludes(excludes)
        }
      classDirectories.setFrom(files(listOf(javaClasses, kotlinClasses)))

      val sourceDirectories = files(
        listOf(
          "$project.projectDir/src/main/java",
          "$project.projectDir/src/${variant.name}/java",
          "$project.projectDir/src/main/kotlin",
          "$project.projectDir/src/${variant.name}/kotlin"
        )
      )

      sourceDirectories.setFrom(files(sourceDirectories))
      executionData.setFrom(files("${project.buildDir}/jacoco/$testTaskName.exec"))
    }
  }
}
