# 草根出海 · 跨境 B2B AI 数字员工平台

WhatsApp、Facebook、Instagram、邮箱、官网在线客服等多渠道询盘由 AI 数字员工自动接待，客户与对话沉淀到 CRM（线索、客户、商机、订单），按「智点」计费。

## 目录结构

| 目录 | 说明 |
| --- | --- |
| `backend/` | Java 17 + Spring Boot 3 后端，基于开源 ruoyi-vue-pro（MIT），保留 system、infra、bpm、crm、pay、ai 模块 |
| `backend/yudao-module-channel/` | 草根出海自研模块：渠道接入（WhatsApp 官方 API / 扫码、Messenger、Instagram、邮箱、官网 IM）、AI 接待、CRMClaw、看板、智点计费 |
| `backend/sql/mysql/` | 建表与初始化脚本；`cgch/migrations/` 是已有数据库的升级脚本 |
| `frontend/` | Vue 3 + Vite 8 + Ant Design Vue 前端，基于开源 yudao-ui-admin-vben（MIT），只保留 `apps/web-antd` |
| `frontend/apps/web-antd/src/views/cgch/` | 草根出海的页面（`v3/` 是看板、会话、CRM、智能体、渠道、设置六个主入口） |
| `deploy/` | 生产部署：一键安装脚本 `install.sh`、Caddy 配置、镜像 Dockerfile |
| `docker-compose.yml` | 本地开发用 MySQL 8 与 Redis 7 |
| `docs/` | 部署说明 `DEPLOY.md`、各平台开发者应用配置 `PLATFORM-APPS.md` |

## 本地开发

需要：JDK 17、Maven 3.9、Node 22、pnpm 11、Docker。

1. 启动数据库与缓存（只绑定本机 127.0.0.1）：

   ```bash
   docker compose up -d
   ```

   **首次启动会自动建好全部表并导入初始数据**，不需要另外导入：系统与基础设施（`ruoyi-vue-pro.sql`、`quartz.sql`），
   BPM / CRM / 支付 / AI / 渠道业务表（`cgch/00-*-schema.sql`），菜单（`cgch/01-menu.sql`），CRM 字典与渠道初始数据（`cgch/02-*`、`cgch/03-*`）。
   数据库已经存在时这些脚本不会再执行；要重建请先 `docker compose down -v` 删除数据卷。

2. 已有的本地数据库升级：按文件名顺序执行 `backend/sql/mysql/cgch/migrations/` 里还没执行过的脚本，例如

   ```bash
   docker exec -i cgch-mysql mysql -uroot -p123456 ruoyi-vue-pro < backend/sql/mysql/cgch/migrations/014-remove-whatsapp-system-chats.sql
   ```

   每个脚本都可以重复执行。生产环境由安装脚本自动执行（见下文）。

3. 启动后端（端口 48080，默认使用 `local` 配置）：

   ```bash
   cd backend
   mvn clean install -DskipTests
   cd yudao-server && mvn spring-boot:run
   ```

   数据库和 Redis 连接在 `backend/yudao-server/src/main/resources/application-local.yaml`，默认 `root / 123456`、`127.0.0.1:6379`。
   本地不填任何密钥也能启动；字段加密使用开发用默认密钥，**不能用于生产**。

4. 启动前端（端口 5666，接口代理到 `http://localhost:48080/admin-api`）：

   ```bash
   cd frontend
   pnpm install
   pnpm dev:antd
   ```

   浏览器打开 <http://localhost:5666>，默认账号 `admin / admin123`，登录后进入「看板」。

5. 提交前检查（CI 会执行同样的检查，有任何错误都不能合并）：

   ```bash
   cd frontend
   pnpm -F @vben/web-antd run typecheck   # 类型检查，必须 0 个错误
   pnpm test:unit                         # 单元测试，必须全部通过
   ```

## 功能配置

登录后按顺序配置即可开始接待：

1. **设置 → AI 接待设置**：填写大模型（服务商、API Key、模型名），选择自动回复模式。不配置大模型时 AI 接待和 CRMClaw 查询都不能用，页面会提示。
2. **智能体**：企业信息、AI 人设、知识库（产品资料、常见问题）、话术。知识库里没有依据的问题 AI 会转人工。
3. **渠道**：绑定 WhatsApp（扫码或官方 API）、Facebook、Instagram、邮箱、官网在线客服。各平台开发者应用的申请和回调地址见 [docs/PLATFORM-APPS.md](docs/PLATFORM-APPS.md)。
4. **设置 → 订阅与用量**：充值或购买智点；AI 回复、CRMClaw 查询、背调等会按次扣点，流水里可查。

## 部署到生产环境

一台装有 Linux 的服务器，一条命令安装或升级（会安装 Docker、生成密钥、拉取镜像、执行数据库升级并启动）：

```bash
curl -fsSL https://raw.githubusercontent.com/rrt887-glitch/CGCH-B2B/main/deploy/install.sh | DOMAIN=你的域名 bash
```

- 安装目录默认 `/opt/cgch`，配置文件 `/opt/cgch/.env`（首次自动生成，请备份；修改后执行 `cd /opt/cgch && docker compose up -d` 生效）
- 升级：在服务器上重新执行同一条命令。镜像由 GitHub Actions 在 `main` 分支合并后自动构建
- 防火墙放行 80、443（TCP）和 443（UDP，HTTP/3）端口
- 详细说明、环境变量列表和手动部署方式见 [docs/DEPLOY.md](docs/DEPLOY.md)

## 密钥与安全

- 所有密码和密钥通过环境变量提供（示例见 [.env.example](.env.example)），`.env` 不会被提交
- 以 `prod` 配置启动时，缺少字段加密密钥、使用公开过的旧密钥或弱数据库密码会**拒绝启动**
- 字段加密密钥（`CGCH_FIELD_ENCRYPT_KEY`）用于加密邮箱密码、渠道令牌、大模型 API Key，上线后不要再改
- 上线后立即修改默认管理员密码

## 第三方许可

见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。WhatsApp 扫码接入使用 [Evolution API](https://github.com/evolution-foundation/evolution-api)（Apache-2.0）。
