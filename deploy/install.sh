#!/usr/bin/env bash
# CGCH 一键安装 / 升级脚本（Ubuntu 22.04 / 24.04）
#
# 首次安装（在服务器上以 root 执行）：
#   curl -fsSL https://raw.githubusercontent.com/rrt887-glitch/CGCH-B2B/main/deploy/install.sh | bash
# 绑定域名并启用 HTTPS（域名已解析到本机后执行）：
#   curl -fsSL https://raw.githubusercontent.com/rrt887-glitch/CGCH-B2B/main/deploy/install.sh | DOMAIN=crm.example.com bash
# 升级到最新版本：再执行一次同样的命令即可，数据和配置都会保留。
#
# 镜像为私有时，先提供只读令牌：GHCR_USER=用户名 GHCR_TOKEN=令牌 ... | bash
set -euo pipefail

# 整个脚本包在函数里：用 curl | bash 执行时，bash 会先读完全部内容再运行，避免中途的命令读走后面的脚本
main() {

DIR=/opt/cgch
REGISTRY=ghcr.io/rrt887-glitch
VERSION="${CGCH_VERSION:-latest}"

say() { printf '\033[1;32m==> %s\033[0m\n' "$*"; }
die() { printf '\033[1;31m错误：%s\033[0m\n' "$*" >&2; exit 1; }

[ "$(id -u)" = 0 ] || die "请用 root 用户执行（或在命令前加 sudo）"

# ---------- 1. Docker ----------
if ! command -v docker >/dev/null 2>&1; then
  say "安装 Docker"
  curl -fsSL https://get.docker.com | sh
fi
docker compose version >/dev/null 2>&1 || die "缺少 docker compose 插件，请执行 apt-get install -y docker-compose-plugin"
systemctl enable --now docker >/dev/null 2>&1 || true

# 内存小于 4G 时加 2G 交换空间，避免打包任务或高峰时被系统杀掉
if [ "$(awk '/MemTotal/ {print int($2/1024)}' /proc/meminfo)" -lt 3800 ] && ! swapon --show | grep -q .; then
  say "内存不足 4G，创建 2G 交换空间"
  fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile >/dev/null && swapon /swapfile
  grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

mkdir -p "$DIR"
cd "$DIR"

# ---------- 2. 配置（只在首次生成；密钥一旦生成不要再改） ----------
rand() { openssl rand -hex "$1"; }
IP=$(curl -fsS --max-time 5 https://api.ipify.org 2>/dev/null || hostname -I | awk '{print $1}')
if [ ! -f .env ]; then
  say "生成配置 $DIR/.env（含随机生成的数据库密码与加密密钥）"
  umask 077
  cat > .env <<EOF
# ===== CGCH 生产配置：由 install.sh 生成。请备份本文件，加密密钥丢失后已加密的数据无法恢复 =====
CGCH_FIELD_ENCRYPT_KEY=$(openssl rand -base64 24)
MYSQL_ROOT_PASSWORD=$(rand 16)
DB_USERNAME=cgch
DB_PASSWORD=$(rand 16)
REDIS_PASSWORD=$(rand 16)

# 访问地址：绑定域名后由 install.sh 自动改为 https://域名
CGCH_SITE=:80
PUBLIC_BASE_URL=http://${IP}

# 公开页面（隐私政策等）里的公司信息：平台审核会检查，请填写真实信息后执行 docker compose up -d
CGCH_PRODUCT_NAME=CGCH
CGCH_COMPANY_NAME=
CGCH_COMPANY_NAME_EN=
CGCH_CONTACT_EMAIL=
CGCH_COMPANY_ADDRESS=

# Meta（Facebook 主页 / Instagram / WhatsApp）
CGCH_META_APP_ID=
CGCH_META_APP_SECRET=
CGCH_META_WEBHOOK_VERIFY_TOKEN=$(rand 12)
CGCH_META_WA_CONFIG_ID=

# 微软 Outlook / Microsoft 365
CGCH_OAUTH_MICROSOFT_CLIENT_ID=
CGCH_OAUTH_MICROSOFT_CLIENT_SECRET=

# Google Gmail（暂不使用，Gmail 用应用专用密码绑定）
CGCH_OAUTH_GOOGLE_CLIENT_ID=
CGCH_OAUTH_GOOGLE_CLIENT_SECRET=
EOF
  umask 022
fi
if [ -n "${DOMAIN:-}" ]; then
  say "绑定域名 $DOMAIN（启用 HTTPS）"
  SITE="$DOMAIN"
  # 主域名（如 example.com）同时为 www.example.com 申请证书；设置 WWW=0 可关闭
  if [ "$(echo "$DOMAIN" | tr -cd . | wc -c)" = 1 ] && [ "${WWW:-1}" != 0 ]; then
    SITE="${DOMAIN}, www.${DOMAIN}"
  fi
  sed -i "s|^CGCH_SITE=.*|CGCH_SITE=\"${SITE}\"|; s|^PUBLIC_BASE_URL=.*|PUBLIC_BASE_URL=https://${DOMAIN}|" .env
fi

# ---------- 3. 编排文件（每次执行都更新到最新） ----------
cat > docker-compose.yml <<EOF
# 由 install.sh 生成，升级时会被覆盖；个性化配置请写在 .env 中
name: cgch
services:
  mysql:
    image: ${REGISTRY}/cgch-mysql:${VERSION}
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: \${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: ruoyi-vue-pro
      MYSQL_USER: \${DB_USERNAME}
      MYSQL_PASSWORD: \${DB_PASSWORD}
      TZ: Asia/Shanghai
    volumes:
      - mysql-data:/var/lib/mysql
    healthcheck:
      test: ["CMD-SHELL", "mysqladmin ping -h 127.0.0.1 -uroot -p\$\$MYSQL_ROOT_PASSWORD --silent"]
      interval: 10s
      retries: 60

  redis:
    image: redis:7
    restart: unless-stopped
    command: ["sh", "-c", "exec redis-server --requirepass \"\$\$REDIS_PASSWORD\" --appendonly yes"]
    environment:
      REDIS_PASSWORD: \${REDIS_PASSWORD}
    volumes:
      - redis-data:/data

  backend:
    image: ${REGISTRY}/cgch-backend:${VERSION}
    restart: unless-stopped
    depends_on:
      mysql: { condition: service_healthy }
      redis: { condition: service_started }
    env_file: .env
    environment:
      DB_URL: "jdbc:mysql://mysql:3306/ruoyi-vue-pro?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true&rewriteBatchedStatements=true"
      REDIS_HOST: redis
    volumes:
      - backend-logs:/root/logs
      - backend-files:/app/files

  web:
    image: ${REGISTRY}/cgch-web:${VERSION}
    restart: unless-stopped
    depends_on: [backend]
    environment:
      CGCH_SITE: \${CGCH_SITE}
    ports:
      - "80:80"
      - "443:443"
      - "443:443/udp"
    volumes:
      - caddy-data:/data
      - caddy-config:/config

volumes:
  mysql-data:
  redis-data:
  backend-logs:
  backend-files:
  caddy-data:
  caddy-config:
EOF

# ---------- 4. 拉取并启动 ----------
if [ -n "${GHCR_TOKEN:-}" ]; then
  echo "$GHCR_TOKEN" | docker login ghcr.io -u "${GHCR_USER:-rrt887-glitch}" --password-stdin
fi
say "下载最新版本镜像"
if ! docker compose pull; then
  die "镜像下载失败。如果提示 denied / unauthorized，说明 GitHub 上的镜像是私有的：
  请把 github.com/rrt887-glitch 下 cgch-backend、cgch-web、cgch-mysql 三个 Package 设为 Public，
  或在命令前加 GHCR_USER=你的GitHub用户名 GHCR_TOKEN=只读令牌 后重试"
fi
say "启动服务（首次启动需要导入数据库，约 1～3 分钟）"
docker compose up -d --remove-orphans

wait_backend() {
  for i in $(seq 1 90); do
    if docker compose exec -T web wget -q -O /dev/null "http://backend:48080/admin-api/public/site/about" </dev/null 2>/dev/null; then
      return 0
    fi
    sleep 4
  done
  docker compose ps; docker compose logs --tail 80 backend
  die "后端没有在预期时间内启动，请把上面的日志发给技术支持"
}
wait_backend

# 附件默认存数据库，访问地址要和对外地址一致（上游默认是 127.0.0.1）
URL=$(grep '^PUBLIC_BASE_URL=' .env | cut -d= -f2-)
sql() { docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -s ruoyi-vue-pro' 2>/dev/null <<< "$1"; }
CUR=$(sql "SELECT JSON_UNQUOTE(JSON_EXTRACT(config, '\$.domain')) FROM infra_file_config WHERE id = 4;" || true)
if [ -n "$CUR" ] && [ "$CUR" != "$URL" ]; then
  say "更新附件访问地址为 $URL"
  sql "UPDATE infra_file_config SET config = JSON_SET(config, '\$.domain', '$URL') WHERE id = 4;"
  docker compose restart backend >/dev/null
  wait_backend
fi

say "安装完成：$URL"
cat <<EOF

  管理后台：$URL   （首次登录账号 admin / admin123，登录后请立即修改密码）
  公开页面：$URL/privacy  $URL/terms  $URL/data-deletion  $URL/about
  配置文件：$DIR/.env   （请备份；修改后执行 cd $DIR && docker compose up -d 生效）
  查看日志：cd $DIR && docker compose logs -f backend

  阿里云轻量服务器请在「防火墙」中放行 80 和 443 端口。
EOF
}

main "$@" </dev/null
