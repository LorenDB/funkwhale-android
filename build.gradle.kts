buildscript {

  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }

  dependencies {
    classpath("com.android.tools.build:gradle:${Versions.androidGradlePlugin}")
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}")
    classpath("com.github.bjoernq:unmockplugin:${Versions.unmock}")
    classpath("com.vanniktech:gradle-android-junit-jacoco-plugin:${Versions.gradleAndroidJUnitJacocoPlugin}")
    classpath("com.github.ben-manes:gradle-versions-plugin:${Versions.gradleDependencyPlugin}")
  }
}

allprojects {

  repositories {
    google()
    maven(url = "https://jitpack.io")
    jcenter()
  }
}

tasks {
  val clean by registering(Delete::class) {
    delete(buildDir)
  }
}
