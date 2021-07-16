buildscript {
  repositories {
    google()
    jcenter()
  }

  dependencies {
    classpath("com.android.tools.build:gradle:4.2.2")
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.21")
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
