package com.ibm.wala.gradle

import java.net.URI
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.repositories

fun Project.adHocDownload(
    uri: URI,
    name: String,
    version: String? = null,
    classifier: String? = null,
    ext: String,
): Configuration = run {
  val repository =
      repositories.ivy {
        isAllowInsecureProtocol = true
        url = uri
        patternLayout { artifact("/[artifact](-[revision])(-[classifier])(.[ext])") }
        metadataSources { artifact() }
      }

  repositories {
    exclusiveContent {
      forRepositories(repository)
      filter { includeGroup(uri.authority) }
    }
  }

  return configurations.detachedConfiguration(
      dependencies.create(
          group = uri.authority,
          name = name,
          version = version,
          classifier = classifier,
          ext = ext))
}
