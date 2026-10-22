# CGCH 部署说明

本文说明如何把 CGCH 部署到生产服务器。所有密码和密钥都通过**环境变量**提供，仓库里不保存任何真实值。

## 一、上线前必须做的安全准备

1. **把 GitHub 仓库设为私有**：Settings → General → 页面最下方 Danger Zone → Change visibility → Private。
2. **生成新的密钥**：早期版本的配置文件里曾写入过字段加密密钥和 API 加密密钥，这些值已经随仓库历史公开，**全部作废**，不能再使用。生产环境请重新生成：
   ```bash
   openssl rand -base64 24   # 生成正好 32 个字符的随机字符串，用作 CGCH_FIELD_ENCRYPT_KEY
   ```
   以 `prod` 配置启动时，后端会检查这些密钥：未设置、长度不是 16/24/32 位或使用了公开过的值，都会**直接拒绝启动**。
3. **数据库、Redis 使用强密码**，并且不要对公网开放端口。
4. **上线后立即修改管理员密码**：默认账号 `admin / admin123` 只用于首次登录。

## 二、环境变量

复制仓库根目录的 `.env.example` 为 `.env` 并填写（`.env` 已加入 `.gitignore`，不会被提交）。

| 变量 | 必填 | 说明 |
|---|---|---|
| `CGCH_FIELD_ENCRYPT_KEY` | 是 | 字段加密密钥，必须正好 16、24 或 32 个字符（AES 密钥长度），推荐 `openssl rand -base64 24`。用于加密数据库里的邮箱密码、WhatsApp 令牌、大模型 API Key。**上线后不要再改**，改了以后已加密的数据解不开，需要重新录入 |
| `DB_URL` | 是 | MySQL 连接串，示例见 `.env.example` |
| `DB_USERNAME` / `DB_PASSWORD` | 是 | 数据库账号和密码，密码不能是 123456 等弱密码 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | 建议 | Redis 地址和密码 |
| `PUBLIC_BASE_URL` | 建议 | 对外访问的 HTTPS 地址，例如 `https://crm.example.com`，用于支付渠道回调 |
| `CAPTCHA_ENABLE` | 否 | 登录图片验证码，默认开启 |
| `CGCH_API_ENCRYPT_*` | 否 | 接口加密，默认关闭；开启时前后端必须使用同一对密钥 |

## 三、方式一：Docker Compose（推荐）

`deploy/docker-compose.prod.yml` 包含 MySQL、Redis、后端和 Nginx。数据库和 Redis 只在容器网络内访问，不暴露端口。

```bash
# 1. 准备环境变量
cp .env.example deploy/.env
vi deploy/.env                    # 填写 CGCH_FIELD_ENCRYPT_KEY、DB_PASSWORD、REDIS_PASSWORD、MYSQL_ROOT_PASSWORD 等

# 2. 打包后端（JDK 17 + Maven）
cd backend && mvn -pl yudao-server -am package -DskipTests && cd ..

# 3. 打包前端（Node 22 + pnpm），接口走同域的 /admin-api
cd frontend
pnpm install
VITE_BASE_URL= VITE_GLOB_API_URL=/admin-api pnpm build:antd
cd ..

# 4. 启动（首次启动会自动初始化数据库）
cd deploy && docker compose -f docker-compose.prod.yml up -d --build
```

启动后访问 `http://服务器地址/`。生产环境请在前面加 HTTPS（云负载均衡、Caddy 或 certbot 均可），WhatsApp Webhook 和支付回调都要求 HTTPS。

## 四、方式二：直接运行 Jar

```bash
# 1. 初始化数据库：按顺序导入以下 SQL
#    backend/sql/mysql/ruoyi-vue-pro.sql
#    backend/sql/mysql/quartz.sql
#    backend/sql/mysql/cgch/00-bpm-schema.sql
#    backend/sql/mysql/cgch/00-crm-schema.sql
#    backend/sql/mysql/cgch/00-pay-schema.sql
#    backend/sql/mysql/cgch/00-ai-schema.sql
#    backend/sql/mysql/cgch/00-channel-schema.sql
#    backend/sql/mysql/cgch/01-menu.sql
#    backend/sql/mysql/cgch/02-crm-seed.sql
#    backend/sql/mysql/cgch/03-channel-seed.sql

# 2. 设置环境变量后启动
set -a; source .env; set +a
java -Xms512m -Xmx1g -jar backend/yudao-server/target/yudao-server.jar --spring.profiles.active=prod
```

前端打包产物在 `frontend/apps/web-antd/dist`，用 Nginx 托管，并把 `/admin-api/` 反向代理到后端 48080 端口，可直接参考 `deploy/nginx.conf`。

## 五、`prod` 配置与本地开发的区别

| 项目 | 本地（local） | 生产（prod） |
|---|---|---|
| 密码与密钥 | 有仅供开发的默认值 | 全部来自环境变量，启动时检查 |
| 模拟登录 | 开启 | 关闭 |
| 登录验证码 | 关闭 | 开启 |
| Druid 监控页、接口文档 | 开启 | 关闭 |
| Actuator 端点 | 全部开放 | 只开放 health、info |
| SQL 日志 | debug | info |
| 背调访问内网地址 | CI 中允许 127.0.0.1 | 一律禁止 |

## 六、上线后在系统里完成的配置

1. **修改管理员密码**：右上角头像 → 个人中心。
2. **数字员工 → 档案**：填写大模型接口地址、API Key、模型名称（需要支持工具调用，对话助手要用）；按需填写向量模型。
3. **配置中台 → 渠道管理**：绑定企业邮箱；添加 WhatsApp 号码后，在 Meta 开发者后台把 Webhook 地址设为
   `https://你的域名/admin-api/channel/whatsapp/webhook/{账号编号}`，验证令牌填页面上显示的值。
4. **配置中台 → 发送策略**：新邮箱和新号码建议先把发送上限调低，再逐步放开。
5. **在线充值（可选）**：支付管理 → 应用信息 →「智点充值」，配置微信支付或支付宝渠道；后端和支付模块在同一个进程内，回调地址保持 `http://127.0.0.1:48080/...` 即可，端口改过的话同步修改。
6. **工作时间**：设置上班时间，以及上下班时间 AI 怎么接待。

## 七、安全检查清单

- [ ] 仓库已设为私有
- [ ] `CGCH_FIELD_ENCRYPT_KEY` 为新生成的随机值，并已安全备份（丢失后已加密的数据无法恢复）
- [ ] 数据库、Redis 为强密码，端口不对公网开放
- [ ] 已启用 HTTPS
- [ ] 已修改 `admin` 默认密码，并为业务员分配了最小必要的角色和数据权限
- [ ] 定期备份数据库（例如每天 `mysqldump`，保留 7~30 天）

## 八、升级

新版本如果新增了数据表或字段，`backend/sql/mysql/cgch/00-*-schema.sql` 会同步更新。生产环境升级前先备份数据库，再对比新旧建表脚本，执行新增的表和字段（`ALTER TABLE ... ADD COLUMN`），不要直接重新导入整个脚本，以免清空已有数据。
