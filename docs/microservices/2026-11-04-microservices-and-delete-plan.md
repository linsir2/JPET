# JPet Store 微服务化改造 + 真实 DELETE 能力 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把现有 Spring Boot 单体 `pet-shop-backend` 拆成 `user-service`、`product-service`、`order-service` 三个注册到 Nacos 的服务，外部请求统一经 `gateway`，服务间只用 OpenFeign；并新增一组真实可用的 DELETE 接口，覆盖三个服务。

**Architecture:** 三服务 + 网关 + Nacos；物理上两个 MySQL schema（`user_db`、`commerce_db`），`product-service` 与 `order-service` 共用 `commerce_db`，边界靠“删除对方 Mapper 引用 + 内部 Feign + grep 验收”强制。跨服务写采用最终一致 + 编排式补偿，不引入 Seata。

**Tech Stack:** Java 21、Spring Boot 4.0.4、Spring Cloud 2025.1.0、Spring Cloud Alibaba 2025.1.0.0、Nacos 3.1.2、OpenFeign、Spring Cloud Gateway 5.x（WebFlux）、MyBatis-Plus 3.5.15、MySQL 8、Caffeine、JWT（jjwt 0.12.6）。

**Spec / 设计依据:**
- `pet-shop-backend/docs/PRD.md`
- `docs/项目功能实现与修改证明.md`
- 本仓库现有代码（2026-11-04 快照）
- 已确认决策：3 服务 / 两 schema / Nacos Docker / SPA 留 product / 根 parent + common / 最终一致 + 幂等重试 / 专用 Docker MySQL / 新增真实 DELETE

---

## Global Constraints

- 不改前端源码；`JPet_Store/vite.config.ts` 的 `base=/spa/`、`/api` 代理 8080、构建输出 `../pet-shop-backend/src/main/resources/static/spa` 全部保持。
- 不改任何现有外部 URL、HTTP 方法、请求/响应字段、`Result` 信封、错误码语义。
- 所有外部请求只经 `gateway:8080`；`/internal/**` 不配置网关路由。
- 服务间调用只用 `@FeignClient(name = "...")`，禁止 `url=` / 硬编码 IP。
- `product-service` 与 `order-service` 共用 `commerce_db`；禁止跨域直接访问对方表。
- 端口：gateway 8080、user 8081、product 8082、order 8083、Nacos 8848（控制台 8088）、MySQL 3308。
- 版本：Boot 4.0.4（保持不变）；Spring Cloud 2025.1.0；SCA 2025.1.0.0；Gateway starter 用 `spring-cloud-starter-gateway-server-webflux`。
- `spring-cloud-starter-loadbalancer` 必须显式加入 product/order/gateway（SCA/OpenFeign 中它是 optional）。
- 共享包名保持 `com.edu.csu.web.pet_shop_backend`，避免大量 import 改名；服务边界靠 Maven 模块隔离。
- 不修改 `AppConfig` 的全局 `ObjectMapper` 行为；内部 Feign DTO 不使用 `LocalDateTime`，时间统一 String。
- 不触碰仓库里预先存在的脏文件（`_archive`、`_git_backup`、`_git_backup` 等 269 项）。
- 每个任务先写失败测试，再实现，再跑绿，再提交；提交信息用 `feat/fix/refactor/test/docs` 前缀。

---

## 0. 评审修订 R1（子智能体评审后必须遵守）

> 本节是 R1 增补，优先级高于后文人手写细节；冲突时以本节为准。

### 0.1 依赖与运行时门禁

- **gateway 禁止依赖 `pet-shop-common`**。common 含 `spring-boot-starter-webmvc`/Servlet Filter/Security，gateway 一旦依赖会被 Boot 判定为 SERVLET 应用，Gateway 起不来。gateway 只依赖 gateway/nacos/loadbalancer。Task 0.1 增加 `mvn dependency:tree` 断言：gateway 不得出现 `spring-boot-starter-webmvc`。
- **Boot 4 双 Jackson 已实测**：Boot 4.0.4 MVC 使用 Jackson 3（`tools.jackson.core:jackson-databind:3.1.0`），项目显式使用 Jackson 2（`com.fasterxml.jackson.core:jackson-databind:2.21.1`）承载 `JsonNode`、MyBatis-Plus `JacksonTypeHandler`、`AppConfig`。
  - `pet-shop-common`、`order-service`、`pet-shop-backend` 必须显式声明 Jackson 2 `jackson-databind`（order 的 `Order.skuAttributeValues`、product 的 `ProductSPU.coreAttributes` 依赖它）。
  - 内部 Feign DTO 禁止 `JsonNode`/`LocalDateTime`；时间一律 String。
  - Task 0.3 增加一次真实请求契约测试：断言 MVC 响应命名（`order_no` vs `orderNo`）与错误响应字段，并记录实际策略；不得假设 `AppConfig` 覆盖 Boot 的 Jackson 3。
- product/order 启动类加 `@EnableFeignClients(basePackages = "com.edu.csu.web.pet_shop_backend.client")`。
- 三份 `application.yml` 必须显式包含 `jwt.secret`、`jwt.access-token-expiration-ms`、`jwt.refresh-token-expiration-ms`；user-service 另加 `auth.admin-register.*`、`auth.default-admin.*`；三服务都加 `app.internal.token`。
- Feign 显式配置 `connectTimeout=2000`、`readTimeout=5000`；读接口缺失时降级为默认值，写接口禁止盲目自动重试。

### 0.2 下单 Saga 改为“状态机 + outbox + 原子扣减”

- **取消“reserve/release 两个独立幂等键”**。订单服务新增 `checkout_op_tb`（commerce_db），状态机：`RESERVING → RESERVED → COMMITTED`，失败/超时进入 `COMPENSATING → COMPENSATED / FAILED`。
  - `checkout_op_tb`：`op_id`(PK)、`user_id`、`status`、`items_json`、`reserve_result_json`、`last_error`、`create_time`、`update_time`。
  - 先落 `RESERVING`，调 product reserve；成功落 `RESERVED`；本地写单成功落 `COMMITTED`（`order_tb.checkout_op_id = op_id`）；任一步失败/超时进 `COMPENSATING`。
  - 定时补偿任务扫描超过 N 分钟的 `RESERVING/RESERVED/COMPENSATING`：按 `order_tb.checkout_op_id = op_id` 判断是否已落单；已落单 → `COMMITTED`（不再 release，覆盖“订单已落但 COMMITTED 未更新”的崩溃窗口）；未落单 → 用**同一个 reserve opId** 调 release（product 按 `op_type='reserve'` 找回 `result_json`）→ `COMPENSATED`（覆盖“RESERVED 但无订单”的库存泄漏）。
  - Feign 超时视为“结果未知”，先查 `checkout_op_tb`/product 的 `GET /internal/products/inventory/ops/{opId}?opType=reserve`，再决定 commit/release；不能像旧计划那样“reserve 抛错一律不 release”。
- **库存扣减改为原子条件更新**：`UPDATE product_sku_tb SET stock = stock - ? WHERE id = ? AND stock >= ?`，受影响行数为 0 即 409；禁止“先读再 setStock 再 updateById”的读改写。
- `inventory_op_tb` 升级为状态机：`op_id`、`op_type`(reserve/release)、`status`、`result_json`、`user_id`、`create_time`；reserve 重复请求按状态返回快照或 409；release 重复请求直接返回已释放。
  - **checkout 补偿**：复用 reserve 的 `opId`，查 `op_type='reserve'` 的 `result_json` 恢复明细；写一条 `op_id` 相同、`op_type='release'` 的记录。
  - **取消/退货**：使用独立 `opId = release:{orderId}:{cancel|return}` 且 items 显式传；落 `order_inventory_op_tb`（PENDING/DONE/FAILED），由补偿任务重试。
- 请求/响应增加 `lineId`（或 `itemIndex`）与 `quantity`，避免同一 `spuId` 多行无法对齐。
- `checkout-reserve` 的 `matchSku` 保持现有语义（含“selection 为空回退第一个 SKU”），但**非空 selection 未精确匹配时返回 409**，不再静默回退；这是对现有隐患的定点修复。

### 0.3 跨服务写一致性

- 商家入驻：先完成所有本地校验与插入准备，最后调 user-service PATCH 联系方式；远程成功、本地失败时返回可重试错误，不再“先改用户再校验商家名”。
- 商家资料改邮箱：远程 PATCH 放在本地事务之外（拆 orchestrator/writer），避免长事务持连接。
- 审核通过：user-service 提供**条件 PATCH**（仅 `status='normal'` 才允许改 role，否则 409）；product 侧用 `merchant_role_sync_tb` outbox（见 §5.2）在同事务写入，定时任务调用条件 PATCH 后再 `GET /internal/users/{id}` 复核 role/status，达标才标记 DONE，否则写 `last_error` 重试；`AdminMerchantServiceImpl` 的 `RuntimeException` 全部改为 `BusinessException`（409/404），不要落成 500。
- 商家名/邮箱的 TOCTOU 由数据库唯一键兜底：`merchant_tb.uk_merchant_name`、`user_tb.uk_email` 捕获重复键后转 409；user-service 的 PATCH 必须把 `DuplicateKeyException` 转成 409，不能让 Feign 收到 500。
- **refresh token 重新查库**：`AuthServiceImpl.refreshAccessToken` 不再直接复制 refresh token 里的 role；改为按 `userId` 查 `user_tb`/`administrator_tb`，状态非 normal 直接拒绝，role 用最新值。这样商家审核通过后刷新即可拿到 `merchant_user`，注销后 refresh 立即失效。
- `profile` JSON 的内部 PATCH 必须**按字段 merge**（只改 phone/email/role），不能整体覆盖，避免丢掉 avatar/intro/address/preferences。
- D1 注销时若存在 pending/rejected 商家申请：D1 通过 `GET /internal/products/merchants/by-user/{userId}/application-status` 检查并返回 409“请先撤回”（D4）。**除这一条只读调用外，不引入其他 user→product 反向 Feign**；管理员审核守卫仍对 deleted 用户返回 409。

### 0.4 Schema runner 与 null 语义

