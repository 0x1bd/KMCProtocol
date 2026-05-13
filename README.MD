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

The regular test suite validates generated packet registration without manually
instantiating every packet class:

```bash
./gradlew :protocol:test
```

Run the opt-in real server status-flow test against a running Paper/Fabric
server:

```bash
./gradlew :protocol:realServerTest -PkmcRealServerHost=127.0.0.1 -PkmcRealServerPort=25565
```

Or let the test start a local server jar:

```bash
./gradlew :protocol:realServerTest -PkmcRealServerJar=/path/to/server.jar
```

Run the Fabric packet ACK fixture, which loads a tiny server-side Fabric mod and
acknowledges a generated configuration custom payload from PKMC:

```bash
KMC_FABRIC_SERVER_PORT=25567 scripts/test_fabric_server.sh
./gradlew :protocol:fabricServerTest -PkmcFabricServerHost=127.0.0.1 -PkmcFabricServerPort=25567
```
