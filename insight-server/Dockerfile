# syntax=docker/dockerfile:1
# 构建上下文：仓库根目录（完整源码构建，CI/发版用）
#   docker build -f insight-server/Dockerfile -t ghcr.io/iweidujiang/spring-insight-server:local .
# 本地联调请用 Dockerfile.runtime（本机 mvn package，避免容器内 Central 超时）
ARG MAVEN_IMAGE=maven:3.9.9-eclipse-temurin-21
FROM ${MAVEN_IMAGE} AS builder
WORKDIR /workspace

COPY .mvn/docker-settings.xml /root/.m2/settings.xml
# prepare-package 会下载 Node 并构建控制台进 jar
COPY . .

RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -ntp -pl insight-server -am clean package -DskipTests \
    && cp insight-server/target/insight-server.jar /workspace/insight-server-app.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# HEALTHCHECK 需要；镜像体积增加很少
RUN apk add --no-cache curl

LABEL org.opencontainers.image.title="Spring Insight Server" \
      org.opencontainers.image.description="轻量监测中心：Span 存储 + API + 控制台（默认 9966）" \
      org.opencontainers.image.source="https://github.com/iweidujiang/spring-insight" \
      org.opencontainers.image.licenses="Apache-2.0"

COPY --from=builder /workspace/insight-server-app.jar app.jar

EXPOSE 9966

# 存储由环境变量覆盖：memory | file | sqlite
ENV JAVA_OPTS="" \
    SPRING_INSIGHT_SERVER_STORAGE_FILE_PATH=/data/spans.json \
    SPRING_INSIGHT_SERVER_STORAGE_SQLITE_PATH=/data/insight.db

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -fsS http://127.0.0.1:9966/api/v1/health >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
