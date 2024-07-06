import com.ibm.wala.gradle.CompileKawaScheme
import com.ibm.wala.gradle.JavaCompileUsingEcj
import com.ibm.wala.gradle.adHocDownload
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.plugins.ide.eclipse.model.AbstractClasspathEntry
import org.gradle.plugins.ide.eclipse.model.Classpath

plugins {
  id("com.ibm.wala.gradle.java")
  id("com.ibm.wala.gradle.publishing")
}

eclipse {
  project.natures("org.eclipse.pde.PluginNature")
  classpath.file.whenMerged {
    (this as Classpath).run {
      entries.forEach {
        if (it is AbstractClasspathEntry && it.path == "src/testSubjects/java") {
          it.entryAttributes["ignore_optional_problems"] = true
        }
      }
    }
  }
}

sourceSets.create("testSubjects")

val compileTestSubjectsJava by tasks.existing(JavaCompile::class)

val ecjCompileJavaTestSubjects: TaskProvider<JavaCompileUsingEcj> =
    JavaCompileUsingEcj.withSourceSet(project, sourceSets["testSubjects"])

ecjCompileJavaTestSubjects.configure {
  options.compilerArgumentProviders.add {
    listOf(
        "-warn:none",
        "-err:-serial",
        "-err:-unchecked",
        "-err:-unusedLocal",
        "-err:-unusedParam",
        "-err:-unusedThrown",
    )
  }
}

tasks.named("check") { dependsOn(ecjCompileJavaTestSubjects) }

compileTestSubjectsJava.configure {
  // No need to run Error Prone on our analysis test inputs
  options.errorprone.isEnabled = false
}

dependencies {
  api(projects.shrike) {
    because("public class Entrypoint implements interface BytecodeConstraints")
  }
  api(projects.util) { because("public interface CallGraph extends interface NumberedGraph") }
  testFixturesApi(libs.junit.jupiter.api)
  testFixturesApi(projects.shrike)
  testFixturesImplementation(libs.ant)
  testFixturesImplementation(libs.junit.platform.engine)
  testFixturesImplementation(libs.junit.platform.launcher)
  testFixturesImplementation(projects.util)
  implementation(libs.gson)
  testImplementation(libs.assertj.core)
  testImplementation(libs.hamcrest)
  testImplementation(libs.junit.jupiter.api)
  testRuntimeOnly(sourceSets["testSubjects"].output.classesDirs)
  // add the testSubjects source files to enable SourceMapTest to pass
  testRuntimeOnly(files(sourceSets["testSubjects"].java.srcDirs))
  // to allow writing test subject classes that use JUnit annotations
  "testSubjectsImplementation"(libs.junit.jupiter.api)
  "testSubjectsImplementation"(platform(libs.junit.bom))
}

// Injected services used by several tasks that extract selected files from downloads.
interface ExtractServices {
  @get:Inject val archive: ArchiveOperations
  @get:Inject val fileSystem: FileSystemOperations
}

////////////////////////////////////////////////////////////////////////
//
//  download and extract kawa 3.0 "kawa.jar"
//

val kawa = adHocDownload(uri("https://ftp.gnu.org/pub/gnu/kawa"), "kawa", "3.0", ext = "zip")

val extractKawa by
    tasks.registering {
      inputs.files(kawa)
      outputs.file(layout.buildDirectory.file("$name/kawa.jar"))

      objects.newInstance<ExtractServices>().run {
        doLast {
          fileSystem.copy {
            from(archive.zipTree(inputs.files.singleFile)) {
              include("kawa-*/lib/${outputs.files.singleFile.name}")
              eachFile { relativePath = RelativePath.parse(!isDirectory, relativePath.lastName) }
            }
            into(outputs.files.singleFile.parent)
            includeEmptyDirs = false
          }
        }
      }
    }

////////////////////////////////////////////////////////////////////////
//
//  download, unpack, and build kawa chess
//

val kawaChessCommitHash = "f1d2dcc707a1ef19dc159e2eaee5aecc8a41d7a8"

val kawaChess =
    adHocDownload(
        uri("https://github.com/ttu-fpclub/kawa-chess/archive"), kawaChessCommitHash, ext = "zip")

