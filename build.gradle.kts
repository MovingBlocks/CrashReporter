// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

// For generating IntelliJ project files
plugins {
    idea
}

tasks.wrapper {
    gradleVersion = "9.6.1"
    distributionSha256Sum = "9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14"
}

// Using this instead of allprojects allows this project to be embedded yet not affect parent projects
group = "org.terasology"
subprojects {
    group = "org.terasology.crashreporter"
}
