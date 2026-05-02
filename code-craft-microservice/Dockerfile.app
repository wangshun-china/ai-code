
# 构建阶段
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# 复制整个项目
COPY . .

# 构建主应用服务模块
RUN mvn clean package -pl code-craft-app -am -DskipTests

# 运行阶段
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 创建非root用户
RUN addgroup -S spring && adduser -S spring -G spring

# 创建必要的目录并设置权限
RUN mkdir -p /tmp/code_output /tmp/code_deploy /tmp/uploads /logs && \
    chown -R spring:spring /tmp/code_output /tmp/code_deploy /tmp/uploads /logs

USER spring:spring

# 复制构建好的jar文件
COPY --from=builder /app/code-craft-app/target/*.jar app.jar

# 设置环境变量
ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_OPTS="-Xmx1024m -Xms512m -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

# 暴露端口
EXPOSE 8126

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8126/actuator/health || exit 1

# 启动应用
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
