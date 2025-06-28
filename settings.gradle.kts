pluginManagement {
    repositories {
        maven {
            url = uri("https://jitpack.io")
        }
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
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "im.agg.android12-system-jars") {
                useModule("com.github.agg23.android12-framework-plugin:android12-system-jars-plugin:${requested.version}")
            }
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SDKBridge"
include(":bridge-core")
include(":example")
include(":sdk")
include(":bridge-system")
