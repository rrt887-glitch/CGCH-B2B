#!/bin/sh
# 在数据库容器里执行尚未执行过的升级脚本（/cgch-migrations/*.sql，按文件名顺序），每个脚本只执行一次
set -e
M="mysql -uroot -p${MYSQL_ROOT_PASSWORD} --default-character-set=utf8mb4 ruoyi-vue-pro"
$M -e "CREATE TABLE IF NOT EXISTS cgch_migration (name varchar(100) NOT NULL PRIMARY KEY, applied_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP)" 2>/dev/null
for f in /cgch-migrations/*.sql; do
  [ -f "$f" ] || continue
  n=$(basename "$f")
  done=$($M -N -s -e "SELECT COUNT(*) FROM cgch_migration WHERE name = '$n'" 2>/dev/null)
  if [ "$done" = "0" ]; then
    echo "执行数据库升级：$n"
    $M < "$f"
    $M -e "INSERT INTO cgch_migration (name) VALUES ('$n')" 2>/dev/null
  fi
done
