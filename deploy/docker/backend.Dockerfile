# 后端镜像：由 CI 打包好的 yudao-server.jar 构建（见 .github/workflows/build.yml 的 release 任务）
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY yudao-server.jar app.jar
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms512m -Xmx1200m -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom" \
    ARGS="--spring.profiles.active=prod"
EXPOSE 48080
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar $ARGS"]