- 终态 runner 命名固定为 `UserSchemaUpgradeRunner` / `ProductSchemaUpgradeRunner` / `OrderSchemaUpgradeRunner`；**旧 `SchemaUpgradeRunner` 的删除时机统一为 Task 2.4**：Task 2.2 先把它里面的 order 段迁到 `OrderSchemaUpgradeRunner`，Task 2.4 再把 merchant_authentication 段迁到 `ProductSchemaUpgradeRunner` 并删除旧类。Task 1.1/1.2 不动它。
- `ProductSchemaUpgradeRunner` 必须 `CREATE TABLE IF NOT EXISTS inventory_op_tb` 和 `merchant_role_sync_tb`；`OrderSchemaUpgradeRunner` 必须 `CREATE TABLE IF NOT EXISTS checkout_op_tb`、`order_inventory_op_tb`，并加 `order_tb.checkout_op_id`/`buyer_deleted`；`db/init/02_commerce_db.sql` 同步建全部新表。加“表存在性”断言测试。
- `db/init/02_commerce_db.sql` 必须是**完整终态**（包含现有 `SchemaUpgradeRunner` 补的 order 列），不能只建基础列。
- 所有需要置 `null` 的更新（D1 `profile=null`、D3 评价字段、审核 `audit_reason=null`）必须用 `LambdaUpdateWrapper.set(field, null)`；`updateById` 不会更新 null。

### 0.5 DELETE 语义修订

- **D1**：匿名化用户名/邮箱加随机后缀（`deleted_user_{id}_{uuid8}`），避免撞唯一键；`profile=null` 用 UpdateWrapper；refresh token 失效；pending 申请按 0.3 处理。
- **D2**：可删 `cancelled/completed`；`buyer_deleted=1`；买家列表过滤；商家列表/统计不过滤。
- **D3**：使用 includeDeleted 的加载方式（隐藏订单仍可撤回公开评价）；清空评价；**所有评价/评分读取统一要求 `status='completed'`**，退货/取消订单的评价不再出现在商品详情/评分中。
- **D4**：只允许 pending/rejected；事务内删 tag rel + merchant；若检测到 `merchant_authentication_tb` 或 `product_spu_tb` 关联（数据异常）→ 409，不做孤儿删除。
- **D5**：只允许 pending/rejected；active → 409；证书图片文件清理列为已知限制。
- **D6**：软删 SPU。订单展示使用的 `spus/batch` **必须能查 inactive SPU**；`merchant/by-user/{userId}/catalog` 必须返回该商家**全部** SPU（含 inactive，带 `isActive` 标志），否则删除商品后 pending/shipped 订单会从商家列表消失。
- **D7**：复用现有软删；不存在/已删沿用现有 `ProductUpdateInvalidException`（400），不强行改成 404；若删除会导致 published SPU 无 active SKU，新单删接口前置校验返回 409（批量旧接口保持原行为）。
- **幂等口径**：D1（账号）与 D2/D3（订单/评价，重复操作是字段置位/清空）重复调用返回 200；D4–D7 是“按 id 寻址的资源删除”，资源不存在/已删返回 404/400（沿用现有），文档和测试按此固定。

### 0.6 安全与内部令牌

- `InternalTokenFilter` fail-closed：`app.internal.token` 为空 → 直接 401/503，绝不默认放行；只用 `@Component + @Order(Ordered.HIGHEST_PRECEDENCE + 10)` 注册（不要再用 `FilterRegistrationBean`，避免重复注册）；各 `SecurityConfig` 显式 `permitAll("/internal/**")` 交给该 filter。
- 内部 PATCH 的 role 只允许 `normal_user → merchant_user`，禁止任意 role 写入；加审计日志。
- 服务端口在实验环境绑定 `127.0.0.1`（`server.address=127.0.0.1`）或在部署说明中要求仅本机/私网访问；内部令牌不得进入前端。
- 为 D1/D2/D3 增加显式 `HttpMethod.DELETE` Security 规则（normal_user/merchant_user），避免落到 `anyRequest().permitAll()`；D4 的双角色规则必须在 `/api/merchant/**` 之前。
- 账号注销后的 JWT/refresh 失效：refresh 查库拒绝（0.3）；access token 仍有 ≤2 小时窗口，文档明确。

### 0.7 商家缓存与残留清理

- `MerchantCacheService` 增加失效：仅在 product 内的**审核通过、封禁、D4 撤回**后 `@CacheEvict(cacheNames="merchantIdByUserId", key="#userId")`；D1 在 user-service，不直接 evict（pending/rejected 不会进入 active 缓存），见 §0.8。
- `ProductConfig` 工厂的参数/import 必须与 Task 2.4 同步改完，不能只改 `ProductServiceImpl` 构造器。
- `WebConfig` 必须删掉 `JWTUserContextMethodArgumentResolver` 字段和 `addArgumentResolvers`，否则与 common 重复注册；加 `ApplicationContext` 断言只注册一次。
- `ProductServiceImpl.getBuyerReviewsBySpuId` 只调一次 `user.batch`，同时组装 username/avatar，禁止两次批量查询。

---

### 0.8 R2 修订（第二轮评审后，优先级最高）

1. **Saga 补偿闭环**：`release` 只强制 `opId`；若省略 `items`，product 从 `inventory_op_tb.result_json`（`op_type='reserve'`）恢复扣减明细。新增 `GET /internal/products/inventory/ops/{opId}?opType=reserve` 供 order 在超时/响应丢失时恢复快照与状态。`order_tb.checkout_op_id` 让补偿任务判断“是否已落单”；`checkout_op_tb.reserve_result_json` 缓存快照；`items_json` 保存请求；`last_error` 由补偿任务写入；取消/退货用 `order_inventory_op_tb` 持久化重试。
2. **新服务必须 `@MapperScan`**：`UserServiceApplication`、`OrderServiceApplication` 加 `@MapperScan("com.edu.csu.web.pet_shop_backend.mapper")`；否则 `UserMapper/OrderMapper` 无 Bean。
3. **common 的 Feign 依赖**：common 加 `feign-core`（`provided`），`InternalTokenFeignInterceptor` 不加 `@Component`，在 **product/order/user 三个服务的 `FeignConfig`** 里各自 `@Bean`（user 用于 D1 的 application-status 只读调用）。`InternalTokenFilter` 只用 `@Component + @Order`，不再同时用 `FilterRegistrationBean`，避免重复注册。
4. **OrderServiceImpl 迁移顺序**：Task 2.2 移动 `OrderServiceImpl` 时，同步创建 order 的 `UserClient`/`UserDirectory`/`FeignConfig`；product 保留自己的 `UserClient` 给 merchant/product 用。
5. **ReviewItem 增加 `id`**（orderId），保持外部 `ProductBuyerReviewVo.id` 不变；reviews/ratings/merchant aggregate 全部加 `status='completed'`。
6. **Runner 命名统一**：`UserSchemaUpgradeRunner` / `ProductSchemaUpgradeRunner` / `OrderSchemaUpgradeRunner`；旧 `SchemaUpgradeRunner` 删除。`ProductSchemaUpgradeRunner` 建 `inventory_op_tb`，`OrderSchemaUpgradeRunner` 建 `checkout_op_tb` 并负责 `buyer_deleted`。`db/init/02_commerce_db.sql` 必须是完整终态（含现有 order 列 + 两张 op 表）。
7. **D6 契约补 `isActive/status`**：`SpuBrief` 与 `MerchantCatalog.spus` 都要带 `isActive`；`spus/batch` 能查 inactive；`catalog` 返回该商家全部 SPU（含 inactive）。
8. **D1 完整化**：匿名化用 `deleted_user_{id}_{uuid8}` / `deleted_user_{id}_{uuid8}@deleted.invalid`；`profile=null` 用 UpdateWrapper；重复注销用独立 loader 返回 200，不复用会抛 401 的 `requireUser`；注销前调用 product 的 `GET /internal/products/merchants/by-user/{userId}/application-status`，若 pending/rejected → 409“请先撤回商家申请”（这是唯一新增的 user→product 只读调用，不构成运行时递归）。
9. **D7 最后 SKU**：新单删接口前置校验“published SPU 删除后仍有 active SKU”，否则 409；旧批量接口保持原行为（已删/不存在 → 400）。
10. **D4 orphan 守卫**：事务内若发现 `merchant_authentication_tb` 或 `product_spu_tb` 仍关联 → 409，不做孤儿删除；补测试。
11. **缓存失效范围**：`MerchantCacheService` 只在 product 内的审核通过、封禁、D4 撤回后 evict；D1 在 user-service，不直接 evict（pending/rejected 本来就不会进入 active 缓存）。
12. **配置文件名统一为 `application.yml`**：迁移时删除/改名旧的 `application.yaml`，避免 Boot 同时加载两份；三个 MVC 服务都加 `@EnableFeignClients`（user-service 因 D1 的 application-status 只读调用也需要）与 `jwt.*`/`app.internal.token`。
13. **前端遗留调用**：`/api/cart/**` 目前无后端控制器（`cartBadge` 已 catch 404）；`/api/merchant/spu/**` 无调用方（死代码）；`/api/products?id=` 是旧形态。网关补 `/api/cart/** → product` 以保持“所有请求经网关”且行为仍为 404；其余在文档标注为遗留/死代码，不新增后端。
14. **错误码断言口径**：本项目 `GlobalExceptionHandler` 返回 HTTP 200 + `Result.code`；所有 DELETE 测试断言 `Result.code`（400/401/403/404/409），不要断言 HTTP status。

---

## 1. 现状证据（2026-11-04 实测）

| 事实 | 证据 |
|---|---|
| 单体可编译 | `mvn -q -DskipTests compile` EXIT=0 |
| Boot 4.0.4 | `pet-shop-backend/pom.xml:7-9` |
| 无任何 RPC | grep 无 `RestTemplate/WebClient/@FeignClient` |
| 认证域干净可拆 | `AuthServiceImpl.java:45-58` 只依赖 User/Administrator Mapper |
| 订单域强耦合 | `OrderServiceImpl.java:59-64` 直接注入 Product/Merchant/User Mapper；`:106-110` 扣库存/加销量 |
| 商品域读订单 | `ProductServiceImpl.java:61-69, 215-247`；`BuyerProductServiceImpl.java:48-52` |
| 商家营收跨表 | `MerchantMapper.java:24-91` JOIN `order_tb + product_spu_tb` |
| 商家写用户 | `MerchantApplicationServiceImpl.java:66-83,245-253`；`MerchantProfileServiceImpl.java:136-141`；`AdminMerchantServiceImpl.java:88-96` |
| 解析器只在 WebConfig | `WebConfig.java:39-44` |
| 订单服务层鉴权 | `SecurityConfig.java:88-91` 只显式保护 checkout；`OrderServiceImpl.java:83-85` 服务层兜底 |
| 资产路径 | `utils/assets.ts:49-66`：`/images/*` → `/spa/images/*`；`/uploads/*` 原样 |
| 现有删除 | 仅 `ProductController.java:189,205` 的 SPU/SKU 软删除（`is_active=0`） |
| 无删除标记 | `order_tb` 无软删列；`user_tb.status` ENUM 只有 normal/banned |
| DB 凭据不可用 | `test/Test@123456` 连不上；需专用 Docker MySQL |

