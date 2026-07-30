plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlinx.serialization) apply false
    alias(libs.plugins.maven.publish) apply false
}

tasks.register("publishToMavenCentral") {
    group = "publishing"
    description = "Uploads the library, the extensions and the Gradle plugin to Maven Central, awaiting manual release."
    dependsOn(":protocol:publishToMavenCentral")
    dependsOn(":extensions:publishToMavenCentral")
    dependsOn(gradle.includedBuild("gradle-plugin").task(":publishToMavenCentral"))
}

tasks.register("publishAndReleaseToMavenCentral") {
    group = "publishing"
    description = "Uploads and releases the library, the extensions and the Gradle plugin to Maven Central."
    dependsOn(":protocol:publishAndReleaseToMavenCentral")
    dependsOn(":extensions:publishAndReleaseToMavenCentral")
    dependsOn(gradle.includedBuild("gradle-plugin").task(":publishAndReleaseToMavenCentral"))
}

tasks.register("publishToMavenLocal") {
    group = "publishing"
    description = "Publishes the library, the extensions and the Gradle plugin to the local Maven repository."
    dependsOn(":protocol:publishToMavenLocal")
    dependsOn(":extensions:publishToMavenLocal")
    dependsOn(gradle.includedBuild("gradle-plugin").task(":publishToMavenLocal"))
}
