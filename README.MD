# KMCProtocol

KMCProtocol is a Kotlin library for the Minecraft Java protocol.

## Features

- Encode and decode Minecraft packets
- Generate packet classes for any Minecraft version PrismarineJS `minecraft-data` ships
- Run several protocol versions side by side in one JVM
- Use Ktor sockets for client and server connections
- Run local protocol checks from Gradle

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

Open a connection against whichever version you need:

```kotlin
val client = Client("127.0.0.1", 25565, Protocols.V1_21_4.protocolData())
```

A `ProtocolVersion` owns its own packet registry and protocol type table, so two versions can be
served from the same process:

```kotlin
val legacy = Protocols.V1_8.protocolData()
val modern = Protocols.V1_21_4.protocolData()
```

`Connection` updates common protocol flow automatically by default: handshake state changes, login
acknowledgement, finish configuration, and compression. Flow-control packets are recognised through
the generated `HandshakeIntentPacket`, `CompressionSetupPacket`, and `StateTransitionPacket`
interfaces rather than by name. Disable the behaviour with:

```kotlin
Protocols.V1_21_4.protocolData(automaticProtocolFlow = false)
```

### Generated packet shape

Generated packets are plain `@Serializable` data classes. Wire details live on the types, not on
your call sites:

```kotlin
/**
 * `block_place` packet.
 *
 * Id: `0x3c`
 *
 * Direction: Serverbound
 *
 * State: Play
 *
 * Minecraft 1.21.4 (protocol 769)
 */
@PacketMetadata(0x3c, Direction.Serverbound)
@Serializable
data class ServerboundBlockPlacePacket(
    @ProtocolNumber(NumberEncoding.VarInt) val hand: Int,
    val location: Position,
    ...
)

@Serializable
@ProtocolBitfield(sizes = [26, 26, 12], signed = [true, true, true])
data class Position(val x: Int, val z: Int, val y: Int) {
    init {
        require(x in -33554432..33554431) { "x does not fit in 26 signed bits: " + x }
    }
}
```

Bounded fields validate on construction rather than truncating silently. Arrays, NBT, buffers,
UUIDs, options, flag sets and single-branch switches all carry compact typed annotations
(`@ProtocolArray`, `@ProtocolBinary`, `@ProtocolOption`, `@ProtocolFlags`, `@ProtocolSwitch`).
Constructs minecraft-data models dynamically that have no faithful static shape — multi-branch
switches, registry entry holders, entity metadata loops — keep a `@ProtocolType` descriptor and are
driven by the bundled `protocol.json` at runtime.

### Conformance against vanilla

`./gradlew :protocol:fabricServerTest` launches a real Fabric 1.21.4 server. Alongside the framing
and payload checks, `VanillaCodecConformanceTest` sends every generated packet to a mod that decodes
it with Mojang's own `PacketCodec` and re-encodes it, then asserts the bytes match. Vanilla consumes
every byte we emit; a small number of packets re-serialise to a different canonical form (text
components, empty bit sets) and are listed explicitly in the test.

### Plugin options

| Option | Default | Purpose |
| --- | --- | --- |
| `minecraftVersions` | `kmcMinecraftVersions` property | Versions to generate; `versions(...)` is shorthand |
| `refreshProtocolData` | `false` | Re-download data even when cached |
| `dataDirectory` | `build/kmcProtocol/minecraft-data` | Where downloaded data lands |
| `vendoredDataDirectory` | unset | Checked before downloading, for offline builds |
| `sourceSet` | `main` | Source set receiving generated code |
| `packetPackage` / `registryPackage` | `org.kvxd.kmcprotocol.*` | Generated package roots |
| `serverboundPrefix` / `clientboundPrefix` | `Serverbound` / `Clientbound` | Generated class prefixes |

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