---

## 2. 目标架构

```
Browser (base=/spa/, 同源 :8080)
   │
   ▼
gateway :8080   (WebFlux + LoadBalancer + Nacos)
   ├─ /api/auth/**, /api/user/**, /api/admin/users/**      → lb://user-service
   ├─ /api/merchant/orders/**, /api/orders/**              → lb://order-service
   ├─ /api/products/**, /api/merchant/**,                   → lb://product-service
   │  /api/admin/merchants/**, /api/admin/products/**
   └─ /spa, /spa/**, /uploads/**                            → lb://product-service
   ( /internal/** 不路由 )

user-service :8081  user_db       (user_tb, administrator_tb)
product-service :8082 commerce_db (merchant/product/tag/authentication + SPA/uploads)
order-service :8083  commerce_db  (order_tb)
```

> **目录与 service name 的映射（全文统一）**
> - `user-service/` 目录 = Nacos 服务名 `user-service`
> - `pet-shop-backend/` 目录 = Nacos 服务名 `product-service`
> - `order-service/` 目录 = Nacos 服务名 `order-service`
> - `gateway/` 目录 = Nacos 服务名 `gateway`
> 下文凡是“product-service 的源码”均指 `pet-shop-backend/src`。

边界铁律（grep 验收）：
```bash
# product（pet-shop-backend）不得直连订单或用户表
grep -rn "OrderMapper\|order_tb\|UserMapper\|user_tb" pet-shop-backend/src/main/java
# order 不得直连商品/商家/用户表
grep -rn "ProductSPUMapper\|ProductSKUMapper\|MerchantMapper\|UserMapper\|product_\|merchant_" order-service/src/main/java
# user 不得出现 commerce 表
grep -rn "merchant_\|product_\|order_tb" user-service/src/main/java
```

---

## 3. 模块与文件结构

```
/
├── pom.xml                         # 根 parent（packaging=pom）
├── pet-shop-common/                # 共享基础设施（无 web server）
├── user-service/    :8081          # user_db
├── pet-shop-backend/ :8082         # 目录名保留；spring.application.name=product-service
├── order-service/   :8083          # commerce_db
├── gateway/         :8080
├── deploy/docker-compose.yml
├── db/init/01_user_db.sql
├── db/init/02_commerce_db.sql
└── docs/microservices/*
```

### 3.1 `pet-shop-common` 内容（原包名不变）

- `common/utils/Result`、`JwtUtil`、`PasswordUtil`、`AdminJWTContextValidator`
- `common/Constant`、`common/JWTContextHolder`、`common/annotation/InjectJWTUserContext`
- `entity/pojo/JWTUserContext`、`enums/UserRoleEnum`
- `exception/*`（含 `GlobalExceptionHandler`）
- `config/MybatisPlusConfig`、`config/MybatisMetaObjectHandler`、`config/AppConfig`
- `config/method_argument_resolver/*`
- `filter/JWTAuthenticationFilter`
- 新增 `config/JwtArgumentResolverConfig`（把解析器注册进 MVC）
- 新增 `security/SecuritySupport`（统一 401/403 JSON）
- 新增 `security/InternalTokenFilter`（校验 `/internal/**` 的 `X-Internal-Token`）
- 新增 `client/InternalTokenFeignInterceptor`（无注解，由 product/order/user 的 `@Bean` 注册；common 声明 `feign-core` 为 `provided` 以便编译）
- 新增 `entity/vo/UserBriefVo`

### 3.2 迁到 `user-service`

控制器/服务/Mapper/实体：
- `AuthController`、`UserProfileController`、`AdminUserController`
- `AuthService/Impl`、`UserProfileService/Impl`、`AdminUserService/Impl`
- `UserMapper`、`AdministratorMapper`、`User`、`Administrator`
- DTO/VO：`UserRegisterDto`、`AdminRegisterDto`、`AdminLoginDto`、`MerchantLoginDto`、`TokenRefreshDto`、`UserProfileUpdateDto`、`UserStatusUpdateDto`、`LoginVo`、`TokenRefreshVo`、`UserProfileVo`、`UserVo`
- 配置：`AdminRegistrationProperties`、`DefaultAdminProperties`、`DefaultAdminInitializer`
- 新增：`SecurityConfig`、`InternalUserController`、`UserSchemaUpgradeRunner`、`ProductClient`（D1 的 application-status 只读）、`FeignConfig`、`application.yml`

### 3.3 迁到 `order-service`

- `OrderController`、`MerchantOrderController`
- `OrderService`、`OrderServiceImpl`
- `OrderMapper`、`Order`
- `BuyerCheckoutDto`、`BuyerCheckoutItemDto`、`BuyerOrderAddressDto`、`BuyerOrderReviewDto`
- `BuyerCheckoutVo`、`BuyerOrderVo`、`BuyerOrderReviewVo`、`MerchantOrderVo`
- 新增：`SecurityConfig`、`InternalOrderController`、`OrderSchemaUpgradeRunner`、`ProductClient`、`OrderCheckoutOrchestrator`、`OrderWriter`、`application.yml`

### 3.4 `product-service`（原 `pet-shop-backend`）

保留：
- 所有 product/merchant/admin-product/admin-merchant/admin-authentication 控制器与服务
- `ProductConfig`、`ProductUploadProperties`、`WebConfig`（只留 SPA/uploads）、`static/spa`、`uploads/`
- `MerchantCacheService`、`ProductSchemaUpgradeRunner`
- `AdminProductServiceImpl`、`MerchantAuthenticationServiceImpl`

改写：
- `ProductConfig`：`ProductService` 工厂去 `UserMapper/OrderMapper`，注入 `UserClient/OrderClient`
- `ProductServiceImpl`：评价/评分走 `OrderClient` + `UserClient`
- `BuyerProductServiceImpl`：评分走 `OrderClient`
- `MerchantProfileServiceImpl`：用户读写走 `UserClient`，营收/评分走 `OrderClient`
- `MerchantApplicationServiceImpl`：用户读写走 `UserClient`
- `AdminMerchantServiceImpl`：用户读取/角色同步走 `UserClient`，审核前校验用户状态
- `MerchantMapper`：删除 `sumEffectiveOrderRevenue*`、`averageReviewedOrderRating`、`countOrdersByStatus`、`sumProductSales`、`countPublishedProducts`
- `SecurityConfig`：只保留 product/merchant/admin-merchants/admin-products
- `SchemaUpgradeRunner`：Task 2.2 迁走 order 段到 `OrderSchemaUpgradeRunner`，Task 2.4 迁走 merchant_authentication 段到 `ProductSchemaUpgradeRunner`（含 `inventory_op_tb`/`merchant_role_sync_tb`）后删除旧类
- `application.yml`（由旧 `application.yaml` 改名）：8082、commerce_db、`spring.application.name=product-service`
- 新增：`InternalProductController`、`UserClient`、`OrderClient`、`UserDirectory`、`OrderDirectory`、`InventoryOperationService`

---

## 4. 内部 Feign 契约（12 个，均有消费方）

所有 `/internal/**` 要求请求头 `X-Internal-Token: ${app.internal.token}`；网关不路由。

### 4.1 user-service

| 方法/路径 | 请求 | 响应 | 消费方 |
|---|---|---|---|
| `GET /internal/users/{id}` | path id | `Result<UserBriefVo>` | product：商家资料/入驻/审核、评价；order：买家快照 |
| `POST /internal/users/batch` | `[Long]` | `Result<List<UserBriefVo>>` | product：评价人；order：批量买家/商家用户 |
| `PATCH /internal/users/{id}` | `{email?,phone?,role?}` | `Result<Void>` | product：改邮箱、入驻同步联系方式、审核同步角色 |

`UserBriefVo`：`{ Long id; String username; String email; String role; String status; String profile; }`

`PATCH /internal/users/{id}` 语义：`email`/`phone` 按字段 merge；`role` 只允许 `normal_user → merchant_user`，且要求当前 `status='normal'`，否则 409。`merchant_role_sync` 任务在 PATCH 后用 `GET /internal/users/{id}` 复核 role/status，未达标则记 `FAILED` 并重试（避免依赖“受影响行数”产生歧义）。

### 4.2 product-service

| 方法/路径 | 请求 | 响应 | 消费方 |
|---|---|---|---|
| `POST /internal/products/checkout-reserve` | `{ opId, userId, items:[{lineId,spuId,selection,quantity}] }` | `Result<List<CheckoutItemSnapshot>>` | order：下单 |
| `GET /internal/products/inventory/ops/{opId}?opType=reserve` | path opId + query opType（reserve/release，默认 reserve） | `Result<InventoryOpView>` | order：超时/未知结果时恢复快照与状态 |
| `POST /internal/products/inventory/release` | `{ opId, items?, reason }` | `Result<Void>` | order：补偿（items 省略时 product 从 op 表恢复）；取消/退货（items 显式传） |
| `POST /internal/products/spus/batch` | `[spuId]` | `Result<List<SpuBrief>>` | order：订单展示（含 inactive） |
| `GET /internal/products/merchants/by-user/{userId}/catalog` | path userId | `Result<MerchantCatalog>` | order：商家订单/发货归属（含 inactive） |
| `GET /internal/products/merchants/by-user/{userId}/application-status` | path userId | `Result<MerchantApplicationStatusBrief>`（`{merchantId,status}` 或 null） | user：D1 注销前检查 pending/rejected 申请 |

`CheckoutItemSnapshot`：`{lineId, spuId, spuName, skuId, quantity, unitPrice, skuAttributes}`（`skuAttributes` 用 `Map<String,String>`；order-service 用 Jackson 2 `ObjectMapper.valueToTree` 转成 `Order.skuAttributeValues` 的 `JsonNode`）。`lineId` 用于多行同 SPU 对齐；`quantity` 回显请求行。不返回 `imageUrl`：订单展示统一走 `spus/batch` 取图。
`InventoryOpView`：`{opId, opType, status, snapshots:[CheckoutItemSnapshot]}`；超时恢复用。
`SpuBrief`：`{id, name, imageUrl, merchantId, merchantName, merchantUserId, isActive, status}`。`merchantName` 是 product 侧的原始/清洗后名称；order 侧若为空或命中 `_merchant_\d+` 旧格式，则用 `merchantUserId` 批量查 user-service 的 username 兜底，最后兜底“商家”。`isActive` 让 order 能展示已软删 SPU 的历史订单。
`MerchantCatalog`：`{merchantId, spus:[{id,name,imageUrl,isActive,status}]}`；**返回该商家全部 SPU（含 inactive）**，供商家订单列表定位 pending/shipped 订单；无 active 商家时 `data=null`。
`MerchantApplicationStatusBrief`：`{merchantId, status}`；无申请时 `data=null`。

