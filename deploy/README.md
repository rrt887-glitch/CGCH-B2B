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
