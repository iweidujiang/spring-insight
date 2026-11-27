<h1 align="center">
  <img src="assets/brand/spring-insight-mark.png" alt="" width="64" align="absmiddle" />
  Spring Insight
</h1>

<p align="center">
  <a href="https://opensource.org/licenses/Apache-2.0"><img src="https://img.shields.io/badge/license-Apache%202.0-blue.svg" alt="License" /></a>
  <a href="https://www.oracle.com/java/"><img src="https://img.shields.io/badge/Agent-JDK%2017%2B-orange" alt="Java" /></a>
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring%20Boot-3.x%20%2F%202.7-brightgreen" alt="Spring Boot" /></a>
  <a href="https://github.com/iweidujiang/spring-insight/releases/tag/v2.1.1"><img src="https://img.shields.io/badge/Server-2.1.1-green.svg" alt="Server" /></a>
  <a href="https://central.sonatype.com/artifact/io.github.iweidujiang/spring-insight-agent-starter/0.7.0"><img src="https://img.shields.io/badge/Agent-0.7.0-blue.svg" alt="Agent" /></a>
</p>

<p align="center"><b>Lightweight APM / distributed tracing for Spring Boot.</b></p>

<p align="center">Spring Boot 轻量链路追踪与性能观测：业务侧一个 Starter，旁边一个监测中心，自带控制台。适合本地开发、联调排查与小规模微服务。</p>

---

## 为什么需要 Spring Insight

SkyWalking、Zipkin、Micrometer Tracing 等方案能力很强，但往往要先搞定采集器、存储和多组件版本对齐。

Spring Insight 更轻：

- **两步上手**：Starter 上报 + 一个 `insight-server`（Docker / jar）
- **链路与拓扑**：谁调了谁、哪段慢、哪里错，点进瀑布图就能看
- **排障闭环**：统一时间窗、错误分析、数据库 / 缓存页、告警可点回控制台
- **个人 / 小团队友好**：不必上重型 APM 也能把请求看清楚

当前发版：**Server `2.1.1`** + **Agent / Starter `0.7.0`**。升到此版监测中心后，可配合 Agent `0.7.0` 看到心跳存活、版本配套与丢弃提示；只升 Server、Agent 仍为 `0.6.x` 时，原有链路上报不受影响。变更详见 [CHANGELOG](CHANGELOG.md)。

---

## 快速开始（两步）

### 1. 启动监测中心

**Docker（推荐）**

```bash
docker run --rm -p 9966:9966 \
  -e SPRING_INSIGHT_SERVER_STORAGE_MODE=file \
  -e SPRING_INSIGHT_SERVER_STORAGE_FILE_PATH=/data/spans.json \
  -v spring-insight-data:/data \
  ghcr.io/iweidujiang/spring-insight-server:2.1.1
```

或在仓库根目录：`docker compose up -d`（默认镜像与上表版本一致）。

**可执行 jar**

