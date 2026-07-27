// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

// For generating IntelliJ project files
plugins {
    idea
}

tasks.wrapper {
    gradleVersion = "9.6.1"
}

// Using this instead of allprojects allows this project to be embedded yet not affect parent projects
group = "org.terasology"
subprojects {
    group = "org.terasology.crashreporter"
}
