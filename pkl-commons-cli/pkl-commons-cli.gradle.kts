plugins {
  pklAllProjects
  pklKotlinLibrary
  pklPublishLibrary
}

dependencies {
  api(projects.pklCore)
  api(libs.clikt) {
    // force clikt to use our version of the kotlin stdlib
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-common")
  }

  implementation(projects.pklCommons)
  testImplementation(projects.pklCommonsTest)
  runtimeOnly(projects.pklCerts)
}

publishing {
  publications {
    named<MavenPublication>("library") {
      pom {
        url.set("https://github.com/apple/pkl/tree/main/pkl-commons-cli")
        description.set("Internal CLI utilities. NOT A PUBLIC API.")
      }
    }
  }
}