### 4.3 order-service

| 方法/路径 | 请求 | 响应 | 消费方 |
|---|---|---|---|
| `GET /internal/orders/reviews/{spuId}` | path spuId | `Result<List<ReviewItem>>` | product：商品详情评价 |
| `POST /internal/orders/ratings` | `[spuId]` | `Result<Map<Long,BigDecimal>>` | product：列表评分 |
| `POST /internal/orders/merchant-aggregates` | `{spuIds,currentMonthStart,nextMonthStart,previousMonthStart}` | `Result<MerchantAggregate>` | product：商家统计 |

`ReviewItem`：`{id(orderId), userId, rating, content, reviewTime}`（String）；`id` 用于保持外部 `ProductBuyerReviewVo.id` 不变。
`MerchantAggregate`：`{currentRevenue, previousRevenue, totalRevenue, averageRating}`。
**评价/评分口径（R2）**：`reviews`、`ratings`、`merchant-aggregates.averageRating` 的 SQL 都必须加 `status='completed'`；退货/取消订单的评价不再出现在商品详情/评分中。`reviews` 仍要求 `review_time IS NOT NULL`，`ratings` 仍要求 `review_rating IS NOT NULL`。

### 4.4 内部接口实现要点

- 统一返回 `Result<T>`；业务错误走 `GlobalExceptionHandler`（HTTP 200 + `code`）。
- Feign 消费端用 `*Directory` 适配器拆包：`code>=400` 抛 `BusinessException(code, message)`；GET 缺失返回 `null`；批量缺失不抛。
- `InternalTokenFeignInterceptor` 由 product/order/user 的 `FeignConfig` 注册为 `@Bean`。
- `InternalTokenFilter` 在 common，`@Component`，只对 `/internal/**` 生效。
- **opId 幂等范围**：`opId` 由 order-service 在单次 checkout 编排内生成并复用；幂等只覆盖“同一次编排内的重试/补偿”，不覆盖客户端重复提交整个 HTTP 请求。跨 HTTP 重试需要前端带幂等键，本期不改前端，作为已知限制记录。

---

### 4.5 外部 API 归属矩阵（改造前后路径不变）

| 控制器 | 路径前缀 | 归属服务 |
|---|---|---|
| AuthController | `/api/auth/**` | user |
| UserProfileController | `/api/user/profile` | user |
| AdminUserController | `/api/admin/users/**` | user |
| BuyerProductController | `/api/products/**` | product |
| ProductController | `/api/merchant/products/**` | product |
| MerchantApplicationController | `/api/merchant/apply/**` | product |
| MerchantAuthenticationController | `/api/merchant/authentications/**` | product |
| MerchantProfileController | `/api/merchant/profile/**` | product |
| MerchantStatsController | `/api/merchant/stats` | product |
| AdminMerchantController | `/api/admin/merchants/**` | product |
| AdminAuthenticationController | `/api/admin/merchants/authentications/**` | product |
| AdminProductController | `/api/admin/products/**` | product |
| OrderController | `/api/orders/**` | order |
| MerchantOrderController | `/api/merchant/orders/**` | order |
| **新增** `DELETE /api/user/profile` | `/api/user/profile` | user（D1） |
| **新增** `DELETE /api/orders/{id}` | `/api/orders/**` | order（D2） |
| **新增** `DELETE /api/orders/{id}/review` | `/api/orders/**` | order（D3） |
| **新增** `DELETE /api/merchant/apply` | `/api/merchant/apply` | product（D4） |
| **新增** `DELETE /api/merchant/authentications/{id}` | `/api/merchant/authentications/**` | product（D5） |
| **新增** `DELETE /api/merchant/products/{spuId}` | `/api/merchant/products/**` | product（D6） |
| **新增** `DELETE /api/merchant/products/skus/{skuId}` | `/api/merchant/products/**` | product（D7） |

> 保留现有 `DELETE /api/merchant/products/spu`（body 批量）与 `/sku`（body 批量），前端 `merchant.ts:451` 仍在使用。
>
> **前端遗留调用（非本次新增，保持现状）**：
> - `/api/cart/**`（`JPet_Store/src/api/index.ts`、`stores/cartBadge.ts`）：后端无 CartController，当前就是 404，`cartBadge` 已 catch。网关补 `/api/cart/** → lb://product-service`，保证请求仍经网关且行为仍是 404；不新增后端。
> - `/api/merchant/spu/**`（`api/merchant.ts:455-470`）：无任何调用方，死代码；不建路由、不建后端。
> - `/api/products?id=`（`api/index.ts` 的 `productApi.get`）：旧形态；实际使用的是 `/api/products/{id}`，不新增兼容路由。

## 5. 数据模型与 schema 变更

### 5.1 两 schema

- `user_db`：`user_tb`、`administrator_tb`
- `commerce_db`：`merchant_tb`、`merchant_authentication_tb`、`tag_tb`、`merchant_tag_rel_tb`、`product_spu_tb`、`product_sku_tb`、`product_spu_tag_rel_tb`、`order_tb`、**新增** `inventory_op_tb`（product 拥有）、**新增** `merchant_role_sync_tb`（product 拥有）、**新增** `checkout_op_tb`（order 拥有）、**新增** `order_inventory_op_tb`（order 拥有）

### 5.2 变更

```sql
-- user_db
ALTER TABLE user_tb MODIFY status ENUM('normal','banned','deleted') NOT NULL;

-- commerce_db（order-service 拥有）
ALTER TABLE order_tb ADD COLUMN buyer_deleted TINYINT(1) NOT NULL DEFAULT 0
  COMMENT '买家是否从历史订单中删除（0否1是）' AFTER status;
ALTER TABLE order_tb ADD COLUMN checkout_op_id VARCHAR(64) NULL
  COMMENT '下单编排 opId，用于超时补偿判断是否已落单' AFTER buyer_deleted;
ALTER TABLE order_tb ADD INDEX idx_checkout_op_id (checkout_op_id);

CREATE TABLE IF NOT EXISTS inventory_op_tb (
  op_id VARCHAR(64) NOT NULL,
  op_type VARCHAR(20) NOT NULL COMMENT 'reserve/release',
  status VARCHAR(20) NOT NULL COMMENT 'RESERVED/RELEASED/FAILED',
  result_json TEXT NULL COMMENT 'reserve 的返回快照（含 skuId/quantity/lineId），用于幂等重放与按 opId 补偿',
  user_id BIGINT NULL COMMENT '下单用户，便于审计',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (op_id, op_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存操作幂等状态表';

-- commerce_db（product-service 拥有）
CREATE TABLE IF NOT EXISTS merchant_role_sync_tb (
  merchant_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  target_role VARCHAR(30) NOT NULL COMMENT 'merchant_user',
  status VARCHAR(20) NOT NULL COMMENT 'PENDING/DONE/FAILED',
  last_error VARCHAR(500) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商家审核角色同步 outbox';

-- commerce_db（order-service 拥有；buyer_deleted 的 DDL 归 OrderSchemaUpgradeRunner，不归 product）
CREATE TABLE IF NOT EXISTS checkout_op_tb (
  op_id VARCHAR(64) NOT NULL,
  user_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL COMMENT 'RESERVING/RESERVED/COMMITTED/COMPENSATING/COMPENSATED/FAILED',
  items_json TEXT NOT NULL COMMENT '请求 items 快照，用于重放',
  reserve_result_json TEXT NULL COMMENT 'reserve 返回快照，用于超时后本地重建订单',
  last_error VARCHAR(500) NULL COMMENT '补偿任务写入的最后一次错误',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (op_id),
  INDEX idx_status (status),
  INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='下单编排 outbox/状态机';

CREATE TABLE IF NOT EXISTS order_inventory_op_tb (
  op_id VARCHAR(64) NOT NULL COMMENT 'release:{orderId}:{cancel|return}',
  order_id BIGINT NOT NULL,
  action VARCHAR(20) NOT NULL COMMENT 'cancel/return',
  items_json TEXT NOT NULL COMMENT 'skuId/spuId/quantity 快照',
  status VARCHAR(20) NOT NULL COMMENT 'PENDING/DONE/FAILED',
  last_error VARCHAR(500) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (op_id),
  INDEX idx_status (status),
  INDEX idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='取消/退货库存回补 outbox';
```

### 5.3 迁移执行

- 新库用 `db/init/*.sql` 直接建终态。
- 旧库用三个 `SchemaUpgradeRunner`（user/product/order）做 `ADD COLUMN IF MISSING` / `MODIFY` 幂等升级。
- 旧 `db/schema.sql`、`db/script.sql` 移入 `db/legacy/`，不再作为启动依据。
- **开发库凭据**：本机现有 `test/Test@123456` 不可用；本期专用 Docker MySQL 用 `root/rootpass`（lab 用途，不写入生产）。三份 `application.yml` 的 datasource 都指向 `127.0.0.1:3308`，库名分别为 `user_db` / `commerce_db`。
- **只读快照**：`db/init` 里的种子用户固定 `id=1`，商家种子 `user_id=1`，保证跨 schema 逻辑外键一致。

---

## 6. 真实 DELETE 接口矩阵

> 目标：三个服务都有**真实产生业务效果**的 DELETE，且接口完整（鉴权、归属校验、状态守卫、级联/副作用、幂等、错误码、测试）。不造假接口。
> **R1 覆盖：本节与 §0.5 冲突时，以 §0.5 为准（特别是 D3 的 includeDeleted、D6 的 inactive SPU 可见性、D7 的 400 语义、D1 的 refresh 失效）。**

| # | Service | 方法/路径 | 资源 | 删除类型 | 守卫 | 副作用 | 幂等 |
|---|---|---|---|---|---|---|---|
| D1 | user | `DELETE /api/user/profile` | 当前账号 | 软删除（匿名化） | 已登录；`role=normal_user`；未删除 | `status=deleted`；username/email 匿名化；密码随机化；profile=null；登录被拒 | 已删除再调 → 200 |
| D2 | order | `DELETE /api/orders/{id}` | 买家历史订单 | 软删除（`buyer_deleted=1`） | 本人订单；`status in (cancelled,completed)` | 买家列表隐藏；商家/统计不受影响 | 已删除再调 → 200 |
| D3 | order | `DELETE /api/orders/{id}/review` | 订单评价 | 字段清除 | 本人订单；`status=completed` | rating/content/review_time 置 null；商家评分重算 | 无评价 → 200 |
| D4 | product | `DELETE /api/merchant/apply` | 商家入驻申请 | 硬删除 | 本人最新商家；`status in (pending,rejected)` | 删 `merchant_tag_rel_tb` + `merchant_tb` | 不存在 → 404 |
| D5 | product | `DELETE /api/merchant/authentications/{id}` | 认证申请 | 硬删除 | 本人商家；`status in (pending,rejected)` | 删 `merchant_authentication_tb` 行 | 不存在 → 404 |
| D6 | product | `DELETE /api/merchant/products/{spuId}` | SPU | 软删除（沿用 `is_active=0`） | 本人商家；active | 级联 SKU `is_active=0` | 已删 → 404（沿用现有语义） |
| D7 | product | `DELETE /api/merchant/products/skus/{skuId}` | SKU | 软删除（沿用 `is_active=0`） | 本人商家；active；删除后 published SPU 不能无 active SKU（新单删接口前置 409） | 重算 SPU minPrice | 已删 → 400（沿用现有 `ProductUpdateInvalidException`） |

