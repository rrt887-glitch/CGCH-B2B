#!/usr/bin/env bash
# 自动部署：检查 ghcr 上是否有新镜像，有就自动执行一次 install.sh
#
# 由 systemd 定时器每 10 分钟跑一次（见 deploy/auto-deploy-install.sh）。
#
# 失败保护：
#   - 拉镜像失败：跳过本次，下一轮重试（旧版本继续跑）
#   - 部署失败：记 pending 标记，下一轮继续重试（不会因为「镜像没变」而放弃）
#   - 连续两次失败：自动回滚到上一版并暂停自动部署，避免一直挂在坏版本
#
# 手动暂停：touch /opt/cgch/autodeploy.disabled
# 恢复：rm /opt/cgch/autodeploy.disabled
set -euo pipefail

DIR=/opt/cgch
cd "$DIR" 2>/dev/null || exit 0

[ -f "$DIR/autodeploy.disabled" ] && exit 0

exec 9>/run/cgch-autodeploy.lock
flock -n 9 || exit 0

log() { printf '[%s] %s\n' "$(date '+%F %T')" "$*"; }

DOMAIN=$(grep '^PUBLIC_BASE_URL=' .env 2>/dev/null | head -1 | sed 's#^[^=]*=##; s#.*//##; s#/.*##' || true)
DOMAIN=${DOMAIN:-hig.global}

image_ids() {
  docker compose config --images 2>/dev/null | sort -u | while read -r img; do
    [ -n "$img" ] || continue
    docker image inspect -f '{{.Id}}' "$img" 2>/dev/null || echo "missing:$img"
  done
}

BEFORE=$(image_ids || true)

# 拉取镜像：失败时重试一次，并把真实错误记下来（第三方镜像源偶发抖动很常见）
pull_once() {
  docker compose pull >"$DIR/autodeploy-pull.log" 2>&1
}
if ! pull_once; then
  log "拉取镜像失败，10 秒后重试一次"
  sleep 10
  if ! pull_once; then
    log "拉取镜像仍然失败，本次跳过。真实错误如下："
    tail -10 "$DIR/autodeploy-pull.log" 2>/dev/null || true
    exit 0
  fi
  log "重试成功"
fi

AFTER=$(image_ids || true)
PENDING=$(cat "$DIR/autodeploy.pending" 2>/dev/null || true)

# 镜像没变、也没有待重试的部署 → 什么都不做
if [ "$BEFORE" = "$AFTER" ] && [ "$PENDING" != "$AFTER" ]; then
  exit 0
fi

# 记录待部署状态：成功后删除；失败时下一轮会继续重试
printf '%s\n' "$AFTER" > "$DIR/autodeploy.pending"

log "检测到新镜像，开始自动部署（域名 $DOMAIN）"
if curl -fsSL --max-time 60 "https://raw.githubusercontent.com/rrt887-glitch/CGCH-B2B/main/deploy/install.sh" -o /tmp/cgch-install.sh \
   && DOMAIN="$DOMAIN" bash /tmp/cgch-install.sh >>"$DIR/autodeploy.log" 2>&1; then
  rm -f "$DIR/autodeploy.pending" "$DIR/autodeploy.fail-count"
  log "自动部署成功"
  exit 0
fi

# ---------- 失败处理 ----------
FAILS=$(cat "$DIR/autodeploy.fail-count" 2>/dev/null || echo 0)
FAILS=$((FAILS + 1))
printf '%s\n' "$FAILS" > "$DIR/autodeploy.fail-count"
log "自动部署失败（连续第 $FAILS 次），最后 40 行日志："
tail -40 "$DIR/autodeploy.log" || true

if [ "$FAILS" -lt 2 ]; then
  log "下一轮会继续重试；如果站点异常，可手动回滚：bash /opt/cgch/rollback.sh"
  exit 1
fi

# 连续失败两次：如果历史里有上一个版本，自动回滚（回滚脚本会暂停自动部署）
if [ -f "$DIR/rollback.sh" ] && [ -s "$DIR/deploy-history.log" ]; then
  log "连续失败，开始自动回滚到上一个版本"
  if bash "$DIR/rollback.sh" >>"$DIR/autodeploy.log" 2>&1; then
    rm -f "$DIR/autodeploy.pending"
    log "已自动回滚到上一版；自动部署已暂停，修好后执行：rm $DIR/autodeploy.disabled"
  else
    log "自动回滚也失败了，请人工处理：bash /opt/cgch/rollback.sh"
  fi
else
  log "没有可回滚的历史版本，请人工处理"
fi
exit 1
