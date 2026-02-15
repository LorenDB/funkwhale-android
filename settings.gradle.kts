pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
    maven(url = "https://jitpack.io")
  }
  resolutionStrategy {
    eachPlugin {
      if (requested.id.id == "de.mobilej.unmock") {
        useModule("com.github.bjoernq:unmockplugin:${requested.version}")
      }
    }
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
  repositories {
    google()
    mavenCentral()
    maven(url = "https://jitpack.io")
  }
}

include(":app")
