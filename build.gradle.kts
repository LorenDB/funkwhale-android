buildscript {
  repositories {
    google()
    jcenter()
  }

  dependencies {
    classpath("com.android.tools.build:gradle:4.2.1")
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.20")
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
