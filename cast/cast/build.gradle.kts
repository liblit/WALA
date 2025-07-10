import com.ibm.wala.gradle.cast.addJvmLibrary
import com.ibm.wala.gradle.cast.configure

plugins {
  `cpp-library`
  id("com.ibm.wala.gradle.subproject")
}

library {
  binaries.whenElementFinalized {
    compileTask.configure { macros["BUILD_CAST_DLL"] = "1" }

    this as CppSharedLibrary
    linkTask.configure {
      if (targetMachine.operatingSystemFamily.isMacOs) {
        linkerArgs.add(linkedFile.map { "-Wl,-install_name,@rpath/${it.asFile.name}" })
      }
    }

    addJvmLibrary(project)
  }
}
