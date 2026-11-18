#!/usr/bin/env bash
# 服务器体检 / 清理（草根出海）
#
# 默认只「看」，不动任何东西；加 --apply 才会删。
# 只删：没用的悬空镜像、已停止的容器、构建缓存、7 天没用过的旧镜像、过大的系统日志。
# 绝不删：数据卷（MySQL / Evolution / Caddy 的持久化数据）。
#
# 用法：
#   bash cleanup.sh              # 只看，不改
#   bash cleanup.sh --apply      # 执行清理
#   bash cleanup.sh --keep 3     # 保留最近 3 个版本（默认 2）
set -euo pipefail

APPLY=0
KEEP_IMAGES=2
for arg in "$@"; do
  case "$arg" in
    --apply) APPLY=1 ;;
    --keep=*) KEEP_IMAGES="${arg#*=}" ;;
    -h|--help)
      sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//'
      exit 0 ;;
    *) echo "未知参数：$arg（用 --help 看用法）"; exit 1 ;;
  esac
done

say() { printf '\n\033[1;32m==> %s\033[0m\n' "$1"; }
warn() { printf '\033[1;33m%s\033[0m\n' "$1"; }

say "1. 磁盘"
df -h / /var/lib/docker 2>/dev/null || df -h /

say "2. 内存"
free -h

say "3. Docker 总占用"
docker system df

say "4. /var/lib/docker 里最占地方的部分"
du -sh /var/lib/docker/* 2>/dev/null | sort -h | tail -8 || true

say "5. 本项目的镜像（每个版本一层，最占地方）"
docker images --format '{{.Repository}}:{{.Tag}}\t{{.Size}}\t{{.CreatedSince}}' 2>/dev/null \
  | grep -E 'cgch-(backend|web|mysql)|evolution-api' || echo "（没有）"

say "6. 容器状态"
if [ -f docker-compose.yml ]; then docker compose ps -a 2>/dev/null || true; else docker ps -a --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}'; fi

say "7. 数据卷（这些是数据，不要删）"
docker volume ls --format 'table {{.Name}}\t{{.Driver}}' 2>/dev/null || true

say "8. 数据卷占用（MySQL / Evolution / 附件）"
for v in $(docker volume ls -q 2>/dev/null | grep -E 'cgch' || true); do
  p=$(docker volume inspect -f '{{.Mountpoint}}' "$v" 2>/dev/null || true)
  [ -n "$p" ] && du -sh "$p" 2>/dev/null | sed "s|$p|$v|" || true
done

say "9. 系统日志与备份"
journalctl --disk-usage 2>/dev/null || echo "（没有 systemd-journald）"
ls -lh /root/*.sql 2>/dev/null || echo "（/root 下没有 .sql 备份）"

say "10. MySQL 二进制日志（默认开着会一直涨）"
if [ -f docker-compose.yml ]; then
  docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -s -e "SHOW BINARY LOGS;"' 2>/dev/null \
    | awk 'NF==3{printf "  %s\t%s\n", $1, $2; n++; s+=$2} END{if(n==0) print "  （没有 binlog）"; else printf "  共 %d 个文件，合计约 %.1f MB\n", n, s/1024/1024}' \
    || echo "  （读不到，跳过）"
fi

if [ "$APPLY" -eq 0 ]; then
  say "只是检查，什么都没改"
  cat <<EOF

要清理就执行：
  bash cleanup.sh --apply

会做这些（都不影响业务数据）：
  1) 删悬空镜像       docker image prune -f
  2) 删已停止的容器   docker container prune -f
  3) 删构建缓存       docker builder prune -f
  4) 删 7 天没用过的旧镜像（运行中的不受影响）
  5) 保留最近 ${KEEP_IMAGES} 个本项目版本，更早的删掉
  6) 系统日志压到 200MB 以内

不会删：MySQL / Evolution / Caddy 的数据卷。
EOF
  exit 0
fi

say "开始清理"

echo "· 悬空镜像"
docker image prune -f || true

echo "· 已停止的容器"
docker container prune -f || true

echo "· 构建缓存"
docker builder prune -f || true

echo "· 7 天没用过的旧镜像"
docker image prune -a -f --filter "until=168h" || true

echo "· 保留最近 ${KEEP_IMAGES} 个本项目版本"
# 只处理本项目仓库前缀的镜像；按创建时间倒序，前 KEEP_IMAGES 个保留
REG="${REGISTRY:-ghcr.io/rrt887-glitch}"
for repo in $(docker images --format '{{.Repository}}' | grep -E "^${REG}/cgch-" | sort -u); do
  keep=0
  for id in $(docker images --format '{{.ID}}' "$repo" | awk '!seen[$0]++'); do
    keep=$((keep + 1))
    if [ "$keep" -le "$KEEP_IMAGES" ]; then continue; fi
    # 有容器在用的镜像不能删（docker 会拒绝），失败也不影响
    docker image rm "$id" >/dev/null 2>&1 || true
  done
  echo "  $repo 保留前 ${KEEP_IMAGES} 个"
done

echo "· 系统日志"
journalctl --vacuum-size=200M 2>/dev/null || true

say "清理后"
df -h /
docker system df
