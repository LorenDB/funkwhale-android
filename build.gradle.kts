buildscript {

  repositories {
    google()
    mavenCentral()
    jcenter()
  }

  dependencies {
    classpath("com.android.tools.build:gradle:${Versions.androidGradlePlugin}")
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}")
    classpath("com.github.bjoernq:unmockplugin:${Versions.unmock}")
    classpath("com.vanniktech:gradle-android-junit-jacoco-plugin:${Versions.gradleAndroidJUnitJacocoPlugin}")
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
