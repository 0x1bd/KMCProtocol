# KMCProtocol

KMCProtocol is a Kotlin library for the Minecraft Java protocol.

## Features

- Encode and decode Minecraft packets
- Generate packet classes for any Minecraft version PrismarineJS `minecraft-data` ships
- Run several protocol versions side by side in one JVM
- Use Ktor sockets for client and server connections
- Run local protocol checks from Gradle

## Artifacts

| Coordinates                          | What                                                                        |
|--------------------------------------|-----------------------------------------------------------------------------|
| `org.kvxd:kmcprotocol`               | runtime library                                                             |
| `org.kvxd:kmcprotocol-gradle-plugin` | codegen plugin, id `org.kvxd.kmcprotocol`                                   |
| `org.kvxd:kmcprotocol-extensions`    | optional decoders for chunk sections, plugin messages and chat session keys |

## Documentation

Full docs live the github wiki:
[Home](https://github.com/0x1bd/KMCProtocol/wiki)

## Gradle Plugin

Apply the plugin in a Kotlin/JVM project and list the versions you want:

```kotlin
plugins {
    kotlin("jvm")
    id("org.kvxd.kmcprotocol")
}

dependencies {
    implementation("org.kvxd:kmcprotocol:<version>")
}

kmcProtocol {
    versions("1.8", "1.12.2", "1.20.4", "1.21.4")
}
```

The plugin resolves each version through `minecraft-data`'s `dataPaths.json`, downloads
`protocol.json` and `version.json` into `build/`, generates Kotlin packet sources, and adds the
sources and protocol resources to the configured source set.

Each version gets its own package, so versions never collide:

```
org.kvxd.kmcprotocol.packets.generated.v1_8.play.clientbound.ClientboundChatPacket
org.kvxd.kmcprotocol.packets.generated.v1_21_4.play.clientbound.ClientboundChatPacket
```

Versions are also collected into a generated `Protocols` catalog:

```kotlin
import org.kvxd.kmcprotocol.generated.Protocols

Protocols.V1_21_4                      // a specific version
Protocols.latest                       // highest protocol number generated
Protocols.versions                     // every generated version
Protocols.byProtocolVersion(47)        // 1.8, or null
Protocols.requireMinecraftVersion("1.12.2")
```

Open a connection against whichever version you need. Connecting suspends, so it never blocks the
calling thread:

```kotlin
val client = Client.connect("127.0.0.1", 25565, Protocols.V1_21_4.protocolData())

client.send(ServerboundSetProtocolPacket(769, "localhost", 25565, 1))
val response = client.receive()
```

Connections are pull-based. `receive()` throws when the peer disconnects, `receiveOrNull()` returns
`null` instead, and `incoming` is a `Flow` that completes on a clean disconnect:

```kotlin
client.incoming.collect { packet -> println(packet) }
```

Servers bind up front and hand out sessions the same way:

```kotlin
val server = Server.bind(InetSocketAddress("0.0.0.0", 25565)) { Protocols.V1_21_4.protocolData() }

server.sessions.collect { session ->
    launch { session.incoming.collect { packet -> handle(session, packet) } }
}
```

Both `Client` and `Server` share one `SelectorManager` by default; pass your own to
`Client.connect`/`Server.bind` if you want an isolated one.

### Plugin options

| Option                                    | Default                            | Purpose                                            |
|-------------------------------------------|------------------------------------|----------------------------------------------------|
| `minecraftVersions`                       | `kmcMinecraftVersions` property    | Versions to generate; `versions(...)` is shorthand |
| `refreshProtocolData`                     | `false`                            | Re-download data even when cached                  |
| `dataDirectory`                           | `build/kmcProtocol/minecraft-data` | Where downloaded data lands                        |
| `vendoredDataDirectory`                   | unset                              | Checked before downloading, for offline builds     |
| `sourceSet`                               | `main`                             | Source set receiving generated code                |
| `packetPackage` / `registryPackage`       | `org.kvxd.kmcprotocol.*`           | Generated package roots                            |
| `serverboundPrefix` / `clientboundPrefix` | `Serverbound` / `Clientbound`      | Generated class prefixes                           |

Generating many versions at once produces a lot of Kotlin. This repository builds eleven versions
spanning 1.7 to 26.1 and raises the Kotlin daemon heap in `gradle.properties`:

```properties
kotlin.daemon.jvmargs=-Xmx4g
```

Every version minecraft-data ships (71, excluding the Classic `0.30c` protocol, which has no
protocol states) generates and round-trips. Building all of them at once needs `-Xmx9g` for the
Kotlin daemon.

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

## License

MIT. See [LICENSE](LICENSE).
