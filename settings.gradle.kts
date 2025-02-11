pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("de.fayard.refreshVersions") version "0.60.5"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        mavenLocal()
    }
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
