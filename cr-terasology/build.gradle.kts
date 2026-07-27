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
        url = uri("http://artifactory.terasology.org:8081/artifactory/virtual-repo-live")
        isAllowInsecureProtocol = true // 😱
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
