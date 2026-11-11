# 微服务化改造 · 实测验证记录

> 环境：JDK 21、Maven 3.8.7、Spring Boot 4.0.4、Spring Cloud 2025.1.0、SCA 2025.1.0.0、Nacos 3.1.2、MySQL 8.0
> 端口：gateway 8080、user 8081、product 8082、order 8083、MySQL 3308、Nacos 8848（控制台 8088）
> 日期：2026-11-04

## 1. 构建与启动

```bash
mvn -DskipTests install          # 五模块 BUILD SUCCESS
docker compose -f deploy/docker-compose.yml up -d
scripts/start-all.sh
```

Nacos 注册结果：

```json
{"count":4,"doms":["gateway","user-service","product-service","order-service"]}
```

## 2. 网关路由

### 2.1 管理员登录（/api/auth → user-service）

```bash
curl -s -X POST http://localhost:8080/api/auth/admin/login \
  -H 'Content-Type: application/json' \
  -d '{"account":"admin","password":"Admin123"}'
```

返回 `code=200`，`data.access_token` 存在，role=`main_admin`。

### 2.2 公开商品（/api/products → product-service）

```bash
curl -s http://localhost:8080/api/products
```

返回 `code=200`，`data=[]`（当时未加测试商品）。

### 2.3 SPA（/spa/** → product-service）

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/spa/
```

返回 `200`，前端构建产物无需改动。

### 2.4 商家订单（/api/merchant/orders/** → order-service，先于 /api/merchant/**）

```bash
curl -s http://localhost:8080/api/merchant/orders -H "Authorization: Bearer $MERCHANT_TOKEN"
```

返回 `code=200`，列表含商品图、买家、SKU 属性，证明路由到 order-service 而非 product-service。

## 3. Feign 链路

### 3.1 product → user（商家资料）

```bash
curl -s http://localhost:8080/api/merchant/profile -H "Authorization: Bearer $MERCHANT_TOKEN"
```

返回 `merchant_name=爱宠乐园`、`email=test_user_1@example.com`、`username=test_user_1`。

### 3.2 order → product + user（下单）

```bash
curl -s -X POST http://localhost:8080/api/orders/checkout \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"receiver":"张三","phone":"13800000000","address":"湖南省长沙市岳麓区","payment_method":"alipay","items":[{"product_id":"1","product_name":"测试狗粮","quantity":2,"price":19.90,"selection":{"重量":"1kg"}}]}'
```

返回 `code=200`，`order_no=PS...`。随后：

- `GET /api/orders` 返回商品名、图片、商家名、买家昵称、SKU 属性。
- 库存 `99 → 97`，销量 `1 → 3`。

### 3.3 product → order（评价 / 评分 / 营收）

商家商品详情：

```bash
curl -s http://localhost:8080/api/merchant/products/1 -H "Authorization: Bearer $MERCHANT_TOKEN"
```

返回 `buyer_reviews=[{id, user, avatar, rating, text, date}]`。

商家统计：

```bash
curl -s http://localhost:8080/api/merchant/stats -H "Authorization: Bearer $MERCHANT_TOKEN"
```

返回：

```json
{"month_revenue":39.80,"rating":5.0,"total_revenue":39.80,"trend_percent":100.0}
```

## 4. 下单 Saga / 取消回补

- `checkout_op_tb`：成功下单行状态 `COMMITTED`，含 `reserve_result_json`。
- `inventory_op_tb`：reserve 行 `RESERVED`，release 行 `RELEASED`。
- 取消订单后：

```text
stock 96 → 97
sales 4 → 3
order_inventory_op_tb: release:{orderId}:cancel / DONE
```

## 5. DELETE 实测

| 接口 | 请求 | 结果 |
|---|---|---|
| D1 账号注销 | `DELETE /api/user/profile` | 200；再次登录 401；旧 token 访问资料 401“账号已注销”；refresh 返回 403“账号已注销”；DB `status=deleted`、用户名/邮箱匿名化、profile NULL |
| D1 pending 守卫 | 有 pending 商家申请时注销 | 409“请先撤回商家入驻申请后再注销账号” |
| D1 商家守卫 | merchant_user 注销 | 409“商家账号需先完成商家注销” |
| D2 删除订单 | `DELETE /api/orders/1` | 200；买家列表清空；商家历史仍可见 |
| D3 撤回评价 | `DELETE /api/orders/1/review` | 200；商品评价为空；商家评分 5.0 → 0.0 |
| D4 撤回申请 | `DELETE /api/merchant/apply` | 200；申请状态变 null；merchant 行删除 |
| D5 撤回认证 | `DELETE /api/merchant/authentications/1` | 200；认证列表清空 |
| D6 单删 SPU | `DELETE /api/merchant/products/1` | 200；商品详情 404；买家商品列表空；商家历史订单仍可见 |
| D7 单删 SKU | `DELETE /api/merchant/products/skus/2` | 200；第二个 SKU `is_active=0` |
| D7 最后 SKU 守卫 | `DELETE /api/merchant/products/skus/1` | 409“已上架商品至少保留一个可售规格” |

## 6. 管理员审核 → 角色同步

1. 新用户提交商家申请（pending）。
2. `GET /api/admin/merchants/audits` 返回申请，含 user-service 的用户名/邮箱/手机号（product→user）。
3. `PUT /api/admin/merchants/3/audit` `{"audit_status":"active"}`。
4. `user_tb.role` 变为 `merchant_user`。
5. 该用户重新登录，返回 `role=merchant_user`。

## 7. 关键问题与修正记录

| 问题 | 现象 | 修正 |
|---|---|---|
| SCG 5.x 路由前缀变更 | 网关全部 404 | 改用 `spring.cloud.gateway.server.webflux.routes` |
| Feign 字段命名不一致 | 内部调用 `opId` 为空 | 共享 Jackson 2 ObjectMapper 设为 SNAKE_CASE，并给 Feign 配 `JacksonEncoder/Decoder` |
| Feign 未设 Content-Type | 服务端报 `application/x-www-form-urlencoded` 不支持 | FeignConfig 加 JSON Content-Type 拦截器 |
| Feign 默认客户端不支持 PATCH | `Invalid HTTP method: PATCH` | 内部用户局部更新改为 PUT |
| 管理员用户列表 id 为 null | `UserVo.id` 是 Integer，`User.id` 是 Long | `UserVo.id` 改为 Long |
| 初始化 SQL 中文乱码 | 商家名/简介乱码 | init SQL 加 `SET NAMES utf8mb4`，并修正现有数据 |

## 8. 测试账号与数据

- 管理员：`admin / Admin123`
- 买家：`smoke_buyer_1 / Test_1234`
- 申请人：`smoke_applicant_1 / Test_1234`
- 新商家：`smoke_merchant_2 / Test_1234`
- 测试商品：SPU 1「测试狗粮」，SKU 1「重量=1kg」

> 上述账号是验证过程中注册的测试数据；重置数据库后需重新注册或按需补充种子数据。
