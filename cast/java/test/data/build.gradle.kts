import com.ibm.wala.gradle.adHocDownload
import net.ltgt.gradle.errorprone.errorprone

plugins { id("com.ibm.wala.gradle.java") }

val compileTestJava by
    tasks.existing(JavaCompile::class) {
      options.run {
        // No need to run Error Prone on our analysis test inputs
        errorprone.isEnabled = false
        // Some code in the test data is written in a deliberately bad style, so allow warnings
        compilerArgs.remove("-Werror")
        compilerArgs.add("-nowarn")
      }
    }

val testJar by
    tasks.registering(Jar::class) {
      group = "build"
      archiveClassifier = "test"
      from(compileTestJava)
    }

val testJarConfig: Configuration by configurations.creating { isCanBeResolved = false }

val testJavaSourceDirectory: Configuration by configurations.creating { isCanBeResolved = false }

artifacts {
  add(testJarConfig.name, testJar)
  add(testJavaSourceDirectory.name, sourceSets.test.map { it.java.srcDirs.first() })
}

// exclude since various tests make assertions based on
// source positions in the test inputs.  to auto-format
// we also need to update the test assertions
spotless { java { targetExclude("**/*") } }

////////////////////////////////////////////////////////////////////////
//
//  download JLex
//

val jLex =
    adHocDownload(
        uri("https://www.cs.princeton.edu/~appel/modern/java/JLex/current"), "Main", ext = "java")

val downloadJLex by
    tasks.registering(Sync::class) {
      from(jLex) { eachFile { name = "Main.java" } }
      into(layout.buildDirectory.dir(name))
    }

sourceSets.test.get().java.srcDir(downloadJLex.map { it.destinationDir })

////////////////////////////////////////////////////////////////////////
//
//  create Eclipse metadata for use by Maven when running
//  com.ibm.wala.cast.java.test.JDTJavaIRTests and
//  com.ibm.wala.cast.java.test.JDTJava15IRTests tests
//

tasks.register("prepareMavenBuild") { dependsOn("eclipseClasspath", "eclipseProject") }

// On JDK 17, deprecation errors in ECJ cannot be disabled when compiling JLex code.  So, we disable
// the ECJ task on JDK 17+.
if (JavaVersion.current() >= JavaVersion.VERSION_17) {
  tasks.named("compileTestJavaUsingEcj") { enabled = false }
}
