buildscript {

  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }

  dependencies {
    classpath("com.android.tools.build:gradle:7.1.3")
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}")
    classpath("com.github.bjoernq:unmockplugin:${Versions.unmock}")
    classpath("com.github.ben-manes:gradle-versions-plugin:${Versions.gradleDependencyPlugin}")
    classpath("org.jacoco:org.jacoco.core:${Versions.jacoco}")
  }
}

allprojects {

  repositories {
    google()
    maven(url = "https://jitpack.io")
    jcenter()
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