### 6.1 D1 账号注销细节

- 只允许 `role=normal_user`；`merchant_user` 返回 `409`：“商家账号需先完成商家注销（本期未开放）”。
- **注销前置条件**：调用 product 的 `GET /internal/products/merchants/by-user/{userId}/application-status`；若返回 `pending`/`rejected` → `409`“请先撤回商家入驻申请”（D4）。这是 D1 唯一新增的 user→product 只读调用，不构成运行时递归。
- 匿名化（带随机后缀，避免撞唯一键）：
  - `suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8)`
  - `username = "deleted_user_" + id + "_" + suffix`
  - `email = "deleted_user_" + id + "_" + suffix + "@deleted.invalid"`
  - `password = passwordUtil.encode(UUID.randomUUID().toString())`
  - `profile = null`（用 `LambdaUpdateWrapper.set(User::getProfile, null)`）
  - `status = "deleted"`
- **幂等**：`deleteAccount` 使用独立 loader（允许已 `deleted`），重复调用直接返回 200；不要复用 `UserProfileServiceImpl.requireUser`（它会对 deleted 抛 401）。
- 登录：`AuthServiceImpl.ensureUserStatusValid` 对 `deleted` 抛 `AccountStatusInvalidException`，消息“账号已注销”。
- `UserProfileServiceImpl.requireUser` 对 `deleted` 抛 401/404，阻止继续使用资料接口（D1 自己的 loader 除外）。
- 管理员列表：`AdminUserService.getUsersPage` 默认追加 `ne(status, "deleted")`；仅当 `status=deleted` 显式过滤时展示已注销账号。
- 展示脱敏：`UserBriefVo.status=deleted` 时，product/order 的 `UserDirectory` 返回 `"匿名用户"`（订单买家、评价人）。
- 审核守卫：`AdminMerchantServiceImpl.auditMerchant` 通过前调用 `UserClient.get(merchant.userId)`，若 `status != normal` → `409`，防止已注销用户被审核成商家。
- **SecurityConfig 必须显式放行** `DELETE /api/user/profile` 给 `normal_user` 和 `merchant_user`（服务层再对 merchant_user 返回 409，保证错误语义清晰），否则会被 `anyRequest().permitAll()` 之外的角色规则误伤。
- **已知限制**：JWT 无状态，已签发的 access token 在 2 小时内仍可被 product/order 接受；前端注销后应清除本地 token。文档记录，不在本期引入黑名单/每请求查库。

### 6.2 D2/D3 订单删除细节

- `Order` 增加 `private Boolean buyerDeleted;`（映射 `buyer_deleted`）。
- `checkout` 创建订单时显式 `setBuyerDeleted(false)`。
- `listBuyerOrders` 追加 `.eq(Order::getBuyerDeleted, false)`。
- `loadBuyerOwnedOrder` 追加 `buyer_deleted=0`；D2 使用单独的 `loadBuyerOwnedOrderIncludeDeleted`。
- `reviewOrder` 若 `buyerDeleted=true` → `404`。
- 商家订单列表、商家统计、商品评分**不过滤** `buyer_deleted`（历史账目与评价统计保留）。
- D3 用 `LambdaUpdateWrapper.set(field, null)` 置空（`updateById` 不会更新 null）。

### 6.3 D4/D5 商家侧删除细节

- `MerchantApplicationService.withdrawApplication(Long userId)`：
  - 取 `getLatestMerchantByUserId`；null → 404；
  - `pending`/`rejected` 才允许；`active`/`banned` → 409；
  - **orphan 守卫**：若存在 `merchant_authentication_tb` 或 `product_spu_tb` 关联（数据异常）→ 409，不做孤儿删除；
  - `@Transactional`：删 tag rels，再删 merchant。
- `MerchantAuthenticationService.deleteAuthentication(Long id, Long userId)`：
  - `selectMerchantIdByUserId`（要求 active）→ merchantId；null → 403/400；
  - 查 auth；不存在或 `merchantId` 不匹配 → 404；
  - `pending`/`rejected` 才允许；`active` → 409；
  - `deleteById`。
- 这两个删除不触碰 `user_tb.role`（申请未通过时本就非 merchant_user）。
- **SecurityConfig 必须显式放行** `DELETE /api/merchant/apply` 给 `normal_user` 和 `merchant_user`：现有 `.requestMatchers("/api/merchant/**").hasAuthority(merchant_user)` 会把普通用户的撤回请求挡成 403，必须在该规则之前加一条 `HttpMethod.DELETE, "/api/merchant/apply"` 的双角色规则。

### 6.4 D6/D7 商品路径删除

- `ProductController` 新增：
  - `@DeleteMapping("/{spuId}")` → `productService.deleteProductSpus(List.of(spuId), userId)`
  - `@DeleteMapping("/skus/{skuId}")` → 新单删逻辑：先做归属/存在校验；若删除后该 SPU 为 `published` 且将无 active SKU → 409；否则复用 `deleteProductSkus(List.of(skuId), userId)`
- 保留现有 `DELETE /spu`、`DELETE /sku`（前端 `merchant.ts:451` 在用），不破坏兼容；旧批量接口保持原行为（不存在/已删 → `ProductUpdateInvalidException` 400，不新增最后 SKU 守卫）。
- D6 复用现有归属校验、软删除、minPrice 重算；D7 只在**新单删接口**加最后 SKU 守卫，不改旧批量行为。
- **订单可见性**：`spus/batch` 不加 `is_active=1` 过滤（历史订单要能取到已删 SPU 的名称/图片）；`merchant/by-user/{userId}/catalog` 返回全部 SPU（含 inactive，带 `isActive`）。

---

## 7. 任务分解（TDD）

### Phase 0：骨架与基础设施

#### Task 0.1：根 parent + 四个模块骨架

**Files:**
- Create: `/pom.xml`
- Create: `pet-shop-common/pom.xml`
- Create: `user-service/pom.xml`
- Create: `order-service/pom.xml`
- Create: `gateway/pom.xml`
- Modify: `pet-shop-backend/pom.xml`

**Interfaces:**
- Produces: 根 `mvn -q -DskipTests compile` 可构建的空模块。

- [ ] Step 1: 写根 `pom.xml`（parent Boot 4.0.4；modules；dependencyManagement 导入 `spring-cloud-dependencies:2025.1.0`、`spring-cloud-alibaba-dependencies:2025.1.0.0`）。
- [ ] Step 2: 各模块 pom。`pet-shop-common` 不启用 boot repackage；MVC 服务用 `spring-boot-starter-webmvc`；product/order 额外加 `spring-cloud-starter-openfeign` + `spring-cloud-starter-loadbalancer` + `spring-cloud-starter-alibaba-nacos-discovery`；user 加 `spring-cloud-starter-alibaba-nacos-discovery` + `spring-cloud-starter-openfeign` + `spring-cloud-starter-loadbalancer`（D1 的 application-status 只读调用）；gateway 用 `spring-cloud-starter-gateway-server-webflux` + `spring-cloud-starter-loadbalancer` + `spring-cloud-starter-alibaba-nacos-discovery`。
- [ ] Step 2b: `pet-shop-common`/order/product 显式加 Jackson 2 `com.fasterxml.jackson.core:jackson-databind`；common 加 `feign-core`（`provided`，供 `InternalTokenFeignInterceptor` 编译）。
- [ ] Step 3: 四个 `@SpringBootApplication` 空类；user/order 启动类加 `@MapperScan("com.edu.csu.web.pet_shop_backend.mapper")`；user/product/order 加 `@EnableFeignClients(basePackages = "com.edu.csu.web.pet_shop_backend.client")`。
- [ ] Step 4: 统一配置文件名为 `application.yml`；删除/改名旧的 `pet-shop-backend/src/main/resources/application.yaml`，避免双份配置。
- [ ] Step 5: 运行 `mvn -q -DskipTests compile`，期望 EXIT=0；`mvn -pl gateway dependency:tree | grep spring-boot-starter-webmvc` 必须为空。
- [ ] Step 6: `git add` + commit `chore: add multi-module skeleton`。

#### Task 0.2：Docker Nacos + MySQL + 初始化 SQL

**Files:**
- Create: `deploy/docker-compose.yml`
- Create: `db/init/01_user_db.sql`
- Create: `db/init/02_commerce_db.sql`
- Move: `db/schema.sql`、`db/script.sql` → `db/legacy/`

- [ ] Step 1: 写 compose：`mysql:8.0`（3308，挂载 init，`MYSQL_ROOT_PASSWORD=rootpass`）、`nacos/nacos-server:v3.1.2`（8848/9848/8088，`MODE=standalone`、`NACOS_AUTH_ENABLE=false`、`NACOS_CONSOLE_PORT=8088`）。
- [ ] Step 2: 拆 SQL；`user_db` 含 `user_tb(status 含 deleted)`、`administrator_tb` + 种子用户 `id=1`；`commerce_db` 含 merchant/product/order（**完整终态：包含现有 `SchemaUpgradeRunner` 补的 receiver_*/tracking/评价等全部列 + `buyer_deleted` + `checkout_op_id` 及索引**）+ `inventory_op_tb` + `merchant_role_sync_tb` + `checkout_op_tb` + `order_inventory_op_tb` + 种子商家 `user_id=1`。
- [ ] Step 3: `docker compose -f deploy/docker-compose.yml up -d`。
- [ ] Step 4: 验证 `mysql -h127.0.0.1 -P3308 -uroot -prootpass -e "show databases"` 出现两库；`curl -s http://127.0.0.1:8848/nacos/v1/console/health/readiness` 返回 UP（若 3.x 路径不同，记录实际路径）。
- [ ] Step 5: commit `chore: add docker infra and split db init`。

#### Task 0.3：四应用注册 Nacos + 网关 stub（版本验证点）

**Files:**
- Create/Modify: 各 `application.yml`
- Modify: `gateway` 路由
- Test: `gateway/src/test/java/.../GatewayRouteSmokeTest.java`（可选）

