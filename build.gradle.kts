plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.parcelize) apply false
  alias(libs.plugins.navigation.safeargs) apply false
}

val jacocoVersion = libs.versions.jacoco.get()

subprojects {
  configurations.all {
    resolutionStrategy {
      eachDependency {
        if (this.requested.group == "org.jacoco") {
          this.useVersion(jacocoVersion)
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
