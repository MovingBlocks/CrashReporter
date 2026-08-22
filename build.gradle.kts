// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
}

// For generating IntelliJ project files
plugins {
    idea
    id("nebula.release") version "21.0.0"
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

// Pass -PnoLock to resolve every dependency range fresh against whatever version satisfies it in
// Gradle's already-cached repository metadata (which is itself refreshed at most once per 24h for a
// dynamic version - add --refresh-dependencies too if you need to force a check past that), ignoring
// gradle.lockfile entirely for that one build - useful for locally trying out an update before
// committing to it. Since locking isn't activated at all in that case, nothing gets checked against
// or written to the lockfile either.
//
// Locks every resolvable configuration (compileClasspath, runtimeClasspath, the test and
// annotationProcessor classpaths, etc.), not just compileClasspath - otherwise dependencies unique
// to those other configurations could still silently float to a newer version picked up from their
// declared range, undermining the "everyone's build uses the same versions" guarantee this is for.
if (!project.hasProperty("noLock")) {
    subprojects {
        dependencyLocking {
            lockAllConfigurations()
        }
    }
}
