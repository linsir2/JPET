#!/usr/bin/env bash

set -euo pipefail

backend_url="http://127.0.0.1:8080/spa/"

if ! curl -fsS "$backend_url" >/dev/null; then
  echo "Backend is not reachable at $backend_url"
  echo "Start pet-shop-backend first, then run this script again."
  exit 1
fi

echo "Starting Cloudflare quick tunnel..."
echo "Share the generated public URL with /spa/ appended."

exec cloudflared tunnel --url http://127.0.0.1:8080