# 前端 + HTTPS 入口：Caddy 托管前端静态文件，把 /admin-api 转给后端，并自动申请与续期 HTTPS 证书
FROM caddy:2.8-alpine
COPY dist/ /srv/
COPY Caddyfile /etc/caddy/Caddyfile
