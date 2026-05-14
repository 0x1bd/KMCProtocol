# KMCProtocol

KMCProtocol is a kotlin library which implements the minecraft java protocol.

## Features
- Encode and decode minecraft packets
- Generate packet classes from PrismarineJS `minecraft-data`
- Easily add custom packets
- Networking with Ktor

## Packet generation

Packet classes are generated during `compileKotlin` from the cached
`protocol/src/main/minecraft-data/pc/1.21.4/protocol.json` source file.

Use a different maintained `minecraft-data` protocol version with:

```bash
./gradlew :protocol:generateMinecraftPackets -PkmcMinecraftVersion=1.21.4
```

Refresh the cached source from PrismarineJS with:

```bash
./gradlew :protocol:generateMinecraftPackets -PkmcMinecraftVersion=1.21.4 -PkmcRefreshProtocolData=true
```

## Packet tests

The regular test suite validates generated packet registration and local
serialization using fabricated sample values instead of hand-written packet
instances:

```bash
./gradlew :protocol:test
```

Run the automated Fabric conformance lane:

```bash
./gradlew :protocol:fabricServerTest
```

This builds `fabric-testmod`, launches a local offline Fabric server with the
stock 1.21.4 server stack, performs handshake, login, configuration, and custom
payload exchange through KMCProtocol, then shuts the server down. It is intended
for CI regression detection without manual server setup.

## Example

The `example` subproject contains a small status-ping client that consumes the
generated protocol module like an application would:

```bash
./gradlew :example:run --args="127.0.0.1 25565"
```

It also contains an offline-mode 1.21.4 play-state chat smoke server. It accepts
a vanilla client, completes login/configuration, and sends system chat messages,
but intentionally does not send terrain chunks:

```bash
./gradlew :example:runPlayStateChatSmokeServer --args="25565"
```
