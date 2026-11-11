#!/usr/bin/env bash

set -euo pipefail

backend_url="http://127.0.0.1:8080/spa/"
lan_ip="$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || true)"

if ! curl -fsS "$backend_url" >/dev/null; then
  echo "Backend is not reachable at $backend_url"
  echo "Start pet-shop-backend first, then run this script again."
  exit 1
fi

if [[ -n "$lan_ip" ]]; then
  echo "LAN URL: http://$lan_ip:8080/spa/"
else
  echo "LAN IP not detected automatically."
fi

echo "Starting public tunnel..."
echo "Keep this terminal open while teammates are using the public link."

exec ssh \
  -o StrictHostKeyChecking=no \
  -o ServerAliveInterval=30 \
  -R 80:localhost:8080 \
  nokey@localhost.run