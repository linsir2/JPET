#!/usr/bin/env bash

set -euo pipefail

root_dir="$(cd "$(dirname "$0")/.." && pwd)"
config_file="$root_dir/cloudflared/config.yml"
backend_url="http://127.0.0.1:8080/spa/"

if ! command -v cloudflared >/dev/null 2>&1; then
  echo "cloudflared is not installed. Install it first with: brew install cloudflared"
  exit 1
fi

if [[ ! -f "$config_file" ]]; then
  echo "Missing $config_file"
  echo "Copy cloudflared/config.example.yml to cloudflared/config.yml and fill in your tunnel ID and hostname first."
  exit 1
fi

if ! curl -fsS "$backend_url" >/dev/null; then
  echo "Backend is not reachable at $backend_url"
  echo "Start pet-shop-backend first, then run this script again."
  exit 1
fi

echo "Starting fixed Cloudflare tunnel using $config_file"
echo "This terminal must stay open unless you install cloudflared as a macOS service."

exec cloudflared tunnel --config "$config_file" run