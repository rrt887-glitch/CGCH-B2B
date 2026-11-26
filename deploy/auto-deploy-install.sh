#!/usr/bin/env bash
# 安装「自动部署」定时器：每 10 分钟检查一次新镜像，有就自动部署
#
# 用法（服务器上以 root 执行一次）：
#   curl -fsSL https://raw.githubusercontent.com/rrt887-glitch/CGCH-B2B/main/deploy/auto-deploy-install.sh | bash
#
# 关闭：systemctl disable --now cgch-autodeploy.timer
# 暂停：touch /opt/cgch/autodeploy.disabled
set -euo pipefail

[ "$(id -u)" = 0 ] || { echo "请用 root 执行"; exit 1; }

# 脚本本体
curl -fsSL https://raw.githubusercontent.com/rrt887-glitch/CGCH-B2B/main/deploy/auto-deploy.sh -o /usr/local/bin/cgch-auto-deploy
chmod +x /usr/local/bin/cgch-auto-deploy

# 回滚脚本放到 /opt/cgch/rollback.sh，随时可用
mkdir -p /opt/cgch
curl -fsSL https://raw.githubusercontent.com/rrt887-glitch/CGCH-B2B/main/deploy/rollback.sh -o /opt/cgch/rollback.sh
chmod +x /opt/cgch/rollback.sh

# 服务：一次执行
cat >/etc/systemd/system/cgch-autodeploy.service <<'EOF'
[Unit]
Description=CGCH 自动部署（有新的镜像时升级）
After=docker.service network-online.target
Wants=network-online.target

[Service]
Type=oneshot
ExecStart=/usr/local/bin/cgch-auto-deploy
Nice=10
IOSchedulingClass=best-effort
IOSchedulingPriority=7
EOF

# 定时器：开机 5 分钟后开始，每 10 分钟检查一次
cat >/etc/systemd/system/cgch-autodeploy.timer <<'EOF'
[Unit]
Description=每 10 分钟检查一次 CGCH 新镜像

[Timer]
OnBootSec=5min
OnUnitActiveSec=10min
AccuracySec=30s
Unit=cgch-autodeploy.service

[Install]
WantedBy=timers.target
EOF

systemctl daemon-reload
systemctl enable --now cgch-autodeploy.timer

echo
echo "✅ 自动部署已开启：每 10 分钟检查一次，有新镜像会自动升级"
echo
echo "常用命令："
echo "  查看状态：systemctl list-timers cgch-autodeploy.timer"
echo "  立即检查一次：systemctl start cgch-autodeploy.service"
echo "  查看日志：journalctl -u cgch-autodeploy.service -n 50 --no-pager"
echo "  暂停自动部署：touch /opt/cgch/autodeploy.disabled"
echo "  恢复自动部署：rm /opt/cgch/autodeploy.disabled"
echo "  关闭自动部署：systemctl disable --now cgch-autodeploy.timer"
echo
echo "回滚到上一个版本：bash /opt/cgch/rollback.sh"
echo "看部署历史：bash /opt/cgch/rollback.sh --list"
