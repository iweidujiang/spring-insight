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

<img width="2547" height="543" alt="局部截取_20260824_114112" src="https://github.com/user-attachments/assets/b4076a9a-060e-4918-85f8-3762a8f3b7e5" />


| 能力 | 说明 | 成熟度 |
|------|------|--------|
| HTTP / Feign 调用采集 | Agent 采 Span，批量报到 Server | 能用，边界情况仍在补 |
| 服务依赖拓扑 | 看谁调用了谁（带箭头和次数） | 基础可用 |
| 链路列表 / Trace 详情 | 按 Trace ID 看一次请求里的 Span；详情页带调用时间线（瀑布图） | 基础可用 |
| 错误率粗看 | 有错误调用时统计一下 | 很简陋 |
| 控制台 UI | Vue 写的几个页面，嵌在 Server 里；浅色纸感 + 墨青主题 | 持续改样式中 |

**当前还没有做的**：

- 没有持久化库：数据在 **Server 内存**里，进程重启就没了  
- **尚未发布到 Maven Central**：需要自己 `mvn install` 到本地仓库  
- 不是 OpenTelemetry / SkyWalking 的替代品  
- 没有告警、没有多租户、没有鉴权完善的生产方案  

---

## 长什么样


### 仪表盘

<img width="2536" height="1188" alt="局部截取_20260824_113854" src="https://github.com/user-attachments/assets/fcd56dee-548e-44b4-bddf-1d2380be678c" />




### 服务拓扑

<img width="2520" height="1171" alt="局部截取_20260824_113921" src="https://github.com/user-attachments/assets/f524f35c-7816-4d0f-9725-92de05c52412" />




### 链路追踪

<img width="2535" height="715" alt="局部截取_20260824_113944" src="https://github.com/user-attachments/assets/c2a3914e-898c-4008-b617-c3807e335be7" />


<img width="2542" height="354" alt="局部截取_20260824_114007" src="https://github.com/user-attachments/assets/5a905d59-0b31-46b7-aeb3-ba5f3248600b" />


### 错误分析

<img width="2541" height="605" alt="局部截取_20260824_114045" src="https://github.com/user-attachments/assets/6c8dbaa0-4e8d-4c9b-9743-d8941b7cbba3" />



---

## 怎么跑起来

环境：**JDK 21**、Maven 3.9+。

### 1. 安装到本地仓库

当前为 **`0.1.0-SNAPSHOT`**，尚未上 Maven Central，请先在本仓库安装到本地：

```bash
cd spring-insight
mvn clean install -DskipTests
```

> IDEA 若报「无效的目标发行版: 21」：把 Maven Runner 的 JRE 换成 JDK 21  
> （`Settings → Maven → Runner → JRE`）。

### 2. 启动监测中心

```bash
java -jar insight-server/target/insight-server-0.1.0-SNAPSHOT.jar
```

浏览器打开：<http://localhost:9966/>

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

## 已知局限 / 心里有数

1. Span 存在 Server **内存**，有条数上限，超了会挤掉旧的；**重启 Server = 历史清空**。  
2. UI、拓扑布局、错误分析都还在改，丑和怪的地方请多包涵。  
3. 文档和示例可能落后于代码，以仓库现状为准。  
4. 作者也是边学边写，PR / Issue 都很欢迎。

---

## 以后可能想做的（画饼，随时可能变）

- [ ] 持久化（解决重启就清空数据的问题）  
- [ ] 发到 Maven Central，少一步本地 install  
- [ ] 与 Prometheus / Micrometer 的轻量联动（连接池等指标）  

排期就不写死了，以免变成空头支票。

---

## 许可

[Apache License 2.0](LICENSE)

---

如果你也在用 Spring 微服务、觉得这方向有点意思，欢迎 star 或提意见。  
写得不好的地方直接说就行——我尽量改。
