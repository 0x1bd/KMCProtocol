plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
    implementation("com.squareup:kotlinpoet:1.18.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}

gradlePlugin {
    plugins {
        create("kmcProtocolPackets") {
            id = "org.kvxd.kmcprotocol.packets"
            implementationClass = "org.kvxd.kmcprotocol.build.KmcProtocolPacketsPlugin"
        }
    }
}
