plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    implementation("com.squareup:kotlinpoet:2.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
}

gradlePlugin {
    plugins {
        create("kmcProtocol") {
            id = "org.kvxd.kmcprotocol"
            implementationClass = "org.kvxd.kmcprotocol.build.KmcProtocolPlugin"
        }
    }
}
