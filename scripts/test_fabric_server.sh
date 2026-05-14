#!/usr/bin/env bash
set -euo pipefail

PORT="${KMC_FABRIC_SERVER_PORT:-25567}"
RUN_DIR="${KMC_FABRIC_SERVER_DIR:-fabric-testmod/run}"

mkdir -p "$RUN_DIR"

cat > "$RUN_DIR/eula.txt" <<EOL
eula=true
EOL

cat > "$RUN_DIR/server.properties" <<EOL
online-mode=false
server-ip=127.0.0.1
server-port=$PORT
enable-query=false
enable-rcon=false
network-compression-threshold=1
max-players=4
motd=KMCProtocol Fabric conformance server
EOL

exec ./gradlew --no-daemon :fabric-testmod:runServer \
  -PkmcFabricServerPort="$PORT" \
  -PkmcFabricServerRunDir="../$RUN_DIR"
