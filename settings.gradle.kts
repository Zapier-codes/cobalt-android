pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Required for NewPipeExtractor (org.schabi.newpipe:extractor), which is
        // not published to Maven Central. Added in Phase 2 for the Shorts feed's
        // NewPipe-based YouTube source. See ARCHITECTURE.md Phase 2.
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "CobaltAndroid"
include(":app")