- [ ] Step 1: 配置四个 `spring.application.name`、端口、`spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848`。
- [ ] Step 2: product/order/user 配 datasource；gateway 配一条 `/api/products/** → lb://product-service`。
- [ ] Step 3: 启动四个应用，`curl http://127.0.0.1:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=100` 应含四个服务名。
- [ ] Step 4: `curl http://localhost:8080/api/products/categories` 应返回 product 的响应（或 401/404 但证明路由到达）。
- [ ] Step 5: 记录版本组合与 Nacos 控制台端口；失败则调整 BOM（回退 SC 2025.1.x 补丁）。
- [ ] Step 6: commit `chore: verify nacos registration and gateway routing`。

---

### Phase 1：抽取 user-service（保底 2 服务可运行）

#### Task 1.1：共享基础设施迁入 common

**Files:**
- Move（保持包名）: 见 §3.1 的 common 列表。
- Create: `pet-shop-common/.../config/JwtArgumentResolverConfig.java`
- Create: `pet-shop-common/.../security/SecuritySupport.java`
- Create: `pet-shop-common/.../client/InternalTokenFeignInterceptor.java`
- Create: `pet-shop-common/.../security/InternalTokenFilter.java`
- Modify: `pet-shop-backend/pom.xml` 依赖 common；删除重复类。

**Interfaces:**
- Produces: common 提供 `Result`、`JwtUtil`、`JWTAuthenticationFilter`、`JWTUserContext`、`UserRoleEnum`、异常、`GlobalExceptionHandler`、MVC 参数解析器注册、内部令牌过滤器。

- [ ] Step 1: `git mv` 列表中的文件到 `pet-shop-common` 对应目录（包名不变）。
- [ ] Step 2: 写 `JwtArgumentResolverConfig`，把 `JWTUserContextMethodArgumentResolver` 加入 MVC；product 的 `WebConfig` 删除该注册。
- [ ] Step 3: 写 `InternalTokenFilter` 与 `InternalTokenFeignInterceptor`；common pom 加 `spring-boot-starter-webmvc`、`spring-boot-starter-security`、`spring-boot-starter-validation`、`mybatis-plus`、`jjwt`、`jackson`、`lombok`。
- [ ] Step 4: `mvn -q -DskipTests compile`，期望 EXIT=0。
- [ ] Step 5: 写 `JwtArgumentResolverConfigTest`（MockMvc 或 ApplicationContext 断言 bean 存在），先红后绿。
- [ ] Step 6: commit `refactor: extract shared kernel into pet-shop-common`。

#### Task 1.2：认证/用户/管理员用户迁入 user-service

**Files:**
- Move: §3.2 列表。
- Create: `user-service/.../config/SecurityConfig.java`
- Create: `user-service/.../controller/InternalUserController.java`
- Create: `user-service/.../config/UserSchemaUpgradeRunner.java`
- Create: `user-service/.../client/ProductClient.java`（D1 的 application-status 只读调用，Task 3.1 用）
- Create: `user-service/.../client/FeignConfig.java`（注册 `InternalTokenFeignInterceptor`）
- Create: `user-service/src/main/resources/application.yml`
- Modify: `pet-shop-backend`（product-service）删除对应类与 SecurityConfig 规则。

**Interfaces:**
- Produces: `GET/POST/PATCH /internal/users/**`；`/api/auth/**`、`/api/user/**`、`/api/admin/users/**` 由 user-service 提供。

- [ ] Step 1: 先写 `UserInternalApiTest`（`@WebMvcTest` + mock service）：GET single、POST batch、PATCH email/role、缺内部令牌 401。运行失败。
- [ ] Step 2: 实现 `InternalUserController` + `UserDirectoryService`（email 唯一性、profile phone **按字段 merge**、role 更新）。
- [ ] Step 3: 实现 `SecurityConfig`（auth 放行；user 用户角色；admin/users 管理员）与 `UserSchemaUpgradeRunner`（status enum 含 deleted）。
- [ ] Step 4: 运行测试绿；加 `UserServiceApplicationTests.contextLoads`；启动 user-service，`curl` 登录成功。
- [ ] Step 5: 更新 gateway：`/api/auth/**`、`/api/user/**`、`/api/admin/users/**` → user-service。
- [ ] Step 6: commit `feat: extract user-service with internal user api`。

#### Task 1.3：product-service 通过 Feign 使用 user-service

**Files:**
- Create: `pet-shop-backend/.../client/UserClient.java`
- Create: `pet-shop-backend/.../client/UserDirectory.java`
- Create: `pet-shop-backend/.../client/FeignConfig.java`
- Modify: `MerchantProfileServiceImpl`、`MerchantApplicationServiceImpl`、`AdminMerchantServiceImpl`、`ProductServiceImpl`、`OrderServiceImpl`、`ProductConfig`
- Modify: `pet-shop-backend/pom.xml`（openfeign + loadbalancer）

**Interfaces:**
- Consumes: user-service 的 3 个内部接口。
- Produces: product/order 不再 import `UserMapper`。

- [ ] Step 1: 写 `UserDirectoryTest`（Mockito）：`get` 成功/缺失、`batch` 去重、`patch` 409 透传。
- [ ] Step 2: 实现 `UserClient`、`UserDirectory`、`FeignConfig`（内部令牌；`InternalTokenFeignInterceptor` 从 common 引入，common 只声明 `feign-core` provided，不标 `@Component`）。`pet-shop-backend` 启动类加 `@EnableFeignClients`。
- [ ] Step 3: 替换 5 个服务里的 `UserMapper` 调用；删除 `UserMapper` import 与字段。特别是 `MerchantApplicationServiceImpl.assertEmailUnique`、`MerchantProfileServiceImpl.assertEmailUnique` 这类只查用户表的私有方法要删除，邮箱冲突改由 `PATCH /internal/users/{id}` 的 409 透传。
- [ ] Step 4: 删除 product 的 `UserMapper`/`AdministratorMapper`（已迁走），`grep -r UserMapper pet-shop-backend/src` 为空。
- [ ] Step 5: 运行 `mvn -q -DskipTests compile`；运行 `UserDirectoryTest` 绿。
- [ ] Step 6: **检查点**：启动 user + product + gateway，登录、看商品、下单、商家资料全部经 8080 正常；这就是保底 2 服务版本。
- [ ] Step 7: commit `feat: call user-service via openfeign`。

---

#### Task 1.4：R1 认证与配置硬化（user-service）

**Files:**
- Modify: `user-service/.../service/impl/AuthServiceImpl.java`
- Modify: `user-service/src/main/resources/application.yml`
- Test: `RefreshTokenRoleTest`、`AuthConfigTest`

- [ ] Step 1: 写失败测试：商家审核通过后，用旧 refresh token 刷新，返回的 access token role 必须是数据库里的最新 `merchant_user`；用户 `status=deleted`/`banned` 时 refresh 必须 401/403。
- [ ] Step 2: `refreshAccessToken` 改为按 `userId` 查 `user_tb`/`administrator_tb`，用最新 role/status 生成 access token；不再复制 refresh claim 的 role。
- [ ] Step 3: `application.yml` 补齐 `jwt.secret`、两个过期时间、`auth.admin-register.*`、`auth.default-admin.*`、`app.internal.token`。
- [ ] Step 4: 测试绿；commit `fix: refresh token re-reads current role and status`。

---

### Phase 2：抽取 order-service（完成 3 服务）

#### Task 2.1：product-service 内部商品/库存接口

**Files:**
- Create: `pet-shop-backend/.../controller/InternalProductController.java`
- Create: `pet-shop-backend/.../service/InventoryOperationService.java`
- Create: `pet-shop-backend/.../entity/model/InventoryOperation.java` + Mapper
- Test: `InternalProductControllerTest`、`InventoryOperationServiceTest`

**Interfaces:**
- Produces: §4.2 的 4 个接口；`inventory_op_tb` 幂等。

- [ ] Step 1: 写失败测试：reserve 库存足 → 扣减 + 返回快照；不足 → 409 且无扣减；重复 opId → 返回相同快照；release 重复 opId 不重复加库存。
- [ ] Step 2: 实现 `checkout-reserve`（事务内校验 SPU/SKU、扣库存、加销量、写 op 表）。**必须把 `OrderServiceImpl` 现有 `matchSku` 的匹配规则和“selection 为空则回退 SKU 属性、否则用 selection”的 `toSelectionNode` 语义原样搬到 product-service**，返回 effective `skuAttributes`，否则订单里的规格快照会和改造前不一致。
- [ ] Step 3: 实现 `inventory-release`（事务内回补、减销量、写 op 表）。
- [ ] Step 4: 实现 `spus/batch` 与 `merchants/by-user/{userId}/catalog`。
- [ ] Step 5: 测试绿；commit `feat: add internal product and inventory apis`。

#### Task 2.2：订单代码迁入 order-service

**Files:**
- Move: §3.3 列表。
- Create: `order-service/.../config/SecurityConfig.java`
- Create: `order-service/.../config/OrderSchemaUpgradeRunner.java`（建 `checkout_op_tb` + order 列 + `buyer_deleted`）
- Create: `order-service/.../client/UserClient.java`、`UserDirectory.java`、`FeignConfig.java`
- Create: `order-service/src/main/resources/application.yml`
- Modify: product `ProductConfig`、`BuyerProductServiceImpl` 去掉 OrderMapper。

- [ ] Step 1: `git mv` 订单文件；order pom 加 webmvc/security/mybatis/mysql/openfeign/loadbalancer/nacos/validation/jjwt(common)/jackson2。
- [ ] Step 2: `OrderSchemaUpgradeRunner` 覆盖现有 order 列 + `buyer_deleted` + `checkout_op_id` 及索引 + `CREATE TABLE IF NOT EXISTS checkout_op_tb` + `order_inventory_op_tb`；`Order` 实体加 `buyerDeleted`/`checkoutOpId` 字段；删除旧 `SchemaUpgradeRunner` 的 order 段。
- [ ] Step 3: 同步创建 order 的 `UserClient`/`UserDirectory`/`FeignConfig`（product 保留自己的副本给 merchant/product 用）；order 启动类加 `@MapperScan`/`@EnableFeignClients`。
- [ ] Step 4: product 删除 `OrderMapper`、`Order` 等；`ProductServiceImpl`/`BuyerProductServiceImpl` 改用 `OrderClient`。
- [ ] Step 5: `mvn -q -DskipTests compile`；加 `OrderServiceApplicationTests.contextLoads`。
- [ ] Step 6: commit `refactor: extract order-service module`。

#### Task 2.3：order-service 改写为 Feign + Saga

