#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
LOG_DIR="${LOG_DIR:-/tmp/opencode/jpet-logs}"
mkdir -p "$LOG_DIR"

for name in product user order gateway; do
  tmux kill-session -t "jpet-$name" 2>/dev/null || true
done

tmux new-session -d -s jpet-product "java -jar pet-shop-backend/target/pet_shop_backend-0.0.1-SNAPSHOT.jar > $LOG_DIR/product.log 2>&1"
tmux new-session -d -s jpet-user "java -jar user-service/target/user-service-0.0.1-SNAPSHOT.jar > $LOG_DIR/user.log 2>&1"
tmux new-session -d -s jpet-order "java -jar order-service/target/order-service-0.0.1-SNAPSHOT.jar > $LOG_DIR/order.log 2>&1"
tmux new-session -d -s jpet-gateway "java -jar gateway/target/gateway-0.0.1-SNAPSHOT.jar > $LOG_DIR/gateway.log 2>&1"

echo "started: product 8082, user 8081, order 8083, gateway 8080"
echo "logs: $LOG_DIR"
echo "check: ss -ltn | grep -E ':808[0-3]'"
echo "nacos: curl 'http://127.0.0.1:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=100'"
