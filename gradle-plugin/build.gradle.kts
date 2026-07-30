import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    id("com.vanniktech.maven.publish.base") version "0.36.0"
}

group = "org.kvxd"
version = "0.1.0"

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    implementation("com.squareup:kotlinpoet:2.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
}

kotlin {
    jvmToolchain(21)
}

gradlePlugin {
    website.set("https://github.com/0x1bd/KMCProtocol/")
    vcsUrl.set("https://github.com/0x1bd/KMCProtocol.git")

    plugins {
        create("kmcProtocol") {
            id = "org.kvxd.kmcprotocol"
            implementationClass = "org.kvxd.kmcprotocol.build.KmcProtocolPlugin"
            displayName = "KMCProtocol"
            description = "Generates Kotlin Minecraft packet classes from PrismarineJS minecraft-data for every configured protocol version."
            tags.set(listOf("minecraft", "protocol", "kotlin", "codegen"))
        }
    }
}

mavenPublishing {
    configure(
        GradlePlugin(
            javadocJar = JavadocJar.Javadoc(),
            sourcesJar = SourcesJar.Sources()
        )
    )

    publishToMavenCentral()
    signAllPublications()

    coordinates("org.kvxd", "kmcprotocol-gradle-plugin", version.toString())

    pom {
        name.set("KMCProtocol Gradle Plugin")
        description.set("Gradle plugin that downloads PrismarineJS minecraft-data protocol definitions and generates Kotlin packet classes and a protocol catalog for every configured Minecraft version.")
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