**Files:**
- Create: `order-service/.../client/ProductClient.java`
- Create: `order-service/.../client/UserClient.java`
- Create: `order-service/.../client/ProductDirectory.java`
- Create: `order-service/.../service/OrderCheckoutOrchestrator.java`
- Create: `order-service/.../service/OrderWriter.java`
- Modify: `OrderServiceImpl`

**Interfaces:**
- `OrderCheckoutOrchestrator.checkout(BuyerCheckoutDto, Long userId)`：非事务状态机；`RESERVING → RESERVED → COMMITTED`；失败/未知 → `COMPENSATING`；按 `opId` 幂等。
- `OrderWriter.insertOrders(List<Order>)`：`@Transactional`。

- [ ] Step 1: 写 `OrderCheckoutOrchestratorTest`，必须区分三类：
  - 业务拒绝（product 返回 409，确定未扣库存）→ 不 insert、不 release，`FAILED`；
  - 超时/连接中断（结果未知）→ `GET /internal/products/inventory/ops/{opId}?opType=reserve` 查状态：RESERVED 且有本地订单 → `COMMITTED`；RESERVED 且无本地订单 → release → `COMPENSATED`；查不到 → `FAILED`；
  - reserve 成功 + insert 抛错 → release（按 opId）→ `COMPENSATED`。
  禁止再写“reserve 抛任何错都不 release”的旧断言。
- [ ] Step 2: 实现 orchestrator + writer + directories；order 启动类确认 `@EnableFeignClients`；`application.yml` 配 `jwt.*`、`app.internal.token`、Feign 超时。
- [ ] Step 3: 改写 `listBuyerOrders`（product.spus/batch + user.batch）、`listMerchantOrders`（product.catalog）、`markOrderShipped`（catalog 归属）、`restoreOrderInventory`（product.release）。**买家快照必须一次性 batch**（买家 userId + 商家 userId 合并去重），禁止在 `toBuyerOrderVo`/`toMerchantOrderVo` 内逐单调用 user-service 造成 N+1。
- [ ] Step 4: 测试绿；`grep` 校验 order 无 product/merchant/user mapper。
- [ ] Step 5: commit `feat: order-service uses feign and checkout saga`。

#### Task 2.4：product-service 通过 Feign 使用 order-service

**Files:**
- Create: `pet-shop-backend/.../client/OrderClient.java`、`OrderDirectory.java`
- Create: `pet-shop-backend/.../config/ProductSchemaUpgradeRunner.java`（merchant_authentication 列 + `inventory_op_tb` + `merchant_role_sync_tb`）
- Modify: `ProductServiceImpl`、`BuyerProductServiceImpl`、`MerchantProfileServiceImpl`、`MerchantMapper`、`ProductConfig`
- Delete: 旧 `SchemaUpgradeRunner`（order 段已在 Task 2.2 迁走）

- [ ] Step 1: 创建 `ProductSchemaUpgradeRunner`，删除旧 `SchemaUpgradeRunner`；写“表存在性”测试（`inventory_op_tb`、`merchant_role_sync_tb`）。写 `OrderDirectoryTest`：reviews/ratings/aggregates 拆包与缺失兜底。
- [ ] Step 2: 实现 `OrderClient`/`OrderDirectory`。
- [ ] Step 3: `ProductServiceImpl.getBuyerReviewsBySpuId` → `order.reviews` + `user.batch`；`BuyerProductServiceImpl.loadRatingMap` → `order.ratings`；`MerchantProfileServiceImpl` 营收/评分 → `order.merchant-aggregates`。
- [ ] Step 4: 删除 `MerchantMapper` 的跨表/死方法。
- [ ] Step 5: 测试绿；`grep -rn "OrderMapper\|order_tb" pet-shop-backend/src/main/java` 为空。
- [ ] Step 6: commit `feat: product-service uses order-service via feign`。

#### Task 2.5：网关最终路由

**Files:**
- Modify: `gateway/src/main/resources/application.yml`
- Test: `GatewayRouteTest`（WebTestClient + mock discovery 或集成）

- [ ] Step 1: 写路由表测试：`/api/merchant/orders/**`→order；`/api/merchant/profile`→product；`/api/orders/**`→order；`/api/auth/**`→user；`/internal/**`→404。
- [ ] Step 2: 配置路由（具体前缀在前）。
- [ ] Step 3: 测试绿；curl 逐条验证。
- [ ] Step 4: commit `feat: finalize gateway routes`。

---

#### Task 2.6：R1 Saga 硬化（outbox + 原子库存 + 取消/退货编排）

**Files:**
- Create: `order-service/.../service/CheckoutOpService.java`
- Create: `order-service/.../service/CheckoutCompensationJob.java`
- Create: `order-service/.../entity/model/CheckoutOp.java` + Mapper
- Modify: `order-service/.../service/OrderCheckoutOrchestrator.java`
- Modify: `order-service/.../service/OrderServiceImpl.java`（cancel/return 改编排）
- Modify: `pet-shop-backend/.../service/InventoryOperationService.java`
- Test: `CheckoutOpStateMachineTest`、`AtomicStockDeductTest`、`CancelReturnOrchestratorTest`

- [ ] Step 1: 写失败测试：reserve 超时（模拟 Feign 超时）→ 查 op 状态再决定补偿；reserve 成功 + 本地写单失败 → 状态 `COMPENSATING` → release → `COMPENSATED`；重复 reserve 同 opId 不重复扣；并发两单扣同一 SKU 只成功一单。
- [ ] Step 2: 实现 `checkout_op_tb` 状态机与 `CheckoutOpService`；`OrderWriter` 写 `order_tb.checkout_op_id = op_id`；reserve 成功后把快照写入 `reserve_result_json`；加“insert 成功但 COMMITTED 更新失败”的测试，补偿任务用 `checkout_op_id` 判定已落单。
- [ ] Step 3: product 增加 `GET /internal/products/inventory/ops/{opId}?opType=reserve`；库存扣减改为 `UPDATE ... WHERE stock >= ?` 原子条件更新；`inventory_op_tb` 记录状态；`release` 支持只传 `opId`（从 `op_type='reserve'` 的 `result_json` 恢复明细）。
- [ ] Step 4: 取消/退货改为非事务编排：稳定 opId `release:{orderId}:{cancel|return}` → 写 `order_inventory_op_tb`（PENDING）→ release（显式 items）→ 本地状态 writer → DONE；失败写 `last_error` 保持 PENDING/FAILED。
- [ ] Step 5: 定时任务扫描 `checkout_op_tb` 的 `RESERVING/RESERVED/COMPENSATING` 和 `order_inventory_op_tb` 的 `PENDING/FAILED`；前者按 `checkout_op_id` 判定 commit/release，后者按 items 重试 release；日志告警。
- [ ] Step 6: 测试绿；commit `fix: harden checkout saga with outbox and atomic stock`。

---

### Phase 3：真实 DELETE 接口

#### Task 3.1：D1 账号注销（user-service）

**Files:**
- Create: `user-service/.../service/AccountDeletionService.java`
- Create/Modify: `user-service/.../client/ProductClient.java`（application-status 只读）
- Modify: `UserProfileController`（增加 `DELETE /api/user/profile`）
- Modify: `AdminUserServiceImpl`（列表默认排除 deleted）、`UserProfileServiceImpl`（状态检查）、`AuthServiceImpl`（deleted 登录消息）
- Modify: `AdminMerchantServiceImpl`（审核前 `UserClient.get` 校验 normal；`RuntimeException` → `BusinessException`）
- Modify: `OrderServiceImpl`/`ProductServiceImpl` 的 `UserDirectory`：status=deleted → “匿名用户”
- Test: `AccountDeletionServiceTest`、`AccountDeletionApiTest`

- [ ] Step 1: 写失败测试：normal_user 注销 → 200，`status=deleted`，username/email 带随机后缀，`profile=null`，登录返回“账号已注销”；merchant_user → 409；有 pending/rejected 商家申请 → 409；重复注销 → 200；admin 审核已注销用户 → 409；订单/评价展示“匿名用户”。
- [ ] Step 2: 实现 `deleteAccount`（单事务；独立 loader 允许已删除；`profile=null` 用 UpdateWrapper）与 controller；注销前调 product 的 application-status。
- [ ] Step 3: 修改登录/资料/管理员列表/审核守卫/展示脱敏。
- [ ] Step 4: 测试绿；commit `feat: add account deregistration (DELETE)`。

#### Task 3.2：D2/D3 订单删除与评价撤回（order-service）

**Files:**
- Modify: `Order`（`buyerDeleted`）
- Modify: `OrderService`/`OrderServiceImpl`（`deleteOrder`、`deleteReview`、查询过滤）
- Modify: `OrderController`（`DELETE /api/orders/{id}`、`DELETE /api/orders/{id}/review`）
- Modify: `OrderSchemaUpgradeRunner`（`buyer_deleted`）
- Test: `OrderDeletionTest`

- [ ] Step 1: 写失败测试：cancelled/completed 可删→`buyer_deleted=1` 且买家列表消失；pending/shipped→409；重复删→200；评价撤回→三个字段 null 且商家评分变化；无评价→200。
- [ ] Step 2: 实现实体字段、`deleteOrder`、`deleteReview`、查询过滤。
- [ ] Step 3: 用 `LambdaUpdateWrapper.set(..., null)` 清评价。
- [ ] Step 4: 测试绿；commit `feat: add order and review delete apis`。

#### Task 3.3：D4/D5/D6/D7 商家与商品删除（product-service）

**Files:**
- Modify: `MerchantApplicationService/Impl`（`withdrawApplication`）
- Modify: `MerchantApplicationController`（`DELETE /api/merchant/apply`）
- Modify: `MerchantAuthenticationService/Impl`（`deleteAuthentication`）
- Modify: `MerchantAuthenticationController`（`DELETE /{id}`）
- Modify: `ProductController`（`DELETE /{spuId}`、`DELETE /skus/{skuId}`）
- Test: `MerchantWithdrawalTest`、`ProductDeleteApiTest`

- [ ] Step 1: 写失败测试：pending/rejected 申请可撤→merchant+tag rel 删除；active→409；他人资源→404；存在认证/商品关联→409；pending/rejected 认证可删、active→409；SPU/SKU 单删复用软删除与归属校验；D7 删除最后一个 active SKU（published SPU）→409；D6 删除后 `catalog` 仍返回该 inactive SPU 且带 `isActive=false`。
- [ ] Step 2: 实现服务与控制器；D4 orphan 守卫；D7 新单删守卫；D6 `SpuBrief`/`MerchantCatalog` 补 `isActive/status`。
- [ ] Step 3: 测试绿；commit `feat: add merchant and product delete apis`。

---

#### Task 3.4：R1 DELETE 与安全硬化

