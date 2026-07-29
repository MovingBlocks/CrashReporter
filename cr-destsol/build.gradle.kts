// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

plugins {
    `java-library`
    `maven-publish`
    eclipse
    idea
}

dependencies {
    api(project(":cr-core"))
}

apply(from = "$rootDir/gradle/common.gradle.kts")
