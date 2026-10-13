<h1 align="center">
  <img src="assets/brand/spring-insight-mark.png" alt="" width="64" align="absmiddle" />
  Spring Insight
</h1>

<p align="center">
  <a href="https://opensource.org/licenses/Apache-2.0"><img src="https://img.shields.io/badge/license-Apache%202.0-blue.svg" alt="License" /></a>
  <a href="https://www.oracle.com/java/"><img src="https://img.shields.io/badge/Agent-JDK%2017%2B-orange" alt="Java" /></a>
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring%20Boot-3.5.9-brightgreen" alt="Spring Boot" /></a>
  <a href="https://github.com/iweidujiang/spring-insight/releases/tag/v1.0.0"><img src="https://img.shields.io/badge/Server-1.0.0-green.svg" alt="Server" /></a>
  <a href="https://central.sonatype.com/artifact/io.github.iweidujiang/spring-insight-agent-starter/0.5.0"><img src="https://img.shields.io/badge/Agent-0.5.0-blue.svg" alt="Agent" /></a>
</p>

**Lightweight APM / distributed tracing for Spring Boot.**

Spring Boot 轻量级链路追踪与性能观测工具：接入简单、零配置起步、自带 UI，适合本地开发、联调排查和小规模微服务观测。

[GitHub stars](https://github.com/iweidujiang/spring-insight/stargazers)

---
## 为什么需要 Spring Insight

SkyWalking、Zipkin、Micrometer Tracing 等方案能力很强，但接入成本往往不低：需要配置采集器、对接后端存储、理解 Trace/Span/Metrics 约定，甚至在多服务之间统一版本。

Spring Insight 的定位更轻：

- **接入成本低**：业务服务引入 `spring-insight-agent-starter`，旁边单独跑 `insight-server` 即可开始观测。
- **链路可视化**：自动聚合 Span，生成请求链路列表、瀑布图和调用拓扑。
- **排查友好**：支持按服务、耗时、状态、关键字筛选，快速定位慢请求和错误链路。
- **适合个人项目 / 小团队**：不需要重型 APM 基础设施，也能看清一次请求经过了哪些服务、哪一段变慢、哪里报错。

如果你正在做 Spring Boot 微服务、Agent 链路、Spring AI 调用链，或者只是想给本地项目加一个轻量可观测面板，Spring Insight 可以作为一个更轻的起点。

## 快速开始（两步）

### 1. 启动监测中心

**方式 A — Docker（推荐）**

```bash
docker run --rm -p 9966:9966 \
  -e SPRING_INSIGHT_SERVER_STORAGE_MODE=file \
  -e SPRING_INSIGHT_SERVER_STORAGE_FILE_PATH=/data/spans.json \
  -v spring-insight-data:/data \
  ghcr.io/iweidujiang/spring-insight-server:1.0.0
```

或使用本仓库根目录 Compose（拉取已发布镜像）：

```bash
docker compose up -d
```

**方式 B — 无 Docker（GitHub Release 可执行 jar）**

从 [Releases](https://github.com/iweidujiang/spring-insight/releases) 下载 `insight-server-1.0.0.jar`（打 `v*` tag 时由 Actions 自动挂载），本机需 **JDK 17+**：

```bash
java -jar insight-server-1.0.0.jar
# 落盘示例：
# java -jar insight-server-1.0.0.jar --spring.insight.server.storage.mode=file
```

**本地改 Server 源码联调（本机打包 + Docker 运行镜像，默认 sqlite）：**

```bash
# 仓库根目录；不要在容器里 mvn package（国内拉 Central 插件依赖容易在 repackage 失败）
mvn -pl insight-server -am package -DskipTests
docker compose -f compose.dev.yaml up -d --build
# 换模式：$env:INSIGHT_STORAGE_MODE="file"（PowerShell）或 export INSIGHT_STORAGE_MODE=file
```

浏览器打开：<http://localhost:9966/>  
本地 Compose 默认打开控制台登录：用户名 `admin`，密码 `1`。改密码或关闭：在仓库根目录建 `.env`，设置 `INSIGHT_UI_PASSWORD`，或 `INSIGHT_UI_AUTH_ENABLED=false`，然后重新 `up -d`。  
告警 / AI / Webhook / SMTP 等在控制台侧栏 **「设置」** 页配置（写入数据目录 `runtime-settings.json`，重启不丢）。
### 2. 业务服务接入

```xml
<dependency>
  <groupId>io.github.iweidujiang</groupId>
  <artifactId>spring-insight-agent-starter</artifactId>
  <version>0.5.0</version>
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

## 核心能力

<img width="2341" height="530" alt="局部截取_20260910_151357" src="https://github.com/user-attachments/assets/80bbebee-1397-4ebd-abcf-d09d598f0a16" />


| 能力 | 说明 |
|------|------|
| HTTP / Feign / WebClient / Gateway / RestTemplate / RestClient 采集 | Agent 采 Span 并批量上报；success 与 HTTP 状态对齐；线程池可透传 Trace；WebFlux 走 Reactor Context；出站带 `remoteService` |
| JDBC 追踪（默认关） | `spring.insight.jdbc-tracing-enabled=true` 包装 DataSource；错误 / 慢 SQL（默认 ≥500ms）上报 CLIENT Span；控制台「数据库」页汇总 |
| Redis 追踪（默认关） | `spring.insight.redis-tracing-enabled=true` 包装 RedisConnectionFactory；错误 / 慢命令（默认 ≥500ms）上报 CLIENT Span；控制台「缓存」页汇总 |
| 服务依赖拓扑 | 谁调用了谁（箭头与次数）；点击节点/边可下钻链路 |
| 链路列表 / Trace 详情 | 按 Trace 聚合，可搜索筛选；详情含瀑布时间线、tags、错误信息；可下载脱敏排障包 |
| 延迟与错误摘要 | 仪表盘 KPI / 慢/错 Top（含 p50/p95）与库缓存卡片，可点进已筛 Trace |
| Micrometer 联动 | 宿主有 MeterRegistry 时导出 `spring.insight.*`（Span Timer / 上报队列） |
| 可选鉴权 / 容量可见（0.2） | Server 可选 ingest Token、控制台登录；健康/Actuator 暴露 stored/max/evicted |
| 可选告警 / AI（0.3，默认关） | 控制台「设置」配 Webhook、SMTP、OpenAI 兼容模型；保存后即时生效 |
| 控制台 UI | Vue 页面内嵌在 Server / Docker 镜像中 |

---

## 长什么样

### 仪表盘

KPI、时段摘要、健康/慢服务/热点依赖、库与缓存卡片、最近请求

<img width="2315" height="837" alt="局部截取_20261013_192623" src="https://github.com/user-attachments/assets/fb6113f8-74e5-4eff-ba49-13f8f8ee3091" />



### 服务拓扑
<img width="2287" height="661" alt="局部截取_20261013_194718" src="https://github.com/user-attachments/assets/e451660f-ec3c-409c-9f76-3fd97b04ecf9" />


### 数据库

<img width="2214" height="486" alt="局部截取_20261013_194742" src="https://github.com/user-attachments/assets/030d71c0-2b02-4c8b-bd74-ce52319df075" />


### 缓存

侧栏 **「缓存」**（`/redis`）：按时间窗 / 业务服务查看 Redis KPI、按实例聚合与最近命令，可点进对应 Trace。

### 链路追踪
<img width="2281" height="294" alt="局部截取_20261013_194820" src="https://github.com/user-attachments/assets/28a87049-c5f9-40bd-9dcf-7621dde343e5" />

<img width="2298" height="677" alt="局部截取_20261013_194841" src="https://github.com/user-attachments/assets/5bd0ff90-9655-4bc0-a9bd-b768e30bc7c4" />



### 错误分析

<img width="2300" height="782" alt="局部截取_20261013_194915" src="https://github.com/user-attachments/assets/8f408da0-071c-4772-a791-70bcb606207c" />



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



## 相关文章

[掘金专栏](https://juejin.cn/column/7593077945015926811)

[CSDN 专栏](https://blog.csdn.net/iweidujiang/category_13115295.html?fromshare=blogcolumn&sharetype=blogcolumn&sharerId=13115295&sharerefer=PC&sharesource=iweidujiang&sharefrom=from_link)

[微信公众号](https://mp.weixin.qq.com/mp/appmsgalbum?__biz=MzI1MDU1MjkxOQ==&action=getalbum&album_id=4333089336431689750&scene=126&sessionid=#wechat_redirect)

## 许可

[Apache License 2.0](LICENSE)

---

如果你也在用 Spring 微服务、觉得这方向有点意思，欢迎 Star。写得不好的地方请直接把 Issue 甩过来。
