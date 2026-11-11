# JPet Store 微服务化改造说明

## 1. 目标

在原有 Spring Boot 单体 `pet-shop-backend` 基础上，完成 Spring Cloud Alibaba 微服务化：

- 拆分为 `user-service`、`product-service`、`order-service` 三个业务服务 + `gateway` 网关
- 全部注册到 Nacos，按服务名访问
- 服务间只用 OpenFeign（无 IP）
- 外部请求统一经 Spring Cloud Gateway
- 各业务服务提供 RESTful API，并新增一组真实可用的 DELETE 接口

## 2. 架构

```
Browser (base=/spa/, 同源 :8080)
   │
   ▼
gateway :8080   (Spring Cloud Gateway WebFlux + LoadBalancer + Nacos)
   ├─ /api/auth/**, /api/user/**, /api/admin/users/**      → lb://user-service
   ├─ /api/merchant/orders/**, /api/orders/**              → lb://order-service
   ├─ /api/products/**, /api/merchant/**,                   → lb://product-service
   │  /api/admin/merchants/**, /api/admin/products/**
   ├─ /api/cart/**                                         → lb://product-service（遗留，保持 404）
   └─ /spa, /spa/**, /uploads/**                            → lb://product-service
   ( /internal/** 不路由，外部不可达 )
```

| 服务 | 端口 | 数据库 | 职责 |
|---|---|---|---|
| gateway | 8080 | 无 | 统一入口、路由、负载均衡 |
| user-service | 8081 | `user_db` | 注册/登录/刷新、用户资料、管理员-用户 |
| product-service | 8082 | `commerce_db` | 商品/商家/认证/标签、SPA、uploads |
| order-service | 8083 | `commerce_db` | 购物车结算、订单、评价、商家订单 |

> 目录与 service name 映射：`pet-shop-backend/` 目录 = Nacos 服务名 `product-service`（保留目录名是为了不破坏前端构建输出路径）。

## 3. 模块结构

```
/
├── pom.xml                 # 根 parent（Boot 4.0.4 + SC 2025.1.0 + SCA 2025.1.0.0）
├── pet-shop-common/        # 共享内核：Result/JwtUtil/异常/JWT 上下文/MyBatis 配置/内部令牌
├── user-service/   :8081
├── pet-shop-backend/ :8082 # product-service
├── order-service/  :8083
├── gateway/        :8080
├── deploy/docker-compose.yml
├── db/init/01_user_db.sql
├── db/init/02_commerce_db.sql
└── docs/microservices/
```

## 4. 启动

### 4.1 基础设施

```bash
docker compose -f deploy/docker-compose.yml up -d
```

- MySQL：`127.0.0.1:3308`，root/rootpass，初始化 `user_db`、`commerce_db`
- Nacos：`127.0.0.1:8848`，控制台 `http://localhost:8088`
- Nacos 3.x 必须提供 `NACOS_AUTH_TOKEN` / `NACOS_AUTH_IDENTITY_KEY` / `NACOS_AUTH_IDENTITY_VALUE`（已在 compose 中配置）

> 首次启动 MySQL 会执行 `db/init/*.sql`。如果修改了初始化 SQL，需要 `docker compose down -v` 后重新启动。

### 4.2 构建

```bash
mvn -DskipTests install
```

### 4.3 启动服务

```bash
java -jar pet-shop-backend/target/pet_shop_backend-0.0.1-SNAPSHOT.jar   # 8082
java -jar user-service/target/user-service-0.0.1-SNAPSHOT.jar          # 8081
java -jar order-service/target/order-service-0.0.1-SNAPSHOT.jar        # 8083
java -jar gateway/target/gateway-0.0.1-SNAPSHOT.jar                    # 8080
```

或使用 `scripts/start-all.sh` / `scripts/stop-all.sh`（tmux 会话，日志在 `/tmp/opencode/jpet-logs`）。

### 4.4 验证注册

```bash
curl "http://127.0.0.1:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=100"
# {"count":4,"doms":["gateway","user-service","product-service","order-service"]}
```

## 5. 网关路由

Spring Cloud Gateway 5.x 的路由前缀是 `spring.cloud.gateway.server.webflux.routes`（旧的 `spring.cloud.gateway.routes` 不再生效）。

| 路由 | 目标 |
|---|---|
| `/api/auth/**` | user-service |
| `/api/user/**` | user-service |
| `/api/admin/users/**` | user-service |
| `/api/merchant/orders/**` | order-service（必须放在 `/api/merchant/**` 之前） |
| `/api/orders/**` | order-service |
| `/api/products/**` | product-service |
| `/api/merchant/**` | product-service |
| `/api/admin/merchants/**` | product-service |
| `/api/admin/products/**` | product-service |
| `/api/cart/**` | product-service（遗留，无控制器，保持 404） |
| `/spa`、`/spa/**`、`/uploads/**` | product-service |

