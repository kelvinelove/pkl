// https://youtrack.jetbrains.com/issue/KTIJ-19369
@file:Suppress("DSL_SCOPE_VIOLATION") 

import org.jetbrains.gradle.ext.ActionDelegationConfig
import org.jetbrains.gradle.ext.ActionDelegationConfig.TestRunner.PLATFORM
import org.jetbrains.gradle.ext.ProjectSettings
import org.jetbrains.gradle.ext.TaskTriggersConfig

plugins {
  pklAllProjects
  pklGraalVm

  alias(libs.plugins.ideaExt)
  alias(libs.plugins.jmh) apply false
  alias(libs.plugins.nexusPublish)
}

nexusPublishing {
  repositories {
    sonatype {
      nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
      snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
    }
  }
}

idea {
  project {
    this as ExtensionAware
    configure<ProjectSettings> {
      this as ExtensionAware
      configure<ActionDelegationConfig> {
        delegateBuildRunToGradle = true
        testRunner = PLATFORM
      }
      configure<TaskTriggersConfig> {
        afterSync(provider { project(":pkl-core").tasks.named("makeIntelliJAntlrPluginHappy") })
      }
    }
  }
}

val clean by tasks.registering(Delete::class) {
  delete(layout.buildDirectory)
}

val printVersion by tasks.registering {
  doFirst { println(buildInfo.pklVersion) }
}

val message = """
====
Gradle version : ${gradle.gradleVersion}
Java version   : ${System.getProperty("java.version")}
isParallel     : ${gradle.startParameter.isParallelProjectExecutionEnabled}
maxWorkerCount : ${gradle.startParameter.maxWorkerCount}
Architecture   : ${buildInfo.arch}

Project Version        : ${project.version}
Pkl Version            : ${buildInfo.pklVersion}
Pkl Non-Unique Version : ${buildInfo.pklVersionNonUnique}
Git Commit ID          : ${buildInfo.commitId}
====
"""

val formattedMessage = message.replace("\n====", "\n" + "=".repeat(message.lines().maxByOrNull { it.length }!!.length))
logger.info(formattedMessage)
