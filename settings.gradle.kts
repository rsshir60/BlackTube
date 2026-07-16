/*
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
        maven(url = "https://repo.clojars.org")
    }
}
include (":app")

// BlackTube: Use the local PipePipeExtractor when it is present (dev builds).
// On F-Droid's build server this directory does not exist, so Gradle falls back
// to resolving com.github.TeamNewPipe:NewPipeExtractor from JitPack using the
// commit hash pinned in gradle/libs.versions.toml.
val localExtractorDir = file("./NewPipeExtractor")
if (localExtractorDir.exists()) {
    includeBuild("./NewPipeExtractor") {
        dependencySubstitution {
            substitute(module("com.github.TeamNewPipe:NewPipeExtractor")).using(project(":extractor"))
        }
    }
}






