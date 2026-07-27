// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

import org.gradle.api.plugins.quality.Checkstyle

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
        url = uri("http://artifactory.terasology.org:8081/artifactory/virtual-repo-live")
        isAllowInsecureProtocol = true // 😱
    }
}

val codeMetrics = configurations.create("codeMetrics")

dependencies {

    codeMetrics("org.terasology.config:codemetrics:1.1.0@zip")

    checkstyle("com.puppycrawl.tools:checkstyle:6.17")
    pmd("net.sourceforge.pmd:pmd-core:5.4.1")
    pmd("net.sourceforge.pmd:pmd-java:5.4.1")

    implementation("org:jpastebin:1.0.1")
    implementation("org.apache.httpcomponents:httpcomponents-client:4.5.2")
    implementation("org.apache.httpcomponents:httpmime:4.5.2")

    testImplementation("junit:junit:4.12")
    testImplementation("org.mockito:mockito-core:2.7.22")
    testImplementation("org.slf4j:slf4j-api:1.7.21")

    testRuntimeOnly("ch.qos.logback:logback-classic:1.1.7")

    implementation("com.google.guava:guava:19.0")

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

tasks.named<Checkstyle>("checkstyleMain") {
    doFirst {
        resources.text.fromArchiveEntry(codeMetrics, "checkstyle/suppressions.xml").asFile()
    }
}

tasks.named<Checkstyle>("checkstyleTest") {
    doFirst {
        resources.text.fromArchiveEntry(codeMetrics, "checkstyle/suppressions.xml").asFile()
    }
}

checkstyle {
    isIgnoreFailures = true
    config = resources.text.fromArchiveEntry(codeMetrics, "checkstyle/checkstyle.xml")
    configProperties["samedir"] = config.asFile().parent
}

pmd {
    isIgnoreFailures = true
    ruleSetConfig = resources.text.fromArchiveEntry(codeMetrics, "pmd/pmd.xml")
    ruleSets = listOf()
}

tasks.javadoc {
    isFailOnError = false
}
