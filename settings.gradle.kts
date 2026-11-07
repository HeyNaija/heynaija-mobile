rootProject.name = "heynaija-mobile"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // LiveKit's audio routing (com.github.davidliu:audioswitch) is published only there.
        maven("https://jitpack.io") {
            mavenContent { includeGroup("com.github.davidliu") }
        }
    }
}

include(":androidApp")
include(":shared")