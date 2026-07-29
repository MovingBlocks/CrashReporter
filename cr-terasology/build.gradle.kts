// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

plugins {
    `java-library`
    `maven-publish`
    eclipse
    idea
}

repositories {
    maven {
        name = "Terasology Artifactory"
        url = uri("https://artifactory.terasology.io/artifactory/virtual-repo-live")
    }
    mavenCentral()
    maven {
        name = "JBoss Public Maven Repository Group"
        url = uri("https://repository.jboss.org/nexus/content/repositories/public/")
    }
}

dependencies {
    api(project(":cr-core"))
}

java {
    targetCompatibility = JavaVersion.VERSION_1_8
}

apply(from = "$rootDir/gradle/common.gradle.kts")