## 6. 内部 Feign 契约

所有 `/internal/**` 需要 `X-Internal-Token: jpet-internal-2026`，网关不路由。

### user-service
- `GET /internal/users/{id}`
- `POST /internal/users/batch` `[ids]`
- `PUT /internal/users/{id}` `{email?,phone?,role?}`（局部更新；role 只允许 normal_user→merchant_user）

### product-service
- `POST /internal/products/checkout-reserve` `{opId,userId,items}`
- `GET /internal/products/inventory/ops/{opId}?opType=reserve`
- `POST /internal/products/inventory/release` `{opId,items?,reason?}`
- `POST /internal/products/spus/batch` `[spuIds]`
- `GET /internal/products/merchants/by-user/{userId}/catalog`
- `GET /internal/products/merchants/by-user/{userId}/application-status`

### order-service
- `GET /internal/orders/reviews/{spuId}`
- `POST /internal/orders/ratings` `[spuIds]`
- `POST /internal/orders/merchant-aggregates`

## 7. 真实 DELETE 接口矩阵

| # | 服务 | 接口 | 语义 | 守卫 | 幂等 |
|---|---|---|---|---|---|
| D1 | user | `DELETE /api/user/profile` | 账号注销（软删 + 匿名化） | normal_user；无 pending/rejected 商家申请 | 重复 → 200 |
| D2 | order | `DELETE /api/orders/{id}` | 买家从历史订单删除（`buyer_deleted=1`） | 本人；cancelled/completed | 重复 → 200 |
| D3 | order | `DELETE /api/orders/{id}/review` | 撤回评价（字段置空） | 本人；completed | 无评价 → 200 |
| D4 | product | `DELETE /api/merchant/apply` | 撤回商家入驻申请（硬删） | 本人；pending/rejected；无关联认证/商品 | 不存在 → 404 |
| D5 | product | `DELETE /api/merchant/authentications/{id}` | 撤回认证申请（硬删） | 本人商家；pending/rejected | 不存在 → 404 |
| D6 | product | `DELETE /api/merchant/products/{spuId}` | 单删 SPU（`is_active=0`，级联 SKU） | 本人商家；active | 已删 → 404 |
| D7 | product | `DELETE /api/merchant/products/skus/{skuId}` | 单删 SKU（`is_active=0`） | 本人商家；published SPU 至少保留一个 active SKU | 已删 → 400 |

> 旧批量接口 `DELETE /api/merchant/products/spu`、`/sku` 保留，前端 `merchant.ts` 仍在使用。

## 8. 下单 Saga

```
order-service:
  checkout_op_tb = RESERVING
  → product: checkout-reserve（原子扣库存、加销量，写 inventory_op_tb）
  → checkout_op_tb = RESERVED
  → 本地写 order_tb（checkout_op_id = opId）
  → checkout_op_tb = COMMITTED
  失败/超时 → COMPENSATING → product: release(opId) → COMPENSATED
定时任务：扫描 RESERVING/RESERVED/COMPENSATING，按 order_tb.checkout_op_id 判断是否已落单；
         扫描 order_inventory_op_tb 的 PENDING/FAILED，重试取消/退货回补。
```

- 库存扣减是原子条件更新：`UPDATE product_sku_tb SET stock=stock-? WHERE id=? AND stock>=?`
- 取消/退货使用稳定 opId `release:{orderId}:{action}`，落 `order_inventory_op_tb`
- 不引入 Seata，接受最终一致 + 幂等重试

## 9. 已知限制

1. product-service 与 order-service 共用 `commerce_db`，边界靠代码纪律（删除跨域 Mapper + grep 验收），不是数据库权限。
2. 补偿极端失败仍可能留下“扣库存无订单”或“订单无库存”，需要人工对账/告警。
3. 账号注销后 access token 仍有 ≤2 小时窗口；refresh 已改为查库，注销后立即失效。
4. 内部接口使用共享 `X-Internal-Token`，实验环境通过端口隔离 + 网关不路由保护，生产应使用 mTLS/独立管理网。
5. 前端零改动；`/api/cart/**` 是遗留无后端接口，网关保持 404。
6. Boot 4 MVC 使用 Jackson 3（SNAKE_CASE），项目显式使用 Jackson 2 处理 `JsonNode`/`JacksonTypeHandler`/Feign；已把共享 Jackson 2 也设为 SNAKE_CASE，Feign 显式使用 `JacksonEncoder/JacksonDecoder` + `Content-Type: application/json`。

## 10. 相关文档

- 实验报告（类图 / API / 截图说明 / 困难与遗留）：`docs/microservices/experiment-report.md`
- 详细实施计划：`docs/microservices/2026-11-04-microservices-and-delete-plan.md`
- 实测验证记录：`docs/microservices/verification.md`
- 原项目功能证明：`docs/项目功能实现与修改证明.md`
