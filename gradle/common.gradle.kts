// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.authentication.http.BasicAuthentication

// Applied via apply(from = ...) into each subproject's build script, so it must use the legacy
// apply(plugin = ...) form (the plugins {} block isn't available in script plugins) and the
// explicit configure<T> form (script plugins don't get the generated type-safe accessors a
// project's own primary build script does).
apply(plugin = "java")
apply(plugin = "maven-publish")

configure<PublishingExtension> {
    publications {
        create<MavenPublication>("CrashReporter") {
            // Without this we get a .pom with no dependencies
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "TerasologyOrg"
            isAllowInsecureProtocol = true // 😱 - no https on our Artifactory yet

            val publishRepo = if (rootProject.hasProperty("publishRepo")) {
                // This first option is good for local testing, you can set a full explicit target repo in gradle.properties
                (rootProject.property("publishRepo") as String).also {
                    logger.info("Changing PUBLISH repoKey set via Gradle property to {}", it)
                }
            } else {
                // Support override from the environment to use a different target publish org
                var deducedPublishRepo = System.getenv("PUBLISH_ORG")?.takeIf { it.isNotEmpty() } ?: "libs"
                // Base final publish repo on whether we're building a snapshot or a release
                deducedPublishRepo += if (project.version.toString().endsWith("SNAPSHOT")) {
                    "-snapshot-local"
                } else {
                    "-release-local"
                }
                logger.info("The final deduced publish repo is {}", deducedPublishRepo)
                deducedPublishRepo
            }
            url = uri("http://artifactory.terasology.org/artifactory/$publishRepo")

            if (rootProject.hasProperty("mavenUser") && rootProject.hasProperty("mavenPass")) {
                credentials {
                    username = rootProject.property("mavenUser") as String
                    password = rootProject.property("mavenPass") as String
                }
                authentication {
                    create<BasicAuthentication>("basic")
                }
            }
        }
    }
}
