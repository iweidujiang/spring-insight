# Spring Insight

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-21%2B-orange)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.9-brightgreen)](https://spring.io/projects/spring-boot)
[![Version](https://img.shields.io/badge/version-0.1.0--SNAPSHOT-orange.svg)](https://github.com/iweidujiang/spring-insight)

一个还在摸索中的、面向 **Spring Boot / Spring Cloud** 微服务的轻量监测小工具。当前开发版本为 **`0.1.0-SNAPSHOT`**，尚未发版。

我重新学习了一下其他类似的 APM 工具，重新认识了一下这个项目，有点惭愧——说实话，它谈不上「可观测性平台」，目前只是：**业务侧加一个 Starter 埋点上报，旁边单独跑一个 `insight-server` 看拓扑和链路**。能力有限，界面也还粗糙，先能用、能改，再慢慢补。

如果你手头项目不大、又不想一开始就上整套 APM，可以当个练手/辅助排查的玩具试试；有问题或建议，欢迎开 Issue，我尽量看。

---

## 版本说明

当前 Maven 坐标为 **`0.1.0-SNAPSHOT`**。**尚未发布到 Maven Central**，接入方需要先 `mvn clean install` 到本地仓库。

业务侧依赖示例：

```xml
<dependency>
  <groupId>io.github.iweidujiang</groupId>
  <artifactId>spring-insight-agent-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

| Maven 模块（GAV 摘要） | 角色 |
|------|------|
| `io.github.iweidujiang:spring-insight-parent:0.1.0-SNAPSHOT` | 父 POM |
| `io.github.iweidujiang:insight-agent:0.1.0-SNAPSHOT` | 采集核心 |
| `io.github.iweidujiang:spring-insight-agent-starter:0.1.0-SNAPSHOT` | **业务侧请依赖这个** |
| `io.github.iweidujiang:insight-server:0.1.0-SNAPSHOT` | 监测中心可执行包 |

---

## 它现在能做什么




| 能力 | 说明 | 成熟度 |
|------|------|--------|
| HTTP / Feign 调用采集 | Agent 采 Span，批量报到 Server | 能用，边界情况仍在补 |
| 服务依赖拓扑 | 看谁调用了谁（带箭头和次数） | 基础可用 |
| 链路列表 / Trace 详情 | 按 Trace 聚合列表（可搜/筛）；详情页瀑布时间线 + Span tags/错误 | 持续打磨中 |
| 错误率粗看 | 有错误调用时统计一下 | 很简陋 |
| 服务延迟摘要 | 仪表盘慢/错 Top（p50/p95），可点进已筛 Trace | 基础可用 |
| 控制台 UI | Vue 写的几个页面，嵌在 Server 里；浅色纸感 + 墨青主题 | 持续改样式中 |

**当前还没有做的**：

- 默认仍是**内存存储**（重启清空）；可选打开 JSON 文件落盘，见下方「可选：Span 落盘」  
- **尚未发布到 Maven Central**：需要自己 `mvn install` 到本地仓库  
- 不是 OpenTelemetry / SkyWalking 的替代品  
- 没有告警、没有多租户、没有鉴权完善的生产方案  

---

## 长什么样


### 仪表盘

<img width="2558" height="1255" alt="局部截取_20260904_182526" src="https://github.com/user-attachments/assets/5d0d53b4-5654-4632-b675-7b31a9f26969" />





### 服务拓扑

<img width="2524" height="1158" alt="局部截取_20260904_182613" src="https://github.com/user-attachments/assets/3742cc8b-f369-441b-9371-87d320db7c8f" />





### 链路追踪

<img width="2533" height="750" alt="局部截取_20260904_182635" src="https://github.com/user-attachments/assets/6b60fcb8-ff84-4645-bf7b-b08e2306fa97" />


<img width="2521" height="1177" alt="局部截取_20260904_193044" src="https://github.com/user-attachments/assets/f7fda1bb-6d6b-4364-b566-1a4c838dbf37" />




### 错误分析

<img width="2544" height="625" alt="局部截取_20260904_182715" src="https://github.com/user-attachments/assets/69243f02-13c7-4aee-bc0f-04de7721d373" />




---

## 怎么跑起来

环境：**JDK 21**、Maven 3.9+。

### 1. 安装到本地仓库

当前为 **`0.1.0-SNAPSHOT`**，尚未上 Maven Central，请先在本仓库安装到本地：

```bash
cd spring-insight
mvn clean install -DskipTests
```


### 2. 启动监测中心

```bash
java -jar insight-server/target/insight-server-0.1.0-SNAPSHOT.jar
```

浏览器打开：<http://localhost:9966/>

#### 可选：Span 落盘（重启可恢复）

默认 `memory`，进程一关数据就没了。需要跨重启保留时，改成 `file`：

```yaml
# application.yml 或同名外部配置
spring:
  insight:
    server:
      storage:
        mode: file
        max-spans: 50000
        file-path: ./data/spans.json
        flush-delay-ms: 2000
```

也可启动参数：`--spring.insight.server.storage.mode=file`。  
目录会自动创建，一般**不必**手工 `mkdir`；文件在 **insight-server 进程工作目录**下的 `./data/spans.json`（Docker 里可挂卷到固定路径）。  
这是 **Server 单点配置**，与业务微服务无关——微服务不要配这个属性。  
健康检查 `GET /api/v1/health` 会带上 `storageMode` 与 `storedSpans`。

### 3. 业务服务接入

```xml
<dependency>
  <groupId>io.github.iweidujiang</groupId>
  <artifactId>spring-insight-agent-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
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

### 演示工程

另有独立仓库 [spring-insight-sca-demo](https://github.com/iweidujiang/spring-insight-sca-demo)（Nacos + 几个微服务）。  
Demo 把 Insight 当成**第三方依赖**使用：自己 `mvn install` 好 Insight，再按 Demo 的 README / `.env` 启动即可。

---

## 仓库里有什么

```text
业务微服务 × N  ──依赖──►  spring-insight-agent-starter（埋点、上报）
独立进程 × 1    ────────►  insight-server :9966（存一点数据 + API + 控制台）
```

| 模块 | 角色 |
|------|------|
| `insight-agent` | 采集核心（`…:insight-agent:0.1.0-SNAPSHOT`） |
| `spring-insight-agent-starter` | **业务侧请依赖这个**（`…:spring-insight-agent-starter:0.1.0-SNAPSHOT`） |
| `insight-server` | 监测中心可执行包（`insight-server-0.1.0-SNAPSHOT.jar`） |
| `insight-ui-vue` | 前端源码（非 Maven 模块）；构建结果拷进 server 的 `static/` |

架构：

<img width="1215" height="1009" alt="局部截取_20260824_152022" src="https://github.com/user-attachments/assets/b9910396-714c-4147-bd57-801a85e94021" />




---

## 配置备忘（常用）

```yaml
spring:
  insight:
    enabled: true
    server-url: http://localhost:9966
    sample-rate: 1.0
    http-tracing-enabled: true
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
- [ ] 发到 Maven Central，少一步本地 install  
- [ ] 与 Prometheus / Micrometer 的轻量联动（连接池等指标）  

排期就不写死了，以免变成空头支票。

---

## 许可

[Apache License 2.0](LICENSE)

---

如果你也在用 Spring 微服务、觉得这方向有点意思，欢迎 star 或提意见。  
写得不好的地方请直接将issue甩我脸上——我将感激不尽。
