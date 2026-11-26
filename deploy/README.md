# 部署脚本说明

| 脚本 | 用途 |
|---|---|
| `install.sh` | 一键安装 / 升级。拉最新镜像 → 同步最新 SQL → 执行迁移 → 启动 → 健康检查 |
| `auto-deploy.sh` | 自动部署：检查 ghcr 是否有新镜像，有就调用 `install.sh` |
| `auto-deploy-install.sh` | 安装自动部署的 systemd 定时器（每 10 分钟检查一次） |
| `diagnose.sh` | 只读诊断：CPU / 内存 / 磁盘 / 容器状态 / 日志错误 |
| `cleanup.sh` | 清理（⚠️ 会删除数据卷，慎用） |
| `Caddyfile` | 反向代理与 HTTPS |
| `docker-compose.prod.yml` | 生产编排（由 `install.sh` 生成到 `/opt/cgch/docker-compose.yml`） |

## 日常升级

```bash
cd /opt/cgch && curl -fsSL https://raw.githubusercontent.com/rrt887-glitch/CGCH-B2B/main/deploy/install.sh | DOMAIN=hig.global bash
```

## 自动部署

装一次即可（之后不用再手动部署）：

```bash
curl -fsSL https://raw.githubusercontent.com/rrt887-glitch/CGCH-B2B/main/deploy/auto-deploy-install.sh | bash
```

原理：

```
每 10 分钟检查一次 ghcr 镜像 ID
  → 没变化：什么都不做
  → 有变化：调用 install.sh 自动升级
```

常用命令：

```bash
systemctl list-timers cgch-autodeploy.timer   # 看下次检查时间
systemctl start cgch-autodeploy.service       # 立即检查一次
journalctl -u cgch-autodeploy.service -n 50   # 看自动部署日志
touch /opt/cgch/autodeploy.disabled           # 暂停
rm /opt/cgch/autodeploy.disabled              # 恢复
systemctl disable --now cgch-autodeploy.timer # 关掉
```

> 自动部署只会在**镜像更新后**触发（CI 构建成功才会推镜像），所以不会反复重启服务。

## 镜像加速（可选，国内建议开）

Docker Hub 的镜像（postgres / redis / mysql 基础镜像）在国内拉取很慢，可以配阿里云镜像加速器：

1. 打开阿里云控制台 → **容器镜像服务 ACR** → 左侧「镜像工具」→「镜像加速器」
2. 复制你的专属地址（形如 `https://xxxx.mirror.aliyuncs.com`）
3. 执行：

```bash
curl -fsSL https://raw.githubusercontent.com/rrt887-glitch/CGCH-B2B/main/deploy/install.sh -o /tmp/install.sh
CGCH_DOCKER_MIRROR=https://xxxx.mirror.aliyuncs.com DOMAIN=hig.global bash /tmp/install.sh
```

> 只影响 docker.io 的镜像；`ghcr.io` 上的后端/前端镜像不走这个加速器。
> 配置会写入 `/etc/docker/daemon.json`（原文件自动备份），并重启 Docker（约 20 秒）。

## 回滚
```bash
bash /opt/cgch/rollback.sh            # 回到上一个版本
bash /opt/cgch/rollback.sh --list     # 看部署历史
bash /opt/cgch/rollback.sh <提交sha>   # 回到指定版本
```

回滚脚本会：

1. **暂停自动部署**（否则 10 分钟内又升回最新）
2. **备份数据库**到 `/opt/cgch/db-before-rollback-*.sql.gz`
3. 用之前的镜像版本重新部署

注意：回滚只换**代码**，数据库已执行的升级不会倒退；如果新版本有破坏性改动，用第 2 步的备份恢复。修复后要恢复自动部署：

```bash
rm /opt/cgch/autodeploy.disabled
systemctl start cgch-autodeploy.service
```
