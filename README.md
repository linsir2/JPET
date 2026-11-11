# JPet Store · Spring Cloud Alibaba 微服务版

在原有 JetPetStore 单体基础上完成的微服务化改造：`gateway` + `user-service` + `product-service` + `order-service`，全部注册 Nacos，服务间使用 OpenFeign，外部请求统一经 Spring Cloud Gateway。

## 模块

| 目录 | Nacos 服务名 | 端口 | 数据库 | 说明 |
|---|---|---|---|---|
| `gateway/` | `gateway` | 8080 | — | Spring Cloud Gateway |
| `user-service/` | `user-service` | 8081 | `user_db` | 注册/登录/刷新、用户资料、管理员-用户 |
| `pet-shop-backend/` | `product-service` | 8082 | `commerce_db` | 商品/商家/认证/标签、SPA、uploads |
| `order-service/` | `order-service` | 8083 | `commerce_db` | 下单、订单、评价、商家订单、Saga |
| `pet-shop-common/` | — | — | — | 公共内核（Result/JWT/异常/MyBatis/内部令牌） |
| `JPet_Store/` | — | — | — | Vue 3 + Vite 前端源码 |
| `db/` | — | — | — | `user_db`/`commerce_db` 初始化 SQL |
| `deploy/` | — | — | — | MySQL + Nacos 的 docker-compose |
| `scripts/` | — | — | — | 启停与公网隧道脚本 |
| `docs/microservices/` | — | — | — | 设计、验证记录、实验报告 |

> `pet-shop-backend` 目录名保留是为了不破坏前端构建输出路径（`static/spa`）；它在 Nacos 中的服务名是 `product-service`。

## 快速启动

### 1. 环境

- JDK 21
- Maven 3.8+
- Docker / Docker Compose
- Node.js 22（仅在需要重新构建前端时使用；仓库已包含 `static/spa` 构建产物）

### 2. 构建后端

```bash
mvn -DskipTests install
```

### 3. 启动 MySQL + Nacos

```bash
docker compose -f deploy/docker-compose.yml up -d
```

- MySQL：`127.0.0.1:3308`，`root/rootpass`（本地实验默认值，可用 `MYSQL_ROOT_PASSWORD` 覆盖）
- Nacos：`127.0.0.1:8848`，控制台 `http://localhost:8088`
- 首次启动会执行 `db/init/*.sql` 创建 `user_db` 与 `commerce_db`

### 4. 启动四个服务

```bash
scripts/start-all.sh
```

或手动：

```bash
java -jar pet-shop-backend/target/pet_shop_backend-0.0.1-SNAPSHOT.jar   # product-service 8082
java -jar user-service/target/user-service-0.0.1-SNAPSHOT.jar          # 8081
java -jar order-service/target/order-service-0.0.1-SNAPSHOT.jar        # 8083
java -jar gateway/target/gateway-0.0.1-SNAPSHOT.jar                    # 8080
```

### 5. 访问

- 前端：`http://localhost:8080/spa/`
- Nacos 控制台：`http://localhost:8088`（登录 `nacos/nacos`）
- 默认管理员：`admin / Admin123`（本地实验默认值）

## 配置项

各服务 `application.yml` 使用环境变量 + 本地实验默认值，部署时请覆盖：

| 配置 | 默认值 | 说明 |
|---|---|---|
| `DB_USERNAME` | `root` | MySQL 用户名 |
| `DB_PASSWORD` | `rootpass` | MySQL 密码 |
| `JWT_SECRET` | 占位字符串 | JWT 签名密钥，至少 32 字符 |
| `INTERNAL_TOKEN` | `jpet-internal-2026` | `/internal/**` 的 `X-Internal-Token` |
| `MAIN_ADMIN_KEY` | `main-admin-123456` | 主管理员注册密钥 |
| `SUB_ADMIN_KEY` | `sub-admin-123456` | 子管理员注册密钥 |
| `DEFAULT_ADMIN_PASSWORD` | `Admin123` | 默认管理员密码 |
| `NACOS_AUTH_TOKEN` | 开发用占位 base64 | Nacos 3.x 鉴权 token |
| `NACOS_AUTH_IDENTITY_KEY` / `VALUE` | `jpet-server` / `jpet-server-value` | Nacos 3.x identity |

> 以上均为本地实验默认值，不是生产凭据；生产环境请通过环境变量或配置中心覆盖，并开启 Nacos 鉴权、使用最小权限数据库账号。

## 前端构建（可选）

仓库已包含 `pet-shop-backend/src/main/resources/static/spa` 构建产物，直接启动即可。修改前端后重新构建：

```bash
cd JPet_Store
npm install
npm run build
```

输出目录已配置为 `pet-shop-backend/src/main/resources/static/spa`，由 product-service 提供、网关转发。

## 文档

- 架构、路由、内部契约、DELETE 矩阵：`docs/microservices/README.md`
- 实测验证记录：`docs/microservices/verification.md`
- 实验报告（类图 / API / 截图 / 困难与遗留）：`docs/microservices/experiment-report.md`
- 实施计划：`docs/microservices/2026-11-04-microservices-and-delete-plan.md`

## 说明

- Spring Cloud Gateway 5.x 的路由前缀是 `spring.cloud.gateway.server.webflux.routes`。
- `product-service` 与 `order-service` 共用 `commerce_db`，边界靠“删除跨域 Mapper 引用 + 只走 Feign”约束。
- 所有服务使用同一基础包 `com.edu.csu.web.pet_shop_backend`，通过 Maven 模块隔离；`gateway` 使用 `com.edu.csu.web.gateway`，不依赖 common。
- 如本机 8080-8083 被占用，可用 `--server.port=18080` 等参数覆盖；前端需通过对应网关端口访问。
