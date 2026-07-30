plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlinx.serialization) apply false
    alias(libs.plugins.maven.publish) apply false
}

tasks.register("publishToMavenCentral") {
    group = "publishing"
    description = "Publishes the library, the extensions and the Gradle plugin to Maven Central."
    dependsOn(gradle.includedBuild("gradle-plugin").task(":publishToMavenCentral"))
}

tasks.register("publishToMavenLocal") {
    group = "publishing"
    description = "Publishes the library, the extensions and the Gradle plugin to the local Maven repository."
    dependsOn(gradle.includedBuild("gradle-plugin").task(":publishToMavenLocal"))
}
