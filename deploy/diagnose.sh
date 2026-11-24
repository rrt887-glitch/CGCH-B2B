#!/usr/bin/env bash
# 只读诊断：CPU / 内存 / swap / 容器占用 / 后端报错
# 用法：bash diagnose.sh        或        curl -fsSL .../diagnose.sh | bash
set -u

echo "================ 基本信息 ================"
date
echo "内核: $(uname -r)    CPU 核数: $(nproc)"
echo "运行时长/负载: $(uptime)"

echo
echo "================ 内存与 swap ================"
free -m
echo "--- swap 设备 ---"
swapon --show 2>/dev/null || echo "（没有 swap）"

echo
echo "================ CPU 压力（vmstat 1 秒 × 3）================"
echo "关注列：r=等待CPU的进程数  si/so=swap换入换出  wa=IO等待  st=被宿主机偷走的CPU"
LANG=C vmstat 1 3 2>/dev/null || echo "（没有 vmstat）"

echo
echo "================ 容器实时占用 ================"
docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}" 2>/dev/null || echo "（docker stats 不可用）"

echo
echo "================ 最吃 CPU 的 8 个进程 ================"
ps -eo pid,comm,%cpu,%mem,rss --sort=-%cpu 2>/dev/null | head -9

echo
echo "================ 磁盘 ================"
df -h / /var/lib/docker 2>/dev/null | sort -u
echo "--- Docker 占用 ---"
docker system df 2>/dev/null || true

echo
echo "================ 容器状态 ================"
docker compose ps 2>/dev/null || docker ps --format "table {{.Names}}\t{{.Status}}"

echo
echo "================ 后端最近报错（最后 200 行里筛）================"
docker compose logs --tail 200 backend 2>/dev/null | grep -iE "error|exception|timeout|OutOfMemory|full gc|slow sql" | tail -20 || echo "（没有明显报错）"

echo
echo "================ 系统级异常（OOM / CPU 限速）================"
dmesg 2>/dev/null | grep -iE "oom|out of memory|throttl|cpu" | tail -10 || echo "（dmesg 无权限或无相关记录）"

echo
echo "================ 完成 ================"
echo "把以上输出整段发给技术支持即可。"
