import org.jetbrains.kotlin.config.JvmTarget

plugins {
  `kotlin-dsl`
}

dependencies {
  implementation(libs.downloadTaskPlugin)
  implementation(libs.spotlessPlugin)
  implementation(libs.kotlinPlugin) {
    exclude(module = "kotlin-android-extensions")
  }
  implementation(libs.shadowPlugin)

  // fix from the Gradle team: makes version catalog symbols available in build scripts
  // see here for more: https://github.com/gradle/gradle/issues/15383
  implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
  target {
    compilations.configureEach {
      kotlinOptions {
        jvmTarget = "17"
      }
    }
  }
}
