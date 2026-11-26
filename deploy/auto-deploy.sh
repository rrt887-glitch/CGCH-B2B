#!/usr/bin/env bash
# 自动部署：检查 ghcr 上是否有新镜像，有就自动执行一次 install.sh
#
# 由 systemd 定时器每 10 分钟跑一次（见 deploy/auto-deploy-install.sh）。
# 只在「镜像变了」时部署，所以不会反复重启服务。
#
# 手动暂停：touch /opt/cgch/autodeploy.disabled
# 恢复：rm /opt/cgch/autodeploy.disabled
set -euo pipefail

DIR=/opt/cgch
cd "$DIR" 2>/dev/null || exit 0

# 暂停开关
[ -f "$DIR/autodeploy.disabled" ] && exit 0

# 同一个时间只允许一个实例
exec 9>/run/cgch-autodeploy.lock
flock -n 9 || exit 0

log() { printf '[%s] %s\n' "$(date '+%F %T')" "$*"; }

# 从 .env 里取域名（没有就用 hig.global）
DOMAIN=$(grep '^PUBLIC_BASE_URL=' .env 2>/dev/null | head -1 | sed 's#^[^=]*=##; s#.*//##; s#/.*##' || true)
DOMAIN=${DOMAIN:-hig.global}

# 当前所有 cgch 镜像的 ID，用来判断有没有新版本
# 顺序：先本地镜像，再 compose 配置里声明的镜像名
image_ids() {
  docker compose config --images 2>/dev/null | sort -u | while read -r img; do
    [ -n "$img" ] || continue
    docker image inspect -f '{{.Id}}' "$img" 2>/dev/null || echo "missing:$img"
  done
}

BEFORE=$(image_ids || true)

# 拉取最新镜像（静默）
if ! docker compose pull -q >/dev/null 2>&1; then
  log "拉取镜像失败（网络或镜像仓库问题），本次跳过"
  exit 0
fi

AFTER=$(image_ids || true)

if [ "$BEFORE" = "$AFTER" ]; then
  exit 0
fi

log "检测到新镜像，开始自动部署（域名 $DOMAIN）"
if curl -fsSL --max-time 60 "https://raw.githubusercontent.com/rrt887-glitch/CGCH-B2B/main/deploy/install.sh" -o /tmp/cgch-install.sh; then
  if DOMAIN="$DOMAIN" bash /tmp/cgch-install.sh >>"$DIR/autodeploy.log" 2>&1; then
    log "自动部署成功"
  else
    log "自动部署失败，最后 40 行日志："
    tail -40 "$DIR/autodeploy.log" || true
    exit 1
  fi
else
  log "下载安装脚本失败，本次跳过"
fi
