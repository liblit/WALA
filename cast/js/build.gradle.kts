import com.ibm.wala.gradle.CreatePackageList
import com.ibm.wala.gradle.adHocDownload

plugins {
  id("com.ibm.wala.gradle.java")
  id("com.ibm.wala.gradle.publishing")
}

dependencies {
  api(libs.jericho.html)
  api(projects.cast) { because("public class JSCallGraphUtil extends class CAstCallGraphUtil") }
  api(projects.core)
  api(projects.util)
  implementation(libs.commons.io)
  implementation(libs.gson)
  implementation(projects.shrike)
  javadocClasspath(projects.cast.js.rhino)
  testFixturesApi(libs.junit.jupiter.api)
  testFixturesApi(projects.cast)
  testFixturesApi(projects.core)
  testFixturesApi(projects.util)
  testFixturesApi(testFixtures(projects.cast))
  testImplementation(libs.junit.jupiter.api)
  testImplementation(testFixtures(projects.core))
}

val createPackageList by
    tasks.registering(CreatePackageList::class) { sourceSet(sourceSets.main.get()) }

val packageListDirectory: Configuration by configurations.creating { isCanBeResolved = false }

val javadocDestinationDirectory: Configuration by
    configurations.creating { isCanBeResolved = false }

tasks.named<Test>("test") { maxHeapSize = "800M" }

val downloadAjaxslt =
    adHocDownload(
        uri(
            "https://storage.googleapis.com/google-code-archive-downloads/v2/code.google.com/ajaxslt"),
        "ajaxslt",
        version = "0.8.1",
        ext = "tar.gz")

val unpackAjaxslt by
    tasks.registering(Sync::class) {
      from(tarTree { downloadAjaxslt.singleFile }) {
        eachFile {
          val newSegments = relativePath.segments.drop(1).toTypedArray()
          relativePath = RelativePath(!isDirectory, *newSegments)
        }
      }
      into(project.layout.buildDirectory.dir(name))
    }

val processTestResources by tasks.existing(Copy::class) { from(unpackAjaxslt) { into("ajaxslt") } }

val testResources: Configuration by configurations.creating { isCanBeResolved = false }

artifacts {
  add(javadocDestinationDirectory.name, tasks.javadoc)
  add(packageListDirectory.name, createPackageList)
  add(testResources.name, processTestResources)
}