从 [Releases](https://github.com/iweidujiang/spring-insight/releases) 下载 `insight-server-2.1.1.jar`（需 JDK 17+）：

```bash
java -jar insight-server-2.1.1.jar
```

浏览器打开 <http://localhost:9966/>。

- 官方 `compose.yaml` 默认**不**开控制台登录
- 本地开发用 `compose.dev.yaml` 时默认 `admin` / `1`（可用 `.env` 改密码或关闭鉴权）
- 告警、AI、SLO 等在控制台 **设置** 页配置

### 2. 业务服务接入

```xml
<dependency>
  <groupId>io.github.iweidujiang</groupId>
  <artifactId>spring-insight-agent-starter</artifactId>
  <version>0.7.0</version>
</dependency>
```

```yaml
spring:
  application:
    name: my-service
  insight:
    server-url: http://localhost:9966
```

造几笔跨服务调用，等几秒后刷新控制台即可。

**可选能力（默认关闭）**

```yaml
spring:
  insight:
    jdbc-tracing-enabled: true          # 慢 SQL / 错误进「数据库」页
    # jdbc-slow-threshold-ms: 500
    # redis-tracing-enabled: true       # 慢命令 / 错误进「缓存」页
    jvm-metrics:
      enabled: true                     # 服务详情看堆 / GC / 线程曲线
```

完整联调可参考示例仓 [spring-insight-sca-demo](https://github.com/iweidujiang/spring-insight-sca-demo)（网关造数如 `/order/create`、`/call-jdbc-slow`）。

Spring Boot **2.7.x** 请使用 Boot2 坐标（`spring-insight-agent-starter-boot2` / `insight-agent-boot2`，版本同为 `0.7.0`），见 Central 与 [CHANGELOG](CHANGELOG.md) Agent 章节。

---

## 核心能力

<img width="2341" height="530" alt="Spring Insight 控制台一览" src="https://github.com/user-attachments/assets/80bbebee-1397-4ebd-abcf-d09d598f0a16" />

| 能力 | 说明 |
|------|------|
| HTTP 采集 | Feign、WebClient、Gateway、RestTemplate、RestClient 等；跨服务透传 Trace |
| 仪表盘 | KPI、时段摘要、健康 / 慢服务、库与缓存卡片、最近请求；无数据时有接入引导 |
| 服务拓扑 | 依赖关系与健康分着色；可选环形 / 力导向 / 层次布局，可按调用量过滤弱边 |
| 链路追踪 | 列表筛选、瀑布图（关键路径、SQL 预览、异常高亮）；可复制含筛选的分享链接；可对比两条 Trace、下载脱敏排障包 |
| 数据库 / 缓存 | JDBC / Redis 追踪（默认关）；SQL 指纹聚合；缓存可按命令汇总；可与链路互相跳转 |
| 错误与告警 | 错误分析；告警历史；钉钉 / 飞书 / 企微 / 邮件 / Webhook，消息可带深链回控制台 |
| 服务与 JVM | 服务列表 / 详情；在线 / 沉默（可合并心跳）；关于页可见推荐 Agent 版本；可选 JVM 运行时曲线 |
| 体验 | 统一时间窗（近 N 小时或自定义起止）；亮 / 暗 / 跟随系统主题 |

---

## 长什么样

### 仪表盘

KPI、时段摘要、健康 / 慢服务、库与缓存、最近请求。

<img width="2315" height="837" alt="仪表盘" src="https://github.com/user-attachments/assets/fb6113f8-74e5-4eff-ba49-13f8f8ee3091" />

### 服务拓扑

调用关系、健康分图例；可切换布局并过滤弱边。

<img width="2287" height="661" alt="服务拓扑" src="https://github.com/user-attachments/assets/e451660f-ec3c-409c-9f76-3fd97b04ecf9" />

### 数据库

按库汇总、SQL 指纹与最近 JDBC；可打开样例链路。

<img width="2214" height="486" alt="数据库" src="https://github.com/user-attachments/assets/030d71c0-2b02-4c8b-bd74-ce52319df075" />

### 缓存

侧栏「缓存」：Redis KPI、按实例聚合与最近命令。

### 链路追踪

请求列表与瀑布详情；可只看关键路径、预览 SQL。

<img width="2281" height="294" alt="链路列表" src="https://github.com/user-attachments/assets/28a87049-c5f9-40bd-9dcf-7621dde343e5" />

<img width="2298" height="677" alt="链路瀑布" src="https://github.com/user-attachments/assets/5bd0ff90-9655-4bc0-a9bd-b768e30bc7c4" />

### 错误分析

按服务与错误类型汇总，便于下钻到链路。

<img width="2300" height="782" alt="错误分析" src="https://github.com/user-attachments/assets/8f408da0-071c-4772-a791-70bcb606207c" />

---

## 架构与版本

```text
业务微服务 × N  ── Starter ──►  埋点、异步批量上报
独立进程 × 1    ── Docker / jar ──►  insight-server :9966（存储 + API + 控制台）
```

| 模块 | 角色 | 当前版本 |
|------|------|----------|
| `spring-insight-agent-starter` | 业务侧依赖（Boot3） | `0.7.0`（Maven Central） |
| `insight-server` | 监测中心（含控制台） | `2.1.1`（GHCR / Release jar） |

Agent 与 Server **分线发版**：控制台与查询能力升 Server 即可；只有埋点 / 协议变更才需要升 Agent。升级说明见 [CHANGELOG](CHANGELOG.md)。

---

## 文档与社区

| 文档 | 说明 |
|------|------|
| [CHANGELOG](CHANGELOG.md) | Server / Agent 版本说明与升级注意 |
| [CONTRIBUTING](CONTRIBUTING.md) | 如何改 Agent / Server / UI、提 Issue / PR |
| [Releases](https://github.com/iweidujiang/spring-insight/releases) | 镜像 tag 与可执行 jar |
| [sca-demo](https://github.com/iweidujiang/spring-insight-sca-demo) | 多服务联调造数示例 |

文章：[掘金专栏](https://juejin.cn/column/7593077945015926811) · [CSDN 专栏](https://blog.csdn.net/iweidujiang/category_13115295.html?fromshare=blogcolumn&sharetype=blogcolumn&sharerId=13115295&sharerefer=PC&sharesource=iweidujiang&sharefrom=from_link) · [微信公众号](https://mp.weixin.qq.com/mp/appmsgalbum?__biz=MzI1MDU1MjkxOQ==&action=getalbum&album_id=4333089336431689750&scene=126&sessionid=#wechat_redirect)

---

## 许可

[Apache License 2.0](LICENSE)

欢迎 Star；问题与建议请开 [Issue](https://github.com/iweidujiang/spring-insight/issues)（请选用模板）。
