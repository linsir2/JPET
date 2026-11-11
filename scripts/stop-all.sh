#!/usr/bin/env bash
set -euo pipefail

for name in gateway order user product; do
  tmux kill-session -t "jpet-$name" 2>/dev/null || true
done

echo "stopped jpet-product / jpet-user / jpet-order / jpet-gateway"
