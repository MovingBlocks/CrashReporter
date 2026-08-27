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
    // jpastebin needs these at runtime (see its own embedded META-INF/maven/org/jpastebin/pom.xml,
    // which pins Jackson 2.9.7) but the POM Gradle actually resolves from the JBoss repo is an
    // empty Nexus-generated stub with no <dependencies> at all - so without declaring these
    // ourselves, PastebinUploadRunnable.call() throws NoClassDefFoundError the first time it
    // touches a Jackson class, only when someone actually clicks "Upload". 2.9.7 is a 2018 release
    // with known CVEs; Jackson's 2.x line keeps this level of API (ObjectMapper, TypeReference,
    // annotations) stable, so a current release is a safe drop-in rather than matching the old pin.
    // The BOM (not three separately-pinned versions) because jackson-annotations renumbered its own
    // versioning away from core/databind's x.y.z scheme starting at 2.20 - the BOM is what keeps the
    // three resolvable together regardless of a given module's own version string.
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.22.2"))
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.core:jackson-core")
    implementation("com.fasterxml.jackson.core:jackson-annotations")
    implementation("org.apache.httpcomponents:httpclient:4.5.13")
    implementation("org.apache.httpcomponents:httpmime:4.5.13")
    // GitHub issue-creation API request/response bodies (GitHubIssueApiClient) - small,
    // dependency-free, no reason to hand-roll JSON escaping/parsing instead.
    implementation("org.json:json:20260814")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.slf4j:slf4j-api:2.0.18")

    testRuntimeOnly("ch.qos.logback:logback-classic:1.6.0")

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

// InteractiveTestCases.main only ever reports against Paths.get(".") - the JavaExec task's own
// working directory - regardless of the log-file-name argument it's passed (that argument is
// logged, never read again; see InteractiveTestCases.main). So the only way to control which log
// files the reporter dialog actually finds is what's sitting in that directory when the task
// runs. Point workingDir at a dedicated build-output folder and seed it fresh every run, so
// "run the interactive test" reliably shows multiple tabs (and real version/module lines for the
// GitHub pre-fill, #53 item 3) without anyone hand-creating files first.
val interactiveTestLogDir = layout.buildDirectory.dir("interactiveTestLogs")

val seedInteractiveTestLogs = tasks.register("seedInteractiveTestLogs") {
    doLast {
        val dir = interactiveTestLogDir.get().asFile
        dir.mkdirs()
        dir.listFiles { file -> file.name.endsWith(".log") }?.forEach { it.delete() }

        // init/menu are the only phases LoggingContext actually defines today (INIT_PHASE, MENU) -
        // a real session never produces more than these two. "game" is a synthetic third file,
        // included purely to exercise the reporter's N>2-tabs case (#53 item 1's alphabetical
        // ordering) since the engine itself doesn't currently produce that scenario.
        File(dir, "Terasology-init.log").writeText(
            "10:00:00.000 [main] INFO  o.t.e.version.TerasologyVersion - " +
                "[buildNumber=42, buildId=42, buildTag=Terasology-42, buildUrl=, " +
                "jobName=Terasology/engine/develop, dateTime=2026-08-20, displayVersion=Aeternum, " +
                "engineVersion=5.4.0-SNAPSHOT]\n" +
                "10:00:00.100 [main] INFO  o.t.e.core.TerasologyEngine - OS: Linux, arch: amd64, version: 6.12.85\n" +
                "10:00:01.000 [main] INFO  o.t.e.core.modes.loadProcesses.RegisterMods - " +
                "Activating module: engine:5.4.0-SNAPSHOT\n" +
                "10:00:01.010 [main] INFO  o.t.e.core.modes.loadProcesses.RegisterMods - " +
                "Activating module: CoreAssets:2.4.0\n"
        )
        File(dir, "Terasology-menu.log").writeText(
            "10:05:00.000 [main] INFO  o.t.e.core.modes.StateMainMenu - Entered main menu\n"
        )
        File(dir, "Terasology-game.log").writeText(
            "10:10:00.000 [main] INFO  o.t.e.core.modes.StateIngame - World loaded\n" +
                "10:10:05.123 [main] ERROR o.t.e.core.TerasologyEngine - Uncaught exception in main loop\n" +
                "java.lang.NullPointerException: world was null\n" +
                "\tat org.terasology.engine.core.TerasologyEngine.run(TerasologyEngine.java:200)\n"
        )
    }
}

val runInteractiveTest = tasks.register<JavaExec>("runInteractiveTest") {
    dependsOn(tasks.named("testClasses"), seedInteractiveTestLogs)
    mainClass.set("org.terasology.crashreporter.InteractiveTestCases")
    classpath = files(sourceSets.test.get().runtimeClasspath)
    workingDir = interactiveTestLogDir.get().asFile
    // The 2nd arg (log file name) is unused by InteractiveTestCases.main - see the comment above -
    // kept only to hold the 3rd arg (locale) in its expected position.
    args = listOf("setupForExtraLongMessageException", "(unused)", "en-US")
    doFirst {
        logger.lifecycle("Seeded $workingDir with 3 sample log files - the reporter dialog should show 3 tabs.")
    }
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
