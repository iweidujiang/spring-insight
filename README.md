<h1 align="center">
  <img src="assets/brand/spring-insight-mark.png" alt="" width="64" align="absmiddle" />
  Spring Insight
</h1>

<p align="center">
  <a href="https://opensource.org/licenses/Apache-2.0"><img src="https://img.shields.io/badge/license-Apache%202.0-blue.svg" alt="License" /></a>
  <a href="https://www.oracle.com/java/"><img src="https://img.shields.io/badge/Agent-JDK%2017%2B-orange" alt="Java" /></a>
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring%20Boot-3.5.9-brightgreen" alt="Spring Boot" /></a>
  <a href="https://github.com/iweidujiang/spring-insight/releases/tag/v1.1.2"><img src="https://img.shields.io/badge/Server-1.1.2-green.svg" alt="Server" /></a>
  <a href="https://central.sonatype.com/artifact/io.github.iweidujiang/spring-insight-agent-starter/0.5.1"><img src="https://img.shields.io/badge/Agent-0.5.1-blue.svg" alt="Agent" /></a>
</p>

**Lightweight APM / distributed tracing for Spring Boot.**

Spring Boot 轻量级链路追踪与性能观测：接入简单、零配置起步、自带 UI，适合本地开发、联调排查和小规模微服务观测。

---

## 为什么需要 Spring Insight

SkyWalking、Zipkin、Micrometer Tracing 等方案能力很强，但接入成本往往不低：采集器、存储、协议约定、多服务版本对齐……

Spring Insight 更轻：

- **接入成本低**：业务引入 Starter，旁边跑一个 `insight-server` 即可。
- **链路可视化**：请求列表、瀑布图、调用拓扑。
- **排查友好**：按服务 / 耗时 / 状态筛选，定位慢请求与错误。
- **适合个人 / 小团队**：不必上重型 APM，也能看清请求经过了谁、哪段慢、哪里错。

---

## 快速开始（两步）

### 1. 启动监测中心

**Docker（推荐）**

```bash
docker run --rm -p 9966:9966 \
  -e SPRING_INSIGHT_SERVER_STORAGE_MODE=file \
  -e SPRING_INSIGHT_SERVER_STORAGE_FILE_PATH=/data/spans.json \
  -v spring-insight-data:/data \
  ghcr.io/iweidujiang/spring-insight-server:1.1.2
```

或仓库根目录：`docker compose up -d`

**可执行 jar**

从 [Releases](https://github.com/iweidujiang/spring-insight/releases) 下载 `insight-server-1.1.2.jar`（需 JDK 17+）：

```bash
java -jar insight-server-1.1.2.jar
```

浏览器打开 <http://localhost:9966/>。Compose 默认登录：`admin` / `1`（可用 `.env` 改密码或关闭鉴权）。告警 / AI 等在控制台 **设置** 页配置。

### 2. 业务服务接入

```xml
<dependency>
  <groupId>io.github.iweidujiang</groupId>
  <artifactId>spring-insight-agent-starter</artifactId>
  <version>0.5.1</version>
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

---

## 核心能力

<img width="2341" height="530" alt="局部截取_20260910_151357" src="https://github.com/user-attachments/assets/80bbebee-1397-4ebd-abcf-d09d598f0a16" />

| 能力 | 说明 |
|------|------|
| HTTP 出站 / 入站采集 | Feign、WebClient、Gateway、RestTemplate、RestClient 等；跨线程可透传 Trace |
| JDBC / Redis 追踪（默认关） | 慢 SQL、慢命令与错误上报；控制台「数据库」「缓存」页汇总 |
| 服务拓扑 | 谁调用了谁；点击节点 / 边可下钻链路 |
| 链路与排障包 | Trace 列表、瀑布图；详情可下载脱敏排障包 |
| 仪表盘摘要 | KPI、慢/错 Top、库与缓存卡片 |
| 可选告警 / AI | 设置页配置 Webhook、邮件、OpenAI 兼容模型 |
| 亮/暗色主题 | 右上角一键切换，`localStorage` 持久化；全页面 CSS 变量驱动，ECharts 图表联动 |

---

## 长什么样

### 仪表盘

KPI、时段摘要、健康 / 慢服务、库与缓存卡片、最近请求

<img width="2315" height="837" alt="局部截取_20261013_192623" src="https://github.com/user-attachments/assets/fb6113f8-74e5-4eff-ba49-13f8f8ee3091" />

### 服务拓扑

<img width="2287" height="661" alt="局部截取_20261013_194718" src="https://github.com/user-attachments/assets/e451660f-ec3c-409c-9f76-3fd97b04ecf9" />

### 数据库

<img width="2214" height="486" alt="局部截取_20261013_194742" src="https://github.com/user-attachments/assets/030d71c0-2b02-4c8b-bd74-ce52319df075" />

### 缓存

侧栏「缓存」页：Redis KPI、按实例聚合与最近命令。

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
| `insight-server` | 监测中心（API + 控制台） |

---

## 相关文章

[掘金专栏](https://juejin.cn/column/7593077945015926811) · [CSDN 专栏](https://blog.csdn.net/iweidujiang/category_13115295.html?fromshare=blogcolumn&sharetype=blogcolumn&sharerId=13115295&sharerefer=PC&sharesource=iweidujiang&sharefrom=from_link) · [微信公众号](https://mp.weixin.qq.com/mp/appmsgalbum?__biz=MzI1MDU1MjkxOQ==&action=getalbum&album_id=4333089336431689750&scene=126&sessionid=#wechat_redirect)

## 许可

[Apache License 2.0](LICENSE)

---

欢迎 Star；问题与建议请开 Issue。