val unpackKawaChess by
    tasks.registering {
      inputs.files(kawaChess)
      outputs.dir(project.layout.buildDirectory.file("kawa-chess-$kawaChessCommitHash"))

      objects.newInstance<ExtractServices>().run {
        doLast {
          fileSystem.copy {
            from(archive.zipTree(inputs.files.singleFile))
            into(outputs.files.singleFile.parent)
          }
        }
      }
    }

val compileKawaSchemeChessMain by
    tasks.registering(CompileKawaScheme::class) {
      schemeFile.fileProvider(
          unpackKawaChess.map { file("${it.outputs.files.singleFile}/main.scm") })
    }

val buildChessJar by
    tasks.registering(Jar::class) {
      from(compileKawaSchemeChessMain)
      destinationDirectory = project.layout.buildDirectory.dir(name)
      archiveFileName = "kawachess.jar"
      archiveVersion = null as String?
    }

////////////////////////////////////////////////////////////////////////
//
//  build the kawa test jar
//

val compileKawaSchemeTest by
    tasks.registering(CompileKawaScheme::class) {
      schemeFile = layout.projectDirectory.file("kawasrc/test.scm")
    }

val buildKawaTestJar by
    tasks.registering(Jar::class) {
      from(compileKawaSchemeTest)
      destinationDirectory = project.layout.buildDirectory.dir(name)
      archiveFileName = "kawatest.jar"
      archiveVersion = null as String?
    }

////////////////////////////////////////////////////////////////////////
//
//  download and extract "bcel-5.2.jar"
//

val downloadBcel =
    adHocDownload(
        uri("https://archive.apache.org/dist/jakarta/bcel/binaries"), "bcel", "5.2", ext = "tar.gz")

val extractBcel by
    tasks.registering(Sync::class) {
      from(tarTree { downloadBcel.singleFile })
      include("**/*.jar")
      into(layout.buildDirectory.map { "$it/$name" })
      eachFile { relativePath = RelativePath.parse(!isDirectory, relativePath.lastName) }
      includeEmptyDirs = false
    }

////////////////////////////////////////////////////////////////////////
//
//  download "java-cup-11a.jar"
//

val downloadJavaCup =
    adHocDownload(uri("https://www2.cs.tum.edu/projects/cup"), "java-cup", "11a", ext = "jar")

////////////////////////////////////////////////////////////////////////
//
//  collect "JLex.jar"
//

val collectJLexFrom: Configuration by configurations.creating { isCanBeConsumed = false }

dependencies {
  collectJLexFrom(
      project(mapOf("path" to ":cast:java:test:data", "configuration" to "testJarConfig")))
}

val collectJLex by
    tasks.registering(Jar::class) {
      from(zipTree { collectJLexFrom.singleFile })
      include("JLex/")
      archiveFileName = "JLex.jar"
      destinationDirectory = layout.buildDirectory.dir(name)
    }

////////////////////////////////////////////////////////////////////////
//
//  generate "hello_hash.jar"
//

val ocamlJavaVersion = "2.0-alpha1"

val downloadOcamlJava =
    adHocDownload(
        uri("http://www.ocamljava.org/files/distrib"),
        "ocamljava",
        ocamlJavaVersion,
        "bin",
        "tar.gz")

// Ideally this would be a `Sync` task using `from(tarTree { downloadOcamlJava.singleFile })`.
// However, this specific tar archive contains a member with a leading slash, and that apparently
// causes Gradle's native tar support to fail.
val unpackOcamlJava by
    tasks.registering(Exec::class) {
      commandLine(
          "tar",
          "xzf",
          downloadOcamlJava.singleFile,
          "ocamljava-$ocamlJavaVersion/lib/ocamljava.jar")
      val outputDir = project.layout.buildDirectory.dir(name)
      workingDir(outputDir)
      outputs.dir(outputDir)
    }

val prepareGenerateHelloHashJar by
    tasks.registering(Sync::class) {
      from("ocaml/hello_hash.ml")
      val outputDir = project.layout.buildDirectory.dir(name)
      into(outputDir)
      extra["copiedOcamlSource"] = file("${outputDir.get()}/${source.singleFile.name}")
    }

