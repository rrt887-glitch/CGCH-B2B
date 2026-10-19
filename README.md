# CGCH · 跨境 B2B AI 数字员工平台

多渠道询盘由 AI 自动接待，沉淀为 CRM 线索、客户、商机与订单。功能规格见项目文档《AI数字员工平台 功能规格与重建方案》。

## 目录结构

| 目录 | 说明 |
| --- | --- |
| `backend/` | Java 17 + Spring Boot 3 后端，基于开源 ruoyi-vue-pro（MIT），已保留 system、infra、bpm、crm、pay、ai 模块 |
| `frontend/` | Vue 3 + Vite + Ant Design Vue 前端，基于开源 yudao-ui-admin-vben（MIT），只保留 `apps/web-antd` |
| `backend/yudao-module-channel/` | CGCH 自研渠道模块：邮箱 IMAP 收信、SMTP 发信、邮件工作台接口 |
| `docker-compose.yml` | 本地开发用 MySQL 8 与 Redis 7 |

## 本地启动

需要：JDK 17、Maven 3.9、Node 22、pnpm 11、Docker。

1. 启动数据库与缓存：

   ```bash
   docker compose up -d
   ```

   首次启动会自动导入系统基础表。CRM、支付、AI 模块的建表脚本不在上游代码仓库里，需要按上游文档单独导入：
   - CRM：<https://doc.iocoder.cn/crm/build/>
   - 支付：<https://doc.iocoder.cn/pay/build/>
   - AI：<https://doc.iocoder.cn/ai/build/>

2. 启动后端（默认端口 48080）：

   ```bash
   cd backend
   mvn clean install -DskipTests
   cd yudao-server && mvn spring-boot:run -Dspring-boot.run.profiles=local
   ```

   数据库连接在 `backend/yudao-server/src/main/resources/application-local.yaml`，默认 `root / 123456`。

3. 启动前端（默认端口 5666）：

   ```bash
   cd frontend
   pnpm install
   pnpm dev:antd
   ```

   浏览器打开 <http://localhost:5666>，默认账号 `admin / admin123`。

## 菜单与页面

- 菜单由后端 `system_menu` 表下发。`backend/sql/mysql/cgch/01-menu.sql` 建立 6 个一级模块（智能看板、渠道消息、AI-CRM、CRM Claw、数字员工管理、配置中台），并隐藏已移除模块的菜单。用 docker compose 首次启动时会自动执行；已有数据库请手动执行一次，可重复执行。
- 尚未开发的页面都指向 `frontend/apps/web-antd/src/views/cgch/placeholder/index.vue`，页面上会列出计划功能和所在阶段。开发某个页面时，新建组件并把对应菜单的 `component` 字段改过去即可。
- AI-CRM 暂时沿用上游 CRM 模块的页面（线索、客户、商机、合同、回款等），阶段 2 再按规格改造。
- 默认租户可以看到全部菜单。新建其他租户时，需要在「系统管理 → 租户套餐」里勾选 20000–20999 段的新菜单。

## 登录

- 账号密码登录和手机验证码登录都已具备，登录页右下角可切换。
- 手机验证码登录要先配置短信：在「系统管理 → 短信管理」里填入你自己的短信渠道密钥（阿里云、腾讯云等），并建一个编码为 `admin-sms-login` 的短信模板。

## 密钥与配置

上游代码里的示例密钥已全部换成 `YOUR_*` 占位符，用到时请填入自己的值。前后端 API 加密使用的 AES 密钥（`backend/yudao-server/src/main/resources/application.yaml` 的 `api-encrypt`，以及 `frontend/apps/web-antd/.env` 的 `VITE_APP_API_ENCRYPT_*`）是新生成的一对。两边必须一致，上线前请一起更换。

## 开发计划

1. 基础底座：多租户、登录、组织与权限、数据字典、导航（本阶段）
2. AI-CRM：线索、客户、公海、查重、联系人、商机看板、订单
3. 邮件与消息工作台：IMAP/SMTP、Gmail、Outlook、WhatsApp、Meta
4. AI 数字员工：知识库、人设、自动接待、违禁词、人工转接、Skill、智点计费
5. CRM Claw、深度背调、客户唤醒、看板与报告、订阅支付、其他社媒渠道

## 第三方许可

见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
