# 数据库镜像：首次启动时自动按顺序导入建表与初始化脚本（之后的启动不会重复导入）；
# 已有数据库升级时由 install.sh 调用 cgch-migrate 执行 /cgch-migrations 里新增的脚本
FROM mysql:8.0
COPY sql/ /docker-entrypoint-initdb.d/
COPY migrations/ /cgch-migrations/
COPY cgch-migrate.sh /usr/local/bin/cgch-migrate
RUN chmod +x /usr/local/bin/cgch-migrate
CMD ["--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci", "--default-time-zone=+08:00"]
