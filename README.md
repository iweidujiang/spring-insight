# Spring Insight

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-21%2B-orange)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.9-brightgreen)](https://spring.io/projects/spring-boot)
[![Version](https://img.shields.io/badge/version-0.1.0-green.svg)](https://github.com/iweidujiang/spring-insight)
[![Maven Central](https://img.shields.io/badge/Maven%20Central-0.1.0-blue.svg)](https://central.sonatype.com/artifact/io.github.iweidujiang/spring-insight-agent-starter/0.1.0)

面向 **Spring Boot / Spring Cloud** 的轻量监测工具：**业务侧加一个 Starter 埋点上报，旁边用 Docker 起一个 `insight-server`，就能看服务拓扑和调用链路。**

适合中小项目、本地联调、教学演示——不想一上来就上整套 APM 时，可以先用它把「谁调了谁、慢在哪、错在哪」看清楚。

- 仓库：[https://github.com/iweidujiang/spring-insight](https://github.com/iweidujiang/spring-insight)
- 正式版：**`0.1.0`**（主线 Boot 3.5 / JDK 21）· Boot 2.7 / Java 8 见 **`0.1.0-boot2`**
- 问题与建议欢迎开 Issue

---

## 快速开始（两步）

### 1. 启动监测中心（Docker）

```bash
docker run --rm -p 9966:9966 \
  -e SPRING_INSIGHT_SERVER_STORAGE_MODE=file \
  -e SPRING_INSIGHT_SERVER_STORAGE_FILE_PATH=/data/spans.json \
  -v spring-insight-data:/data \
  ghcr.io/iweidujiang/spring-insight-server:0.1.0
```

或使用本仓库根目录 Compose：

```bash
docker compose up -d
```

浏览器打开：<http://localhost:9966/>

### 2. 业务服务接入

**Spring Boot 3：**

```xml
<dependency>
  <groupId>io.github.iweidujiang</groupId>
  <artifactId>spring-insight-agent-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

```yaml
spring:
  application:
    name: my-service
  insight:
    server-url: http://localhost:9966
```

**Spring Boot 2.7 / Java 8：** 改用 `spring-insight-agent-starter-boot2:0.1.0-boot2`（详见 [`boot2/README.md`](boot2/README.md)）。

造几笔跨服务调用，等几秒（Agent 异步批量上报），刷新控制台即可。

---

## 它能做什么

<img width="2327" height="516" alt="局部截取_20260907_210948" src="https://github.com/user-attachments/assets/5ddd8b7d-880f-4ffc-b2cb-59b0fdf6f53c" />

| 能力 | 说明 |
|------|------|
| HTTP / Feign / WebClient / Gateway 采集 | Agent 采 Span 并批量上报；success 与 HTTP 状态对齐；线程池可透传 Trace；WebFlux 走 Reactor Context；出站带 `remoteService` |
| 服务依赖拓扑 | 谁调用了谁（箭头与次数）；点击节点/边可下钻链路 |
| 链路列表 / Trace 详情 | 按 Trace 聚合，可搜索筛选；详情含瀑布时间线、tags、错误信息 |
| 延迟与错误摘要 | 仪表盘慢/错 Top（含 p50/p95），可点进已筛 Trace |
| Micrometer 联动 | 宿主有 MeterRegistry 时导出 `spring.insight.*`（Span Timer / 上报队列） |
| 控制台 UI | Vue 页面内嵌在 Server / Docker 镜像中 |

---

## 长什么样

### 仪表盘

<img width="2328" height="1251" alt="局部截取_20260907_210738" src="https://github.com/user-attachments/assets/9855277a-4790-4697-bca8-082ae1dbec31" />

### 服务拓扑

<img width="2313" height="1232" alt="局部截取_20260907_210519" src="https://github.com/user-attachments/assets/7a95aed7-9491-4cc0-b4fe-ccc00b1dab8c" />

### 链路追踪

<img width="2317" height="717" alt="局部截取_20260907_210813" src="https://github.com/user-attachments/assets/f1fedae5-f44f-4089-a6b3-fbad85f172d7" />
<img width="2341" height="1211" alt="局部截取_20260907_210850" src="https://github.com/user-attachments/assets/65b01e60-4225-48b7-8d45-e84fc37835d4" />

### 错误分析

<img width="2544" height="625" alt="局部截取_20260904_182715" src="https://github.com/user-attachments/assets/69243f02-13c7-4aee-bc0f-04de7721d373" />

---

## 版本与坐标

| 坐标 | 说明 |
|------|------|
| `io.github.iweidujiang:spring-insight-agent-starter:0.1.0` | **Boot 3 业务侧依赖这个**（[Central](https://central.sonatype.com/artifact/io.github.iweidujiang/spring-insight-agent-starter/0.1.0)） |
| `io.github.iweidujiang:insight-agent:0.1.0` | 采集核心（由 Starter 传递） |
| `io.github.iweidujiang:spring-insight-agent-starter-boot2:0.1.0-boot2` | **Boot 2.7 / Java 8**（[Central](https://central.sonatype.com/artifact/io.github.iweidujiang/spring-insight-agent-starter-boot2/0.1.0-boot2)） |
| `ghcr.io/iweidujiang/spring-insight-server:0.1.0` | **监测中心镜像（推荐）** |

本仓库开发中版本为 `0.1.1-SNAPSHOT` / `0.1.1-boot2-SNAPSHOT`，日常使用请用上方正式版。

---

## 架构一览

```text
业务微服务 × N  ──Starter──►  埋点、异步批量上报
独立进程 × 1    ──Docker──►  insight-server :9966（存储 + API + 控制台）
```

| 模块 | 角色 |
|------|------|
| `spring-insight-agent-starter` | 业务侧依赖 |
| `insight-agent` | 采集核心 |
| `insight-server` | 监测中心（GHCR 镜像或本地打包） |
| `insight-ui-vue` | 控制台前端（打进 Server） |
| `compose.yaml` | 一键起监测中心 |
| `boot2/` | Boot 2.7 / Java 8 兼容线 |

---

## 存储与其它启动方式

- **Docker / Compose** 默认 `file` 落盘并挂卷，重启可保留 Span。
- 仅内存：将 `SPRING_INSIGHT_SERVER_STORAGE_MODE` 设为 `memory`。
- 本机 jar（需 JDK 21 + 先构建仓库）：

```bash
mvn clean install -DskipTests
java -jar insight-server/target/insight-server-0.1.1-SNAPSHOT.jar
# 可选：--spring.insight.server.storage.mode=file
```

存储只在 **Server** 侧配置，业务微服务不要配 `spring.insight.server.storage.*`。  
健康检查：`GET /api/v1/health`。

演示工程：[spring-insight-sca-demo](https://github.com/iweidujiang/spring-insight-sca-demo)（Nacos + 若干微服务）。

---

## 常用配置

```yaml
spring:
  insight:
    enabled: true
    server-url: http://localhost:9966
    sample-rate: 1.0
    http-tracing-enabled: true
    context-propagation-enabled: true  # @Async / 线程池透传 Trace
    micrometer-enabled: true           # 有 MeterRegistry 时导出 spring.insight.*
    diagnostic-logs: false             # 排查上报时可临时打开
```

### 与 Prometheus / Micrometer

| 关注点 | 建议 |
|--------|------|
| 链路、拓扑、跨服务调用 | **Spring Insight** |
| JVM / 连接池 / HTTP QPS | **Actuator + Micrometer / Prometheus** |
| Insight 自身指标 | 可选刮取 `spring.insight.*`（默认 `micrometer-enabled=true`） |

---

## 适用边界

Spring Insight 定位是**轻量辅助排查**，不是 OpenTelemetry / SkyWalking 的替代品：

- Span 默认有条数上限；不配落盘时重启会清空（Docker 推荐挂卷 + `file`）
- 无内置告警规则、多租户、完善鉴权等生产级运维能力
- JVM / JDBC 采集默认关闭（实验开关，需自行验证）

欢迎 Issue / PR。

---

## 许可

[Apache License 2.0](LICENSE)

---

如果你也在用 Spring 微服务、觉得这方向有点意思，欢迎 Star。写得不好的地方请直接把 Issue 甩过来。
