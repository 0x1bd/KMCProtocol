import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.SourcesJar

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.maven.publish)
    id("org.kvxd.kmcprotocol")
}

group = "org.kvxd"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    api(libs.ktor.network)
    api(libs.kotlin.coroutines)
    api(libs.kotlinx.serialization.json)
    api(libs.knbt)

    implementation(libs.kotlin.reflect)

    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.kotlin.coroutines.test)
}

kotlin {
    jvmToolchain(21)
}

kmcProtocol {
    minecraftVersions.set(
        providers.gradleProperty("kmcMinecraftVersions").map { it.split(',').map(String::trim) }
    )
    refreshProtocolData.set(providers.gradleProperty("kmcRefreshProtocolData").map(String::toBoolean).orElse(false))
    sourceSet.set("test")
    vendoredDataDirectory.set(layout.projectDirectory.dir("src/main/minecraft-data"))
    runtimePackage.set("org.kvxd.kmcprotocol")
    packetPackage.set("org.kvxd.kmcprotocol.packets.generated")
    registryPackage.set("org.kvxd.kmcprotocol.generated")
    serverboundPrefix.set(providers.gradleProperty("kmcPacketServerboundPrefix").orElse("Serverbound"))
    clientboundPrefix.set(providers.gradleProperty("kmcPacketClientboundPrefix").orElse("Clientbound"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.register<Test>("fabricServerTest") {
    group = "verification"
    description = "Runs protocol conformance tests against an automatically launched Fabric server."

    dependsOn(":fabric-testmod:classes")
    testClassesDirs = tasks.test.get().testClassesDirs
    classpath = tasks.test.get().classpath
    shouldRunAfter(tasks.test)

    systemProperty("kmc.fabricServer.enabled", "true")
    systemProperty("kmc.projectDir", rootProject.layout.projectDirectory.asFile.absolutePath)
    systemProperty("kmc.protocolVersion", providers.gradleProperty("kmcFabricProtocolVersion").get())
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

    coordinates("org.kvxd", "kmcprotocol", version.toString())

    pom {
        name.set("KMCProtocol")
        description.set("A Kotlin library for the Minecraft Java protocol: packet encoding and decoding, generated packet classes for any version PrismarineJS minecraft-data ships, and Ktor-backed client and server connections.")
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
