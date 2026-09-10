# Spring Insight

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-21%2B-orange)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.9-brightgreen)](https://spring.io/projects/spring-boot)
[![Version](https://img.shields.io/badge/version-0.1.0-green.svg)](https://github.com/iweidujiang/spring-insight)

一个还在摸索中的、面向 **Spring Boot / Spring Cloud** 微服务的轻量监测小工具。最新已发布版本为 **`0.1.0`**（Maven Central；主线 Boot 3.5 / JDK 21）。仓库开发中版本为 **`0.1.1-SNAPSHOT`**。

我重新学习了一下其他类似的 APM 工具，重新认识了一下这个项目，有点惭愧——说实话，它谈不上「可观测性平台」，目前只是：**业务侧加一个 Starter 埋点上报，旁边单独跑一个 `insight-server` 看拓扑和链路**。能力有限，界面也还粗糙，先能用、能改，再慢慢补。

如果你手头项目不大、又不想一开始就上整套 APM，可以当个练手/辅助排查的玩具试试；有问题或建议，欢迎开 Issue。

---

## 版本说明

最新已发布：**`0.1.0`**（[Maven Central](https://central.sonatype.com/artifact/io.github.iweidujiang/spring-insight-agent-starter/0.1.0)）。业务侧请优先用正式版坐标；本仓库当前开发版本为 **`0.1.1-SNAPSHOT`**（需本地 `mvn clean install`）。

业务侧依赖示例：

```xml
<dependency>
  <groupId>io.github.iweidujiang</groupId>
  <artifactId>spring-insight-agent-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

| Maven 模块（GAV 摘要） | 角色 |
|------|------|
| `io.github.iweidujiang:spring-insight-parent:0.1.0` | 父 POM（主线 Boot 3.5 / JDK 21） |
| `io.github.iweidujiang:insight-agent:0.1.0` | 采集核心 |
| `io.github.iweidujiang:spring-insight-agent-starter:0.1.0` | **Boot 3 业务侧请依赖这个** |
| `io.github.iweidujiang:insight-server:0.1.0` | 监测中心可执行包 |
| `io.github.iweidujiang:spring-insight-agent-starter-boot2:0.1.0-boot2` | **Boot 2.7 / Java 8 业务侧请依赖这个**（见 `boot2/`） |

---

## 它现在能做什么

<img width="2327" height="516" alt="局部截取_20260907_210948" src="https://github.com/user-attachments/assets/5ddd8b7d-880f-4ffc-b2cb-59b0fdf6f53c" />



| 能力 | 说明 | 成熟度 |
|------|------|--------|
| HTTP / Feign / WebClient / Gateway 采集 | Agent 采 Span，批量报到 Server；success 与 HTTP 状态对齐；线程池可透传 Trace；WebFlux 入口走 Reactor Context；WebClient/Gateway 出站带 remoteService | 能用，持续补齐 |
| Micrometer 轻联动 | 宿主有 MeterRegistry 时导出 `spring.insight.*`（Span Timer / 上报队列）；连接池等仍走 Actuator | 基础可用 |
| 服务依赖拓扑 | 看谁调用了谁（带箭头和次数）；点击节点/边可下钻链路 | 基础可用 |
| 链路列表 / Trace 详情 | 按 Trace 聚合列表（可搜/筛）；详情页瀑布时间线 + Span tags/错误 | 持续打磨中 |
| 错误率粗看 | 有错误调用时统计一下 | 很简陋 |
| 服务延迟摘要 | 仪表盘慢/错 Top（p50/p95），可点进已筛 Trace | 基础可用 |
| 控制台 UI | Vue 写的几个页面，嵌在 Server 里 | 持续改样式中 |

**当前还没有做的**：

- 默认仍是**内存存储**（重启清空）；可选打开 JSON 文件落盘，见下方「可选：Span 落盘」  
- 主线正式版 **`0.1.0`** 已发 Maven Central；开发中为 `0.1.1-SNAPSHOT`；Boot2 正式版 **`0.1.0-boot2`** 已发 Central（见 `boot2/`，开发中 `0.1.1-boot2-SNAPSHOT`）  
- 不是 OpenTelemetry / SkyWalking 的替代品  
- 没有告警、没有多租户、没有鉴权完善的生产方案  
- **JVM 指标 / JDBC Aspect 默认关闭**（Server 暂无 JVM 落库 UI；普通 AOP 难以稳定拦 `java.sql.*`）。需要时显式：

```yaml
spring:
  insight:
    jvm-metrics:
      enabled: true   # 实验：上报后 Server 仍可能仅 debug 丢弃
    db-metrics:
      enabled: true   # 实验：请自行验证是否采到 DB Span
```
---

## 长什么样


### 仪表盘

<img width="2328" height="1251" alt="局部截取_20260907_210738" src="https://github.com/user-attachments/assets/9855277a-4790-4697-bca8-082ae1dbec31" />



### 服务拓扑

<img width="2313" height="1232" alt="局部截取_20260907_210519" src="https://github.com/user-attachments/assets/7a95aed7-9491-4cc0-b4fe-ccc00b1dab8c" />

### 链路追踪

<img width="2317" height="717" alt="局部截取_20260907_210813" src="https://github.com/user-attachments/assets/f1dedae5-f44f-4089-a6b3-fbad85f172d7" />
<img width="2341" height="1211" alt="局部截取_20260907_210850" src="https://github.com/user-attachments/assets/65b01e60-4225-48b7-8d45-e84fc37835d4" />



### 错误分析

<img width="2544" height="625" alt="局部截取_20260904_182715" src="https://github.com/user-attachments/assets/69243f02-13c7-4aee-bc0f-04de7721d373" />




---

## 怎么跑起来

业务侧：**JDK 21** + 从 Maven Central 拉 Starter。监测中心：**推荐 Docker**（不必本机再 `java -jar`）。

### 1. 启动监测中心（推荐 Docker）

**方式 A — 一行（GHCR 镜像）**

```bash
docker run --rm -p 9966:9966 \
  -e SPRING_INSIGHT_SERVER_STORAGE_MODE=file \
  -e SPRING_INSIGHT_SERVER_STORAGE_FILE_PATH=/data/spans.json \
  -v spring-insight-data:/data \
  ghcr.io/iweidujiang/spring-insight-server:0.1.0
```

**方式 B — 本仓库 Compose（同样默认拉 GHCR）**

```bash
cd spring-insight
docker compose up -d
```

浏览器打开：<http://localhost:9966/>

> 镜像由 GitHub Actions 在推送主线 tag（如 `v0.1.0`）或手动 workflow **Publish Server Image** 时推到 GHCR。  
> 若尚未拉取到 `0.1.0` 镜像，可先本地构建：  
> `docker build -f insight-server/Dockerfile -t ghcr.io/iweidujiang/spring-insight-server:local .`  
> 再设 `INSIGHT_SERVER_IMAGE=ghcr.io/iweidujiang/spring-insight-server:local` 后 `docker compose up -d`。

#### 可选：Span 落盘（重启可恢复）

Compose / 上面的 `docker run` 已默认 `file` + 数据卷。纯内存可把 `SPRING_INSIGHT_SERVER_STORAGE_MODE` 改成 `memory`。

等价配置（`java -jar` 或挂载外部 yml 时）：

```yaml
spring:
  insight:
    server:
      storage:
        mode: file
        max-spans: 50000
        file-path: ./data/spans.json
        flush-delay-ms: 2000
```

也可：`--spring.insight.server.storage.mode=file`。  
目录会自动创建；Docker 里建议挂卷到 `/data`。  
这是 **Server 单点配置**，业务微服务不要配。  
健康检查：`GET /api/v1/health`（含 `storageMode`、`storedSpans`）。

#### 备选：本机 `java -jar`

```bash
cd spring-insight
mvn clean install -DskipTests   # 主线需 JDK 21；会构建 UI 进 server jar
java -jar insight-server/target/insight-server-0.1.1-SNAPSHOT.jar
```

### 2. 业务服务接入

**业务侧（推荐）**：直接依赖 Central 坐标，无需克隆本仓库。

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
    name: my-service          # 会当作 Insight 的 serviceName
  insight:
    server-url: http://localhost:9966
```

造几笔跨服务调用后，等几秒（Agent 异步批量上报），再刷控制台。

#### 开发期：让业务应用顺带拉起 Server（可选）

Spring Boot 3.1+ 可加依赖 `spring-boot-docker-compose`，在业务工程放一份指向 Insight 的 compose（或复用本仓库 `compose.yaml` 片段），本地 `bootRun` 时自动起容器——类似起 Zipkin 的体验。生产仍建议单独部署监测中心。

### 3. 演示工程

另有独立仓库 [spring-insight-sca-demo](https://github.com/iweidujiang/spring-insight-sca-demo)（Nacos + 几个微服务）。  
Demo 把 Insight 当成**第三方依赖**使用；监测中心也可改为上述 GHCR 镜像，而不必再挂本地 jar。

---

## 仓库里有什么

```text
业务微服务 × N  ──依赖──►  spring-insight-agent-starter（埋点、上报）
独立进程 × 1    ────────►  insight-server :9966（Docker / jar；存数据 + API + 控制台）
```

| 模块 | 角色 |
|------|------|
| `insight-agent` | 采集核心（`…:insight-agent:0.1.0`） |
| `spring-insight-agent-starter` | **业务侧请依赖这个**（`…:spring-insight-agent-starter:0.1.0`） |
| `insight-server` | 监测中心（镜像 `ghcr.io/iweidujiang/spring-insight-server`；或本地 jar） |
| `insight-ui-vue` | 前端；构建结果会放进 server 的 `static/` / 镜像 |
| `compose.yaml` | 一键起监测中心（默认拉 GHCR） |






---

## 配置备忘（常用）

```yaml
spring:
  insight:
    enabled: true
    server-url: http://localhost:9966
    sample-rate: 1.0
    http-tracing-enabled: true
    context-propagation-enabled: true  # @Async / 线程池透传 Trace；冲突时可关
    micrometer-enabled: true           # 有 MeterRegistry 时导出 spring.insight.*；可关
    diagnostic-logs: false   # 排查上报问题时可临时打开
```

优先级大致是：配置文件 > 注解（若用了）> `spring.application.name`（仅服务名）> 默认值。

---

## 已知局限

1. 默认 Span 在 Server **内存**，有条数上限，超了会挤掉旧的；**重启 = 清空**。需要时可开 `spring.insight.server.storage.mode=file` 做 JSON 落盘（轻量，不是数据库）。  
2. UI、拓扑布局、错误分析都还在改，丑和怪的地方请多包涵。  
3. 文档和示例可能落后于代码，以仓库现状为准。  
4. 作者也是边学边写，PR / Issue 都很欢迎。

---

## 以后可能想做的（画饼，随时可能变）

- [x] 可选文件持久化（默认仍内存；`mode=file` 落盘）  
- [x] WebFlux Reactor Context + Gateway / WebClient 出站 CLIENT Span  
- [x] 与 Prometheus / Micrometer 的轻量联动（Span 耗时 + 上报队列；连接池仍走 Actuator）  
- [ ] 发到 Maven Central，少一步本地 install  
- [x] 兼容 **Boot 2.7 / Java 8 ** 

#### Boot 2.7 / Java 8 分期（`boot2/` 独立工程，不进主 reactor）

| 阶段 | 内容 | 状态 |
|------|------|------|
| B0 | 独立父 POM（Boot 2.7.x + Java 8）、`spring.factories` 装配骨架 | 完成 |
| B1 | 核心：`TraceSpan` / `TraceContext` / HTTP 入口 / `HttpInsightBatchSink`（`javax.servlet`，Java8 用 HttpURLConnection） | 完成 |
| B2 | Feign CLIENT + `remoteService` 拓扑边；配套 `spring-insight-boot2-demo` | 完成 |
| B3 | 固化坐标 `spring-insight-agent-starter-boot2`；配置元数据；Boot 2.7 demo 冒烟脚本 | 完成 |
| B4 | Micrometer 桥；WebFlux 入口 + WebClient 出站 CLIENT Span（Boot2） | 完成 |
| B5 | Gateway 出站 CLIENT Span（`remoteService` / `lb://`） | 完成 |

原则：主线已发布 `0.1.0`（Boot 3.5 + JDK 21）；仓库继续 `0.1.1-SNAPSHOT`；兼容线单独版本/artifact。

排期就不写死了，以免变成空头支票。当前主线仍是 **Spring Boot 3.5 + JDK 21**。

### 与 Prometheus / Micrometer 怎么分工

| 关注点 | 建议 |
|--------|------|
| 链路、拓扑、跨服务调用 | **Spring Insight**（Span → insight-server） |
| JVM / 连接池 / HTTP 服务端 QPS | **Spring Boot Actuator + Micrometer/Prometheus** |
| Insight 自身健康 | 可选：宿主启用 Actuator 后刮取 `spring.insight.*`（`micrometer-enabled`，默认开） |

业务侧示例（已有 Actuator 时无需额外依赖，agent 已 optional 编译 Micrometer）：

```yaml
spring:
  insight:
    micrometer-enabled: true   # 默认 true；无 MeterRegistry 时自动跳过
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus,metrics
```

常见指标名：`spring.insight.span`（Timer，含 `span.kind` / `remote.service` / `success`）、`spring.insight.spans.accepted`、`spring.insight.reporter.queue.size`。

---

## 许可

[Apache License 2.0](LICENSE)

---

如果你也在用 Spring 微服务、觉得这方向有点意思，欢迎 star 或提意见。  
写得不好的地方请直接将issue甩我脸上——我将感激不尽。
