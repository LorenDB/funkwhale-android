buildscript {

  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }

  dependencies {
    classpath("com.android.tools.build:gradle:7.2.2")
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.21")
    classpath("com.github.bjoernq:unmockplugin:0.7.9")
    classpath("com.github.ben-manes:gradle-versions-plugin:0.42.0")
    classpath("org.jacoco:org.jacoco.core:0.8.7")
  }
}

allprojects {

  repositories {
    google()
    jcenter()
    maven(url = "https://jitpack.io")
  }
}

subprojects {
  configurations.all {
    resolutionStrategy {
      eachDependency {
        if (this.requested.group == "org.jacoco") {
          this.useVersion("0.8.7")
        }
      }
    }
  }
}

tasks {
  val clean by registering(Delete::class) {
    delete(buildDir)
  }
}
