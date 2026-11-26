#!/usr/bin/env bash
# 回滚到上一个版本（或指定版本）
#
# 用法（服务器上以 root 执行）：
#   bash /opt/cgch/rollback.sh              # 回滚到上一个版本
#   bash /opt/cgch/rollback.sh <提交sha>     # 回到指定版本（见 deploy-history.log）
#   bash /opt/cgch/rollback.sh --list        # 看部署历史
#
# 原理：CI 每次构建都会同时推送 :latest 和 :<提交sha> 两个标签，
#      回滚就是把镜像版本切回之前的 sha（代码回滚；数据库不会倒退，见文末说明）。
set -euo pipefail

DIR=/opt/cgch
HIST=$DIR/deploy-history.log

say() { printf '\033[1;32m==> %s\033[0m\n' "$*"; }
die() { printf '\033[1;31m错误：%s\033[0m\n' "$*" >&2; exit 1; }

[ "$(id -u)" = 0 ] || die "请用 root 执行（或在命令前加 sudo）"
cd "$DIR" 2>/dev/null || die "找不到 $DIR，请确认服务器已安装"

[ -f "$HIST" ] || die "还没有部署历史（$HIST）。请先正常部署一次，之后才能回滚"

if [ "${1:-}" = "--list" ]; then
  say "部署历史（最新在下）"
  cat "$HIST"
  exit 0
fi

TARGET=${1:-}
if [ -z "$TARGET" ]; then
  # 倒数第二条 = 上一次成功部署的版本；如果和最后一条相同，就再往前找
  LAST=$(awk 'NF>=2{print $2}' "$HIST" | tail -n 1)
  TARGET=$(awk 'NF>=2{print $2}' "$HIST" | awk -v last="$LAST" '$0!=last' | tail -n 1)
fi
[ -n "$TARGET" ] || die "找不到可回滚的版本（历史里只有一个版本）"

say "准备回滚到：$TARGET"

# 1) 先暂停自动部署，否则 10 分钟内又会被自动升回最新
touch "$DIR/autodeploy.disabled"
say "已暂停自动部署（/opt/cgch/autodeploy.disabled）"

# 2) 备份当前数据库（回滚只是换代码，但先留一份总没错）
STAMP=$(date '+%F-%H%M')
say "备份数据库到 db-before-rollback-$STAMP.sql.gz"
if docker compose exec -T mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --quick ruoyi-vue-pro' 2>/dev/null \
   | gzip > "$DIR/db-before-rollback-$STAMP.sql.gz"; then
  say "备份完成：$DIR/db-before-rollback-$STAMP.sql.gz"
else
  say "备份失败（继续回滚，如需保险请先手动备份）"
fi

# 3) 用指定版本重新部署
DOMAIN=$(grep '^PUBLIC_BASE_URL=' .env 2>/dev/null | head -1 | sed 's#^[^=]*=##; s#.*//##; s#/.*##' || true)
DOMAIN=${DOMAIN:-hig.global}

say "拉取并切换到版本 ${TARGET:0:8}，域名 $DOMAIN"
if curl -fsSL --max-time 60 "https://raw.githubusercontent.com/rrt887-glitch/CGCH-B2B/main/deploy/install.sh" -o /tmp/cgch-install-rollback.sh; then
  CGCH_VERSION="$TARGET" DOMAIN="$DOMAIN" bash /tmp/cgch-install-rollback.sh
else
  die "下载安装脚本失败（无法访问 GitHub）"
fi

say "回滚完成，当前版本：${TARGET:0:8}"
cat <<EOF

注意：
  1. 自动部署已暂停，修复后要恢复：
       rm /opt/cgch/autodeploy.disabled
       systemctl start cgch-autodeploy.service   # 想立刻升回最新就执行这条
  2. 本次只回滚「代码」，数据库里的升级（新增字段/表）不会倒退。
     如果新版本做过破坏性改动，需要用回滚前的备份：
       gunzip -c /opt/cgch/db-before-rollback-*.sql.gz | \\
         docker compose exec -T mysql sh -c 'mysql -uroot -p"\$MYSQL_ROOT_PASSWORD" ruoyi-vue-pro'
  3. 看部署历史：bash /opt/cgch/rollback.sh --list
EOF
