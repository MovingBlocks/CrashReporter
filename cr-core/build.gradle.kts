// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.Pmd

plugins {
    `java-library`
    eclipse
    idea
    checkstyle
    pmd
    `maven-publish`
}

apply(from = "$rootDir/gradle/common.gradle.kts")

val env: Map<String, String> = System.getenv()
val versionInfoFile = File(sourceSets.main.get().output.resourcesDir, "versionInfo.properties")

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
    withJavadocJar()
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
    // JBoss Maven Repository requried to fetch `org.jpastebin` dependency for CrashReporter
    // https://developer.jboss.org/docs/DOC-11377
    maven {
        name = "JBoss Public Maven Repository Group"
        url = uri("https://repository.jboss.org/nexus/content/repositories/public/")
        content {
            includeModule("org", "jpastebin")
        }
    }
    maven {
        name = "Terasology Artifactory"
        url = uri("https://artifactory.terasology.io/artifactory/virtual-repo-live")
    }
}

val codeMetrics = configurations.create("codeMetrics")

dependencies {

    codeMetrics("org.terasology.config:codemetrics:2.2.0@zip")

    checkstyle("com.puppycrawl.tools:checkstyle:10.2")
    pmd("net.sourceforge.pmd:pmd-ant:7.0.0-rc4")
    pmd("net.sourceforge.pmd:pmd-core:7.0.0-rc4")
    pmd("net.sourceforge.pmd:pmd-java:7.0.0-rc4")

    implementation("org:jpastebin:1.0.1")
    implementation("org.apache.httpcomponents:httpclient:4.5.13")
    implementation("org.apache.httpcomponents:httpmime:4.5.13")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.6.0")
    testImplementation("org.slf4j:slf4j-api:2.0.11")

    testRuntimeOnly("ch.qos.logback:logback-classic:1.4.14")

    implementation("com.google.guava:guava:31.1-jre")

    // But on the other hand to be able to run the unit tests successfully while embedded we still do need this
    if (rootProject.name == "Terasology") {
        testImplementation("com.google.http-client:google-http-client-jackson2:1.20.0")
    }
}

fun convertGitBranch(gitBranch: String?): String? {
    // Remove "origin/" from "origin/develop"
    return if (gitBranch.isNullOrEmpty()) null else gitBranch.substringAfterLast("/")
}

val createVersionInfoFile = tasks.register("createVersionInfoFile") {
    doLast {
        logger.lifecycle("Creating $versionInfoFile")
        ant.withGroovyBuilder {
            "propertyfile"("file" to versionInfoFile) {
                "entry"("key" to "buildNumber", "value" to env["BUILD_NUMBER"])
                "entry"("key" to "buildId", "value" to env["BUILD_ID"])
                "entry"("key" to "buildTag", "value" to env["BUILD_TAG"])
                "entry"("key" to "buildUrl", "value" to env["BUILD_URL"])
                "entry"("key" to "jobName", "value" to env["JOB_NAME"])
                "entry"("key" to "gitBranch", "value" to convertGitBranch(env["GIT_BRANCH"]))
                "entry"("key" to "gitCommit", "value" to env["GIT_COMMIT"])
                "entry"("key" to "displayVersion", "value" to version)
            }
        }
    }
}

tasks.jar {
    dependsOn(createVersionInfoFile)
}

val runInteractiveTest = tasks.register<JavaExec>("runInteractiveTest") {
    dependsOn(tasks.named("testClasses"))
    mainClass.set("org.terasology.crashreporter.InteractiveTestCases")
    classpath = files(sourceSets.test.get().runtimeClasspath)
    args = listOf("setupForExtraLongMessageException", "src/test/resources/lengthy_logfile.log", "en-US")
}

// checkstyle.xml's own SuppressionFilter references ${config_loc}/suppressions.xml, Checkstyle's
// built-in "directory containing the config file" property - that's only populated correctly
// when the config is loaded from a real directory on disk, not from an in-place archive-entry
// read (which extracts each entry to its own isolated temp location, so checkstyle.xml and
// suppressions.xml never end up as real siblings). Extract the already-downloaded codeMetrics
// zip once instead, and point checkstyle/pmd at the extracted directory.
val extractCodeMetrics = tasks.register<Copy>("extractCodeMetrics") {
    from(codeMetrics.map { zipTree(it) })
    into(layout.buildDirectory.dir("codeMetrics"))
}

tasks.withType<Checkstyle>().configureEach {
    dependsOn(extractCodeMetrics)
}

tasks.withType<Pmd>().configureEach {
    dependsOn(extractCodeMetrics)
}

checkstyle {
    isIgnoreFailures = true
    configDirectory.set(layout.buildDirectory.dir("codeMetrics/checkstyle"))
}

pmd {
    isIgnoreFailures = true
    ruleSetFiles = files(layout.buildDirectory.file("codeMetrics/pmd/pmd.xml"))
    ruleSets = listOf()
}

tasks.javadoc {
    isFailOnError = false
}

tasks.test {
    useJUnitPlatform()
    // InteractiveTestCases is a manual runner (see runInteractiveTest), not an automated test -
    // there are no @Test methods in this project.
    failOnNoDiscoveredTests.set(false)
}
