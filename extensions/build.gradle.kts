import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.SourcesJar

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.maven.publish)
}

group = "org.kvxd"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":protocol"))

    testImplementation(kotlin("test-junit5"))
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

mavenPublishing {
    configure(
        KotlinJvm(
            javadocJar = JavadocJar.Javadoc(),
            sourcesJar = SourcesJar.Sources()
        )
    )

    publishToMavenCentral()
    signAllPublications()

    coordinates("org.kvxd", "kmcprotocol-extensions", version.toString())

    pom {
        name.set("KMCProtocol Extensions")
        description.set("Optional decoders for the Minecraft protocol payloads that minecraft-data leaves as opaque byte arrays: paletted chunk sections, chunk biome sections, plugin messages, and chat session public keys.")
        inceptionYear.set("2026")
        url.set("https://github.com/0x1bd/KMCProtocol/")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("https://opensource.org/licenses/MIT")
            }
        }

        developers {
            developer {
                id.set("kvxd")
                name.set("kvxd")
                email.set("0x1bd@proton.me")
            }
        }

        scm {
            url.set("https://github.com/0x1bd/KMCProtocol/")
            connection.set("scm:git:git://github.com/0x1bd/KMCProtocol.git")
            developerConnection.set("scm:git:ssh://git@github.com/0x1bd/KMCProtocol.git")
        }
    }
}
