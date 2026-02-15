buildscript {
  extra.apply{
    set("navVersion", "2.8.9")
    set("lifecycleVersion", "2.8.7")
  }

  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }

  val navVersion: String by extra

  dependencies {
    classpath("com.android.tools.build:gradle:8.7.3")
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
    classpath("com.github.bjoernq:unmockplugin:0.7.9")
    classpath("com.github.ben-manes:gradle-versions-plugin:0.51.0")
    classpath("org.jacoco:org.jacoco.core:0.8.12")
    classpath("androidx.navigation:navigation-safe-args-gradle-plugin:$navVersion")
  }
}

allprojects {
  repositories {
    google()
    mavenCentral()
    maven(url = "https://jitpack.io")
  }
}

subprojects {
  configurations.all {
    resolutionStrategy {
      eachDependency {
        if (this.requested.group == "org.jacoco") {
          this.useVersion("0.8.12")
        }
      }
    }
  }
}

tasks {
  val clean by registering(Delete::class) {
    delete(layout.buildDirectory)
  }
}
