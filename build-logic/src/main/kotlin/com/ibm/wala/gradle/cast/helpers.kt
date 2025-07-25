package com.ibm.wala.gradle.cast

import java.io.File
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskInstantiationException
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.jvm.Jvm
import org.gradle.kotlin.dsl.closureOf
import org.gradle.language.cpp.CppBinary
import org.gradle.nativeplatform.OperatingSystemFamily
import org.gradle.nativeplatform.tasks.AbstractLinkTask

////////////////////////////////////////////////////////////////////////
//
//  helpers for building native CAst components
//

/**
 * Configures the provided [Task] using the given action.
 *
 * [TaskProvider] already offers a [TaskProvider.configure] method that is compatible with Gradle's
 * [task configuration avoidance APIs](https://docs.gradle.org/current/userguide/task_configuration_avoidance.html).
 * Unfortunately, many of the APIs for native compilation provide access only to [Provider]<[Task]>
 * instances, which have no configuration-avoiding `configure` method. Instead, the best we can do
 * is to [get][Provider.get] the provided [Task], then configure it using [Task.configure].
 *
 * See also
 * [an existing request to improve these APIs](https://github.com/gradle/gradle-native/issues/683).
 *
 * @param action The configuration action to be applied to the task.
 */
fun <T : Task> Provider<T>.configure(action: T.() -> Unit) {
  get().configure(closureOf(action))
}

private fun File.findJvmLibrary(extension: String, subdirs: List<String>) =
    subdirs.map { resolve("$it/libjvm.$extension") }.find { it.exists() }!!

fun CppBinary.addJvmLibrary(project: Project) {
  val currentJavaHome = Jvm.current().javaHome
  val family = targetMachine.operatingSystemFamily

  val (osIncludeSubdir, libJVM) =
      when (family.name) {
        OperatingSystemFamily.LINUX ->
            "linux" to
                currentJavaHome.findJvmLibrary(
                    "so", listOf("jre/lib/amd64/server", "lib/amd64/server", "lib/server"))
        OperatingSystemFamily.MACOS ->
            "darwin" to
                currentJavaHome.findJvmLibrary("dylib", listOf("jre/lib/server", "lib/server"))
        OperatingSystemFamily.WINDOWS -> "win32" to currentJavaHome.resolve("lib/jvm.lib")
        else -> throw TaskInstantiationException("unrecognized operating system family \"$family\"")
      }

  compileTask.configure {
    val jniIncludeDir = "$currentJavaHome/include"
    includes(project.files(jniIncludeDir, "$jniIncludeDir/$osIncludeSubdir"))
  }

  project.dependencies.add((linkLibraries as Configuration).name, project.files(libJVM))
}

/**
 * Adds runtime search paths (rpaths) for all library dependencies of the link task.
 *
 * This extension method configures the underlying [AbstractLinkTask] to add linker arguments that
 * specify runtime search paths for all libraries that the task links against. This ensures that the
 * runtime loader can find these libraries when the resulting binary is executed.
 *
 * The method only adds rpaths on non-Windows platforms, as the rpath concept is not applicable to
 * Windows.
 */
fun Provider<out AbstractLinkTask>.addRpaths() {
  configure {
    if (!targetPlatform.get().operatingSystem.isWindows) {
      linkerArgs.addAll(project.provider { libs.map { "-Wl,-rpath,${it.parentFile}" } })
    }
  }
}
