# 商家端代码修改指南

本文档用于说明：商家前端各模块由谁负责、改哪里、怎么改、如何联调与验收。  
适用项目：`JPet_Store`（Vue 3 + TypeScript + Pinia + Vue Router）。

## 1. 目录与职责总览

### 1.1 页面层（你主要改这里）

- `src/views/MerchantCenterView.vue`  
  商家中心（商家资料、认证、经营数据）。
- `src/views/ProductAuditView.vue`  
  商品上架审核（待审核、已下架、审核失败、提审/撤回/重提）。
- `src/views/CategoryView.vue`  
  商品管理列表（SPU 维度，筛选、排序、分页、上/下架、改名入口）。
- `src/views/LogisticsView.vue`  
  商品物流/订单模块（未寄出、在途、历史）。

### 1.2 接口层（前后端联调核心）

- `src/api/merchant.ts`  
  商家端 API 聚合：资料、审核、订单、SPU。
- `src/types/merchant.ts`  
  商家端类型定义：分页结构、SPU/SKU、订单、审核项等。
- `src/api/http.ts`  
  Axios 实例（基础 URL、跨域凭证、全局拦截可在此扩展）。

### 1.3 样式层

- `src/styles/legacy/merchant-center.css`
- `src/styles/legacy/product-audit.css`
- `src/styles/legacy/category.css`
- `src/styles/legacy/logistics.css`

## 2. 当前分工建议（可直接发同学）

- **（商家资料与认证）**  
  负责 `MerchantCenterView.vue` + `merchantApi` 中 profile/stats/auth。
- **（商品审核流）**  
  负责 `ProductAuditView.vue` + `merchantProductAuditApi`。
- **（商品管理 SPU/SKU）**  
  负责 `CategoryView.vue` + `merchantSpuApi`，重点是完整编辑器（SPU+SKU）。
- **同学（物流订单）**  
  负责 `LogisticsView.vue` + `merchantOrderApi`。
- **你（主负责人）**  
  统一接口契约、状态枚举、分页规范，最终联调验收。

## 3. 各模块“改哪里、怎么改”

### 3.1 商家中心（MerchantCenter）

目标：从本地展示升级为真实接口驱动。

改动入口：

- 页面逻辑：`src/views/MerchantCenterView.vue`
- API：`merchantApi.getProfile/updateProfile/getStats/submitAuth`
- 类型：`MerchantProfile`、`MerchantStats`、`MerchantProfilePayload`

改法建议：

1. 页面加载：先拉 `getProfile + getStats`，失败再用本地兜底。
2. 保存资料：仅提交后端允许字段（如名称、简介、邮箱、电话、标签）。
3. 认证申请：用 `FormData` 上传证书文件，后端返回申请状态。
4. 前端只做“输入校验 + 结果提示”，权限与审核规则交给后端。

### 3.2 商品上架审核（ProductAudit）

目标：状态流转真实化，避免“接口失败但 UI 已变”。

改动入口：

- 页面逻辑：`src/views/ProductAuditView.vue`
- API：`merchantProductAuditApi.listPending/listOffShelf/listRejected/create/update/withdraw`
- 类型：`MerchantProductItem`、`PageResult`

改法建议：

1. 提交新商品：`await create` 成功后再插入待审核列表。
2. 修改待审核商品：`await update` 成功后再更新本地卡片。
3. 撤回审核：`await withdraw` 成功后再移动到失败列表（原因“取消审核”）。
4. 列表读取统一按分页对象 `list/total/page/size` 消费。

### 3.3 商品管理（Category -> SPU/SKU）

目标：从展示页升级为“商家可管理”页。

改动入口：

- 页面逻辑：`src/views/CategoryView.vue`
- API：`merchantSpuApi.list/getById/create/update/remove`
- 类型：`MerchantSpu`、`MerchantSku`、`MerchantSpuListQuery`
- 样式：`src/styles/legacy/category.css`

改法建议：

1. 查询参数统一走 `MerchantSpuListQuery`：`page/size/keyword/category/status/sortField/sortOrder`。
2. 列表请求返回 `PageResult<MerchantSpu>`，页面用 `list + total` 渲染分页栏。
3. 当前已有轻量操作：改名、上/下架；下一步补完整编辑器（SPU 基础信息 + SKU 列表）。
4. SKU 处理建议：`attributes` 结构固定后再做动态表格，避免联调反复。

### 3.4 物流订单（Logistics）

目标：先读后改，逐步接入动作接口。

改动入口：

- 页面逻辑：`src/views/LogisticsView.vue`
- API：`merchantOrderApi.list/listHistory`
- 类型：`MerchantOrder`、`PageResult`

改法建议：

1. 先接列表查询（待发货、在途、已取消、已完成）。
2. 后续再接动作接口（发货、取消、更新物流节点），不要一次塞完。
3. 弹窗信息字段以接口实体为准，掩码逻辑放前端显示层。

## 4. 前后端接口对齐规范（必须统一）

### 4.1 分页返回结构（统一）

```json
{
  "data": {
    "list": [],
    "total": 0,
    "page": 1,
    "size": 10
  }
}
```

说明：前端 `merchant.ts` 已兼容“直接数组”，但联调阶段建议后端统一分页结构。

### 4.2 状态枚举（统一）

建议固定为：

- SPU：`草稿 | 待审核 | 已上架 | 已下架 | 已拒绝`
- SKU：`可售卖 | 不可售卖`

如果后端用英文枚举，前端再做一层映射，不要在多个页面写死中文判断。

### 4.3 排序字段（统一）

- `sortField`: `heat | purchaseCount | createdAt | price`
- `sortOrder`: `asc | desc`

### 4.4 错误码（建议）

至少约定：

- 参数错误
- 权限不足
- 资源不存在
- 状态流转非法（例如“已下架”直接发货）

前端据此做差异化提示，而不是统一“操作失败”。

## 5. 开发顺序（推荐）

1. **先锁契约**：字段名、枚举、分页、排序参数。
2. **再接列表**：先把 `list + total` 跑通。
3. **再接动作**：create/update/withdraw/remove/上下架。
4. **最后做体验**：loading、空态、错误提示、按钮禁用态。

## 6. 提交与联调建议

- 每次只提交一个模块（如“商品审核接口化”），便于回归。
- PR 描述固定三段：
  - 改了什么接口
  - 影响哪些页面
  - 如何验证（步骤 + 预期）
- 联调记录保留“请求参数 + 响应样例 + 异常样例”。

## 7. 你当前阶段的结论

商家端页面已经进入“可联调状态”，可并行推进后端接口开发。  
下一步最优先是：补齐 SPU/SKU 完整编辑流程与物流动作接口。