val generateHelloHashJar by
    tasks.registering(JavaExec::class) {
      val ocamlSource = prepareGenerateHelloHashJar.map { it.extra["copiedOcamlSource"] as File }
      inputs.file(ocamlSource)

      val jarTarget = layout.projectDirectory.file("ocaml/hello_hash.jar")
      outputs.file(jarTarget)
      outputs.cacheIf { true }

      val ocamlJavaJar =
          unpackOcamlJava.map {
            file("${it.workingDir}/ocamljava-$ocamlJavaVersion/lib/ocamljava.jar")
          }
      inputs.file(ocamlJavaJar)
      classpath(ocamlJavaJar)

      mainClass = "ocaml.compilers.ocamljavaMain"
      args("-o", jarTarget)
      argumentProviders.add { listOf(ocamlSource.get().toString()) }
    }

////////////////////////////////////////////////////////////////////////
//
//  collect "com.ibm.wala.core.testdata_1.0.0.jar"
//

val collectTestData by
    tasks.registering(Jar::class) {
      archiveFileName = "com.ibm.wala.core.testdata_1.0.0.jar"
      from(compileTestSubjectsJava)
      from("classes")
      includeEmptyDirs = false
      destinationDirectory = layout.buildDirectory.dir(name)
    }

val collectTestDataJar: Configuration by configurations.creating { isCanBeResolved = false }

artifacts.add(collectTestDataJar.name, collectTestData.map { it.destinationDirectory })

////////////////////////////////////////////////////////////////////////
//
//  collect "com.ibm.wala.core.testdata_1.0.0a.jar" for Dalvik tests
//

val collectTestDataAForDalvik by
    tasks.registering(Jar::class) {
      archiveFileName = "com.ibm.wala.core.testdata_1.0.0a.jar"
      from(compileTestSubjectsJava)
      from("classes")
      includeEmptyDirs = false
      destinationDirectory = layout.buildDirectory.dir(name)
      exclude(
          // This is an invalid class so don't include it; it causes D8 to crash
          "**/CodeDeleted.class",
      )
    }

////////////////////////////////////////////////////////////////////////

tasks.named<Copy>("processTestResources") {
  from(
      buildChessJar,
      buildKawaTestJar,
      collectJLex,
      collectTestData,
      downloadJavaCup,
      extractBcel,
      extractKawa,
  )

  // If "ocaml/hello_hash.jar" exists, then treat it as up-to-date and ready to use.  But if it is
  // missing, then use the generateHelloHashJar task to rebuild it.  The latter will entail
  // downloading OCaml-Java if we haven"t already: something we prefer to avoid.
  val helloHashJar = generateHelloHashJar.get().outputs.files.singleFile
  from(if (helloHashJar.exists()) helloHashJar else generateHelloHashJar)
}

tasks.named<Test>("test") {
  maxHeapSize = "2000M"
  systemProperty("com.ibm.wala.junit.profile", "short")
  classpath += files(sourceSets.test.get().output.classesDirs)
  testLogging {
    exceptionFormat = TestExceptionFormat.FULL
    events("passed", "skipped", "failed")
  }
  // temporarily turn off some tests on JDK 11+
  if (JavaVersion.current() >= JavaVersion.VERSION_11) {
    exclude("**/cha/LibraryVersionTest.class") // https://github.com/wala/WALA/issues/963
  }

  outputs.file(layout.buildDirectory.file("report"))
}

val testResources: Configuration by configurations.creating { isCanBeResolved = false }

artifacts.add(testResources.name, sourceSets.test.map { it.resources.srcDirs.single() })

////////////////////////////////////////////////////////////////////////

val testJar by
    tasks.registering(Jar::class) {
      group = "build"
      archiveClassifier = "test"
      from(tasks.named("compileTestJava"))
    }

val testJarConfig: Configuration by configurations.creating { isCanBeResolved = false }

artifacts.add(testJarConfig.name, testJar)

val dalvikTestResources: Configuration by configurations.creating { isCanBeResolved = false }

listOf(
        collectJLex,
        collectTestDataAForDalvik,
        downloadJavaCup.singleFile,
        extractBcel,
    )
    .forEach { artifacts.add(dalvikTestResources.name, it) }
