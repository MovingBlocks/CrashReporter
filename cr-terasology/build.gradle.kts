// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

plugins {
    `java-library`
    `maven-publish`
    eclipse
    idea
}

// We use both Maven Central and our own Artifactory instance, which contains module builds, extra libs, and so on
repositories {
    mavenCentral {
        content {
            // This is first choice for most java dependencies, but assume we'll need to check our
            // own repository for things from our own organization.
            // (This is an optimization so gradle doesn't try to find our hundreds of modules in 3rd party repos)
            excludeGroupByRegex("org.terasology(..+)?")
        }
    }
    maven {
        name = "Terasology Artifactory"
        url = uri("https://artifactory.terasology.io/artifactory/virtual-repo-live")
    }
}

dependencies {
    api(project(":cr-core"))
}

java {
    targetCompatibility = JavaVersion.VERSION_1_8
}

apply(from = "$rootDir/gradle/common.gradle.kts")
