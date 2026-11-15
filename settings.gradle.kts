pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://nexus-playground.pleos.ai/repository/maven-releases/")
    }
}

rootProject.name = "telltale-voice"
include(":core")
// app 모듈은 Android SDK가 있을 때만 포함한다. CI(core 시험)는 -PcoreOnly=true 로 app 없이 돈다.
if (providers.gradleProperty("coreOnly").orNull != "true") {
    include(":app")
}
