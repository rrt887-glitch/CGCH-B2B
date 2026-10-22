# 数据库镜像：首次启动时自动按顺序导入建表与初始化脚本（之后的启动不会重复导入）
FROM mysql:8.0
COPY sql/ /docker-entrypoint-initdb.d/
CMD ["--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci", "--default-time-zone=+08:00"]
