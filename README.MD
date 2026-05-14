# KMCProtocol

KMCProtocol is a Kotlin library for the Minecraft Java protocol.

## Features

- Encode and decode Minecraft packets
- Generate packet classes from PrismarineJS `minecraft-data`
- Use Ktor sockets for client and server connections
- Run local protocol checks from Gradle

## Gradle Plugin

Apply the plugin in a Kotlin/JVM project:

```kotlin
plugins {
    kotlin("jvm")
    id("org.kvxd.kmcprotocol")
}

dependencies {
    implementation("org.kvxd:kmcprotocol:<version>")
}

kmcProtocol {
    minecraftVersion.set("1.21.4")
}
```

The plugin downloads protocol data into `build/`, generates Kotlin packet
sources, and adds the generated sources and resources to the configured source
set. It does not require `src/main/minecraft-data` in user projects.

Use the generated protocol factory when opening connections:

```kotlin
val client = Client("127.0.0.1", 25565, GeneratedProtocol.protocolData())
```

`Connection` updates common protocol flow automatically by default: handshake
state changes, login acknowledgement, finish configuration, and compression.
Disable this with:

```kotlin
GeneratedProtocol.protocolData(automaticProtocolFlow = false)
```

Generated protocol types model the wire format. Keep domain helpers, such as
vector math, in handwritten runtime types or adapters.

## Development

Run unit tests:

```bash
./gradlew :protocol:test
```

Run Fabric conformance tests:

```bash
./gradlew :protocol:fabricServerTest
```

Run the status-ping example:

```bash
./gradlew :example:run --args="127.0.0.1 25565"
```

Prepare and run a local vanilla server from a plugin-applied module:

```bash
./gradlew :example:prepareMinecraftServer
./gradlew :example:runMinecraftServer
```
