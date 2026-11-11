# JPet Store（统一前端）

这是当前唯一的正式前端入口，已经完成商家界面与管理员界面的合并。

对应关系：

- `JPet_Store`：统一前端，继续开发和运行时只使用这个目录
- `_archive/JPet_Shop_UI_Pre-20260417-archive.tar.gz`：旧管理员前端的备份归档，如需恢复可从这里解压

与后端项目 `pet-shop-backend` 配套：页面路由仍使用 `/index`、`/login` 等，由 Spring 转发到静态资源 `/spa/index.html`。

## 合并后的鉴权说明

当前前端统一使用后端 JWT 接口：

- 商家/普通用户登录：`/api/auth/merchant/login`
- 管理员登录：`/api/auth/admin/login`
- 商家注册：`/api/auth/merchant/register`
- token 刷新：`/api/auth/token/refresh`

登录页已合并为双入口模式：

- 商家入口登录后，`normal_user` / `merchant_user` 自动进入商家界面
- 管理员入口登录后，`main_admin` / `sub_admin` 自动进入管理员界面

## 路由与页面说明

当前仍保留部分历史路径以兼容已有跳转：

- `/index`：商家首页
- `/category`：商品页
- `/cart`：商品物流
- `/medical`：商品上架审核
- `/profile`：商家中心
- `/admin`：管理员控制台
- `/login`：统一登录页

## 开发

1. 启动后端（默认 `http://localhost:8080`）。
2. 在本目录执行：

```bash
npm install
npm run dev
```

浏览器访问：**http://localhost:5173/spa/**。

接口与图片通过 Vite 代理到 8080（主要为 `/api`、`/images`）。

## 生产构建（写入后端 static）

```bash
npm run build
```

产物输出到：`../pet-shop-backend/src/main/resources/static/spa/`。

然后照常打包/运行 Spring Boot。访问示例：`http://localhost:8080/index`。

## 自定义 context-path

若 `application.yml` / `config.properties` 中 `app.contextPath` 非空，需同步：

- 在 `.env.production` 中设置 `VITE_CONTEXT_PATH=/你的前缀`
- 并将 `vite.config.ts` 中的 `base` 同步为对应的 `/spa/` 前缀后重新构建