**Files:**
- Modify: `user-service/.../AccountDeletionService`（匿名化后缀、UpdateWrapper、pending 申请说明）
- Modify: `order-service/.../OrderServiceImpl`（D3 includeDeleted、评价读取统一 `status='completed'`）
- Modify: `pet-shop-backend/.../InternalProductController`（D6 catalog 含 inactive、D7 最后 SKU 守卫）
- Modify: `pet-shop-backend/.../MerchantApplicationServiceImpl`（D4 orphan 守卫）
- Modify: `pet-shop-common/.../InternalTokenFilter`（fail-closed、`@Component + @Order`，不要 FilterRegistrationBean 重复注册）
- Modify: 三份 `SecurityConfig`（D1/D2/D3 显式 DELETE 规则；D4 双角色前置；`/internal/**` permitAll 交给 filter）
- Modify: `MerchantCacheService`（审核通过/封禁/D4 撤回后 `@CacheEvict`）
- Modify: `pet-shop-backend/.../ProductConfig`、`WebConfig`（工厂参数同步；删除解析器注册）
- Create: `pet-shop-backend/.../service/MerchantRoleSyncJob.java`（扫描 `merchant_role_sync_tb`，条件 PATCH → GET 复核 role/status，达标置 DONE，否则写 `last_error` 重试）
- Test: `DeleteSecurityMatrixTest`、`InactiveSpuOrderVisibilityTest`、`ReviewConsistencyTest`、`InternalRoleTransitionTest`、`MerchantCacheEvictTest`、`MerchantRoleSyncJobTest`

- [ ] Step 1: 写失败测试（断言 `Result.code`，不是 HTTP status）：D1 匿名化不撞唯一键、profile 被置 null；D3 隐藏订单仍可撤回评价；退货/取消订单的评价不出现在商品详情/评分；D6 删除后 pending 订单仍在商家列表；D7 删除最后一个 SKU → 409；内部令牌缺失 → 401；内部 PATCH role 只允许 normal→merchant；普通用户 DELETE /api/merchant/apply 不被 403；审核通过/封禁/D4 后缓存失效。
- [ ] Step 2: 按 §0.5/§0.6 实现。
- [ ] Step 3: `grep` 边界验收 + 全量测试绿。
- [ ] Step 4: commit `fix: harden delete semantics and internal security`。

---

### Phase 4：端到端与交付物

#### Task 4.1：E2E 验证脚本与证据

**Files:**
- Create: `scripts/verify-microservices.sh`
- Create: `docs/microservices/verification.md`

- [ ] Step 1: 脚本依次：检查 Nacos 四服务 → 登录拿 token → 商品列表 → 下单 → 订单列表 → 取消 → 订单删除 → 评价撤回 → 账号注销 → 商家申请撤回。
- [ ] Step 2: 运行并保存输出到 `docs/microservices/verification.md`。
- [ ] Step 3: 记录 Feign 调用日志、网关路由日志、Nacos 实例列表。
- [ ] Step 4: commit `docs: add e2e verification evidence`。

#### Task 4.2：文档收口

**Files:**
- Create: `docs/microservices/README.md`（架构、端口、启动）
- Create: `docs/microservices/api-matrix.md`（外部 + 内部 + DELETE）
- Create: `docs/microservices/feign-contracts.md`
- Update: `docs/项目功能实现与修改证明.md`（新增微服务章节）

- [ ] Step 1: 写文档。
- [ ] Step 2: 核对每个外部 URL 与改造前一致。
- [ ] Step 3: commit `docs: add microservices documentation`。

---

## 8. 测试矩阵（RIPR）

| ID | R1 需求 | I 实现 | P 证明 | R2 核验 |
|---|---|---|---|---|
| U-1 | 注册唯一/密码加密 | AuthServiceImpl | 重复用户名 409；重复邮箱 409 | user_tb |
| U-2 | 登录支持用户名/邮箱、封禁/注销拒绝 | loginMerchant | 三种路径 | JWT claims |
| U-3 | 内部用户接口 | InternalUserController | GET/POST/PATCH/无令牌 401 | 3 个接口有消费方 |
| F-1 | product→user 无 IP | UserClient | Mockito 断言 `name=user-service` | grep 无 UserMapper |
| F-2 | order→product/user | ProductClient/UserClient | 同上 | grep 无跨域 Mapper |
| S-1 | 下单 Saga | Orchestrator | reserve 成功/失败/insert 失败补偿 | release 调用次数 |
| S-2 | 库存幂等 | InventoryOperationService | 重复 opId 不重复扣/补 | op 表 |
| P-1 | 商品评价/评分 | OrderClient | 有/无数据、缺失兜底 | 无 order 表直连 |
| M-1 | 商家统计 | OrderClient aggregates | 取消单不计、月边界、评分仅 completed | 无跨表 SQL |
| D1 | 账号注销 | AccountDeletionService | normal/merchant/重复/审核守卫/脱敏 | status enum |
| D2 | 订单删除 | OrderService.deleteOrder | 可删/不可删/重复/列表过滤 | buyer_deleted |
| D3 | 评价撤回 | OrderService.deleteReview | 撤回/无评价/completed 守卫/评分变化 | 字段置空 |
| D4 | 申请撤回 | MerchantApplicationService | pending/rejected/active/他人 | 行删除 |
| D5 | 认证撤回 | MerchantAuthenticationService | 同上 | 行删除 |
| D6/D7 | 商品单删 | ProductController | 归属/软删/级联/minPrice | is_active |
| G-1 | 路由顺序 | gateway yml | `/api/merchant/orders`→order；`/api/merchant/profile`→product | 逐条 curl |
| G-2 | 全注册 | application.yml | Nacos 实例列表 4 个 | 无硬编码 IP |

---

## 9. 风险与已知缺口（R1 后）

1. **product/order 共库**：边界是逻辑的；靠删 Mapper + grep 验收，不是数据库权限。D6 删除商品后，订单展示必须能读 inactive SPU，否则会出现“隐身订单”。
2. **下单是编排式 Saga**：已加 `checkout_op_tb` + `order_tb.checkout_op_id` 状态判定 + `order_inventory_op_tb` 取消/退货补偿 + 定时任务 + 原子库存条件更新；极端情况下仍可能出现“扣库存无订单”或“订单无库存”，需对账/告警，不引入 Seata。
3. **账号注销的 JWT 窗口**：refresh 已改为查库拒绝；access token 仍有 ≤2 小时窗口，product/order 不会每请求查用户状态。前端注销后应清除 token。
4. **D1 与 pending 商家申请**：D1 通过一条只读 `application-status` 调用检查 pending/rejected，存在则 409 要求先调 D4；除此之外不引入其他 user→product 调用。管理员审核仍对 deleted 用户 409。
5. **D4/D5 硬删除**：只允许 pending/rejected；若数据异常存在关联认证/商品，返回 409，不做孤儿删除。D5 的证书图片文件不自动清理，列为已知限制。
6. **Jackson 2/3 双轨**：Boot 4 MVC 用 Jackson 3，项目用 Jackson 2 处理 `JsonNode`/`JacksonTypeHandler`。内部 DTO 不用 `JsonNode`/`LocalDateTime`；Task 0.3 必须用真实请求确认 MVC 与 Feign 的命名/时间序列化一致。
7. **3 服务工作量约为 2 服务的 2–3 倍**；Phase 1 是保底可提交检查点。
8. **未实测项**：SCA 2025.1.0.0 + Boot 4.0.4 + Gateway WebFlux 组合、Nacos 3.x 控制台端口环境变量；Task 0.3 先验证，失败则调整版本/端口并记录。
9. **`matchSku` 历史行为**：非空 selection 未匹配时旧代码回退第一个 SKU；R1 改为 409（定点修复），需在 Task 2.1 测试中固定，避免静默错单。
10. **D1 历史订单 PII**：账号注销只匿名化 `user_tb`，历史订单收件人信息保留在 order-service；本期不做跨服务 PII 清理，文档明确。

---

## 10. 自查（写完计划后执行）

- [ ] 每条实验要求都能指向具体任务：≥2 服务（Phase 1/2）、Nacos（Task 0.3）、Feign（Task 1.3/2.3/2.4）、Gateway（Task 2.5）、RESTful DELETE（Phase 3）、详细文档（Task 4.2）。
- [ ] 无占位符：每个任务有文件、步骤、测试或实现方向；机械移动用 `git mv` 列表。
- [ ] 类型一致：`UserBriefVo`、`SpuBrief`、`MerchantCatalog`、`CheckoutItemSnapshot`、`MerchantAggregate` 在契约与任务中同名同字段。
- [ ] 边界一致：product/order 共 `commerce_db`；DELETE D1–D7 的归属、守卫、幂等与 §6 一致。
- [ ] 不改前端：新 DELETE 只加后端接口；前端现有路径/字段不变。
- [ ] R2 门禁：gateway 依赖树无 `spring-boot-starter-webmvc`；user/order 启动类有 `@MapperScan`；product/order/user 有 `@EnableFeignClients`；配置文件统一 `application.yml`。
- [ ] R2 数据：`inventory_op_tb`（含 result_json）、`merchant_role_sync_tb`、`checkout_op_tb`（含 reserve_result_json）、`order_inventory_op_tb` 在 init SQL 与各自 runner 中都能创建；终态 SQL 含现有 order 列 + `checkout_op_id`。
- [ ] R2 补偿：release 支持只带 opId；有 op-status 查询；Task 2.3 测试区分 409/超时；补偿任务可查状态并重试。
- [ ] R2 并发：库存扣减是原子条件 UPDATE；D6 后 inactive SPU 仍能被订单展示；D3 评价读取统一 `status='completed'`。
- [ ] R2 契约：`ReviewItem.id`、`SpuBrief.isActive`、`MerchantCatalog.spus[].isActive`、`CheckoutItemSnapshot.lineId/quantity` 都存在且被消费。
- [ ] R2 安全：内部令牌 fail-closed；D1/D2/D3 有显式 DELETE 规则；D4 双角色规则在 `/api/merchant/**` 之前；role 只允许 normal→merchant；D1 注销前查 pending 申请。
- [ ] R2 错误码：所有 DELETE 测试断言 `Result.code`，不混用 HTTP status。

---

## 11. 执行顺序与检查点

1. Phase 0 通过（四服务注册 + 网关 stub）→ 才动业务代码。
2. Phase 1 通过（保底 2 服务）→ 可提交状态。
3. Phase 2 通过（3 服务 + Saga）→ 再做 DELETE。
4. Phase 3 通过（D1–D7）→ 做 E2E 与文档。
5. 每个任务由子智能体做一次“实现后评审”（正确性、边界、测试、是否残留跨域访问）。
