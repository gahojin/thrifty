pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("jp.co.gahojin.refreshVersions") version "0.2.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        mavenLocal()
    }
}

refreshVersions {
    sortSection = true
}

rootProject.name = "thrifty"
include("thrifty-schema")
include("thrifty-runtime")
include("thrifty-java-codegen")
include("thrifty-kotlin-codegen")
include("thrifty-compiler")
include("thrifty-example-postprocessor")
include("thrifty-compiler-plugins")
include("thrifty-test-server")
include("thrifty-integration-tests")
include("thrifty-gradle-plugin")
