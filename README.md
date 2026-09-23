<h1 align="center">
  <img src="assets/brand/spring-insight-mark.png" alt="" width="64" align="absmiddle" />
  Spring Insight
</h1>

<p align="center">
  <a href="https://opensource.org/licenses/Apache-2.0"><img src="https://img.shields.io/badge/license-Apache%202.0-blue.svg" alt="License" /></a>
  <a href="https://www.oracle.com/java/"><img src="https://img.shields.io/badge/Agent-JDK%2017%2B-orange" alt="Java" /></a>
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring%20Boot-3.5.9-brightgreen" alt="Spring Boot" /></a>
  <a href="https://github.com/iweidujiang/spring-insight/releases/tag/v0.4.0"><img src="https://img.shields.io/badge/Server-0.4.0-green.svg" alt="Server" /></a>
  <a href="https://central.sonatype.com/artifact/io.github.iweidujiang/spring-insight-agent-starter/0.3.2"><img src="https://img.shields.io/badge/Agent-0.3.2-blue.svg" alt="Agent" /></a>
</p>

面向 **Spring Boot / Spring Cloud** 的轻量监测工具：**业务侧加一个 Starter 埋点上报，旁边用 Docker 起一个 `insight-server`，就能看服务拓扑和调用链路。**


- 仓库：[https://github.com/iweidujiang/spring-insight](https://github.com/iweidujiang/spring-insight)
- 已发布：**Agent `0.3.2`**（Maven Central）+ **Server `0.4.0`**（GHCR）
- **运行时要求**：Spring Boot 3.x + JDK 17+
- 问题与建议欢迎开 Issue

---

## 快速开始（两步）

### 1. 启动监测中心

**方式 A — Docker（推荐）**

```bash
docker run --rm -p 9966:9966 \
  -e SPRING_INSIGHT_SERVER_STORAGE_MODE=file \
  -e SPRING_INSIGHT_SERVER_STORAGE_FILE_PATH=/data/spans.json \
  -v spring-insight-data:/data \
  ghcr.io/iweidujiang/spring-insight-server:0.4.0
```

或使用本仓库根目录 Compose（拉取已发布镜像）：

```bash
docker compose up -d
```

**方式 B — 无 Docker（GitHub Release 可执行 jar）**

从 [Releases](https://github.com/iweidujiang/spring-insight/releases) 下载 `insight-server-0.4.0.jar`（打 `v*` tag 时由 Actions 自动挂载），本机需 **JDK 17+**：

```bash
java -jar insight-server-0.4.0.jar
# 落盘示例：
# java -jar insight-server-0.4.0.jar --spring.insight.server.storage.mode=file
```

**本地改 Server 源码联调（本机打包 + Docker 运行镜像，默认 sqlite）：**

```bash
# 仓库根目录；不要在容器里 mvn package（国内拉 Central 插件依赖容易在 repackage 失败）
mvn -pl insight-server -am package -DskipTests
docker compose -f compose.dev.yaml up -d --build
# 换模式：$env:INSIGHT_STORAGE_MODE="file"（PowerShell）或 export INSIGHT_STORAGE_MODE=file
```

浏览器打开：<http://localhost:9966/>  
本地 Compose 默认打开控制台登录：用户名 `admin`，密码 `insight`。改密码或关闭：在仓库根目录建 `.env`，设置 `INSIGHT_UI_PASSWORD`，或 `INSIGHT_UI_AUTH_ENABLED=false`，然后重新 `up -d`。  
告警 / AI / Webhook / SMTP 等在控制台侧栏 **「设置」** 页配置（写入数据目录 `runtime-settings.json`，重启不丢）。
### 2. 业务服务接入

```xml
<dependency>
  <groupId>io.github.iweidujiang</groupId>
  <artifactId>spring-insight-agent-starter</artifactId>
  <version>0.3.2</version>
</dependency>
```

```yaml
spring:
  application:
    name: my-service
  insight:
    server-url: http://localhost:9966
```

造几笔跨服务调用，等几秒（Agent 异步批量上报），刷新控制台即可。

---

## 它能做什么

<img width="2341" height="530" alt="局部截取_20260910_151357" src="https://github.com/user-attachments/assets/80bbebee-1397-4ebd-abcf-d09d598f0a16" />


| 能力 | 说明 |
|------|------|
| HTTP / Feign / WebClient / Gateway / RestTemplate / RestClient 采集 | Agent 采 Span 并批量上报；success 与 HTTP 状态对齐；线程池可透传 Trace；WebFlux 走 Reactor Context；出站带 `remoteService` |
| 服务依赖拓扑 | 谁调用了谁（箭头与次数）；点击节点/边可下钻链路 |
| 链路列表 / Trace 详情 | 按 Trace 聚合，可搜索筛选；详情含瀑布时间线、tags、错误信息 |
| 延迟与错误摘要 | 仪表盘慢/错 Top（含 p50/p95），可点进已筛 Trace |
| Micrometer 联动 | 宿主有 MeterRegistry 时导出 `spring.insight.*`（Span Timer / 上报队列） |
| 可选鉴权 / 容量可见（0.2） | Server 可选 ingest Token、控制台登录；健康/Actuator 暴露 stored/max/evicted |
| 可选告警 / AI（0.3，默认关） | 控制台「设置」配 Webhook、SMTP、OpenAI 兼容模型；保存后即时生效 |
| 控制台 UI | Vue 页面内嵌在 Server / Docker 镜像中 |

---

## 长什么样

### 仪表盘

<img width="2341" height="1248" alt="局部截取_20260910_151425" src="https://github.com/user-attachments/assets/5d121a96-3dce-4911-b972-2bf7927e946c" />


### 服务拓扑
<img width="2328" height="1228" alt="局部截取_20260910_151452" src="https://github.com/user-attachments/assets/2e6ba9d2-4bbf-4e67-91ce-93ab89b1a18c" />

### 链路追踪
<img width="2325" height="717" alt="局部截取_20260910_151551" src="https://github.com/user-attachments/assets/83af472c-0919-49e9-bd5d-fcb635a8abd4" />

<img width="2298" height="1192" alt="局部截取_20260910_151611" src="https://github.com/user-attachments/assets/8768b3d3-c13a-48f2-8b2a-ced7fc13080d" />


### 错误分析

<img width="2324" height="967" alt="局部截取_20260910_151633" src="https://github.com/user-attachments/assets/7e019fd9-0520-4e1d-b01e-8d0627b4434a" />


---

## 版本与坐标

Agent 与 Server **版本说明如下：**

| 坐标 | 当前推荐 | 说明 |
|------|----------|------|
| `io.github.iweidujiang:spring-insight-agent-starter` | **`0.3.2`** | **业务侧依赖**（[Central](https://central.sonatype.com/artifact/io.github.iweidujiang/spring-insight-agent-starter/0.3.2)） |
| `io.github.iweidujiang:insight-agent` | **`0.3.2`** | 采集核心（由 Starter 传递） |
| `ghcr.io/iweidujiang/spring-insight-server` | **`0.4.0`** | **监测中心镜像** |
| Release `insight-server-*.jar` | 同上 | **无 Docker**：JDK 17+ 下 `java -jar` |

仓库根 `pom.xml` 属性：`agent.version` / `server.version`。发版说明见 [CHANGELOG.md](CHANGELOG.md)。

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
| `insight-server` | 监测中心（API + 控制台 UI；前端源码在 `insight-server/ui`） |
| `compose.yaml` | 一键起监测中心 |

---

## 存储与其它启动方式

- **Docker / Compose** 默认 `file` 落盘并挂卷，重启可保留 Span。
- 仅内存：将 `SPRING_INSIGHT_SERVER_STORAGE_MODE` 设为 `memory`。
- **SQLite（0.2）**：`SPRING_INSIGHT_SERVER_STORAGE_MODE=sqlite`，库文件默认 `/data/insight.db`（需挂卷）。
- 按时间保留（可选）：`SPRING_INSIGHT_SERVER_STORAGE_RETENTION_MAX_AGE_HOURS=72`。
- **可选鉴权 / 运维**：见 [`docs/dev_docs/v0.2-ops.md`](docs/dev_docs/v0.2-ops.md)（ingest Token、UI 登录、容量指标；默认关闭）。
- **告警 / AI**：优先在控制台「设置」页开关；亦可启动期写 `spring.insight.server.alert.*` / `ai.*`（页面保存后文件覆盖启动默认）。
- 本机从源码打 jar（构建与运行均需 JDK 17+）：

```bash
mvn clean install -DskipTests
java -jar insight-server/target/insight-server.jar
```

仅改控制台时，可在 `insight-server/ui` 下 `npm run dev`（默认代理到本机 9966）。

正式发版后优先从 GitHub Release 下载 `insight-server-x.y.z.jar`，无需自行编译。

存储只在 **Server** 侧配置，业务微服务不要配 `spring.insight.server.storage.*`。  
健康检查：`GET /api/v1/health`（含 `storageMode`、`storedSpans`）。

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
- 告警为简易阈值 + Webhook/邮件；无多租户、OIDC、密钥保险箱（可选 Token / 简易登录见 0.2 运维文档）
- JVM / JDBC 采集默认关闭（实验开关，需自行验证）

欢迎 Issue / PR。

---

## 许可

[Apache License 2.0](LICENSE)

---

如果你也在用 Spring 微服务、觉得这方向有点意思，欢迎 Star。写得不好的地方请直接把 Issue 甩过来。
