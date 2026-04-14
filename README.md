# Spring Insight

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-21%2B-orange)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.9-green)
](https://spring.io/projects/spring-boot#learn)

**Spring Insight** 是一个面向Spring Boot应用的轻量级诊断与架构洞察工具。它无需复杂配置，即可自动分析你的微服务依赖、识别潜在瓶颈，并生成可视化的健康报告，帮助开发者像架构师一样思考。

> ✨ **核心价值**：让每一个Spring Boot开发者，都能在5分钟内看清自己应用的架构脉络与运行健康度。

## 阶段性成果

> 截图中的数据是mock数据

<img width="1258" height="791" alt="局部截取_20260112_231913(1)" src="https://github.com/user-attachments/assets/7dc10284-da64-4cce-87d6-c98555bc3fd9" />


## 🎯 项目愿景
在云原生时代，可观测性至关重要，但现有方案对中小团队或传统Spring项目往往过于沉重。Spring Insight旨在填补这一空白：
1.  **零学习成本**：完全基于Spring生态，以Starter方式提供。
2.  **开箱即用**：引入依赖，即可获得架构洞察，无需额外部署中间件。
3.  **智能诊断**：超越数据展示，提供架构层面的健康分析与改进建议。

## 🚀 快速开始

### 0. 安装到本机 Maven 仓库

默认本地仓库路径为 **`~/.m2/repository`**（Windows 多为 `%USERPROFILE%\.m2\repository`）。

当前版本（如 `0.1.0-SNAPSHOT`）尚未发布到 **Maven Central**，需要先把工程**编译并安装**到本地仓库，业务项目才能解析依赖。

**仓库定位**：`spring-insight` 是 **独立维护的通用工具库**（自研 Starter + 内部模块），**与任何具体业务工程无编译耦合**；业务方只需在自身项目的 `pom.xml` 中声明 `spring-insight-spring-boot-starter` 即可。

本仓库是 **Maven 多模块**工程，父工程为 `spring-insight-parent`，子模块包括：

| 模块 | 说明 |
|------|------|
| `insight-agent` | 采集与自动配置（Servlet 拦截器 + WebFlux `WebFilter`，Span、JVM 指标等） |
| `insight-collector-service` | **收集器业务层**：批处理、校验、写入存储等，**不依赖** Servlet/WebMVC |
| `insight-collector` | **收集器 HTTP 适配层**：基于 Spring MVC 的 REST / 控制台；依赖 `insight-collector-service` |
| `insight-storage` | 内存存储 |
| `spring-insight-spring-boot-starter` | **对外唯一推荐坐标**：聚合上述模块，供任意 Spring Boot 应用引用 |

**宿主为 WebFlux 时（例如 Spring Cloud Gateway）**：Starter 默认会带上基于 MVC 的 `insight-collector`，从而引入 `spring-boot-starter-web`，与 WebFlux 冲突。做法是：仍依赖 **`spring-insight-spring-boot-starter`**，对 **`insight-collector`** 与 **`spring-boot-starter-web`** 做 Maven `<exclusions>`；**`insight-collector-service` 仍随 Starter 进入 classpath**，进程内上报与内存聚合照常工作，入口 HTTP 追踪由 **`ReactiveInsightWebFilter`** 完成。需要 **Insight 控制台 UI** 时，在**任意一个 Servlet 栈**的服务上保留完整 Starter（不做上述排除）即可。

**无需逐个模块安装。** 在 **`spring-insight` 目录**（与父 `pom.xml` 同级）执行一条命令即可按 reactor 顺序编译并全部 `install` 到本机：

```bash
cd spring-insight
mvn clean install -DskipTests
```

成功后，本地仓库中会有 `io.github.iweidujiang` 下各构件；业务工程在 `pom.xml` 里只声明 **`spring-insight-spring-boot-starter`** 即可（版本与父 POM 的 `<version>` 一致，见下文）。



### 1. 添加依赖
```xml
<dependency>
    <groupId>io.github.iweidujiang</groupId>
    <artifactId>spring-insight-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 2. 启用功能

在启动类或配置类上添加注解 `@EnableSpringInsight`，支持以下配置选项：

```java
@SpringBootApplication
@EnableSpringInsight(
    serviceName = "my-service",              // 服务名称
    serviceInstance = "instance-001",         // 服务实例标识
    sampleRate = 0.8,                          // 采样率（0.0-1.0）
    httpTracingEnabled = true,                 // 启用HTTP请求追踪
    jvmMetricsEnabled = true,                  // 启用JVM指标监控
    dbMetricsEnabled = true                    // 启用数据库调用监控
)
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

**注解属性说明：**
| 属性名 | 类型 | 默认值 | 描述 |
|--------|------|--------|------|
| `enabled` | `boolean` | `true` | 是否启用Spring Insight |
| `serviceName` | `String` | - | 服务名称（必填） |
| `serviceInstance` | `String` | - | 服务实例标识（可选，默认使用 host:port） |
| `sampleRate` | `double` | `1.0` | 采样率（0.0-1.0，1.0表示采样所有请求） |
| `httpTracingEnabled` | `boolean` | `true` | 是否启用HTTP请求追踪 |
| `jvmMetricsEnabled` | `boolean` | `true` | 是否启用JVM指标监控 |
| `dbMetricsEnabled` | `boolean` | `true` | 是否启用数据库调用监控 |

**配置文件方式（application.yml）：**

除了使用注解外，还可以通过配置文件进行配置：

```yaml
spring:
  insight:
    enabled: true
    service-name: my-service
    service-instance: instance-001
    sample-rate: 0.8
    http-tracing-enabled: true
    db-metrics:
      enabled: true
    jvm-metrics:
      enabled: true
      report-interval: 30000  # JVM指标上报间隔，单位毫秒
    # 诊断日志：true 时打印 HTTP 拦截器每次触发、TraceContext 强制清理等（默认 false，避免刷屏）
    diagnostic-logs: false
```

**优先级：** 配置文件 > 注解属性 > 默认值

**日志：** 需要更细粒度时可用标准 Spring 配置，例如 `logging.level.io.github.iweidujiang.springinsight=DEBUG`；日常建议保持 `diagnostic-logs: false`。

### 3. 查看控制台
启动应用后，在浏览器访问应用根路径（端口以 `server.port` 为准），例如：`http://localhost:8080/`  
兼容旧文档入口：`http://localhost:8080/insight-ui`（会重定向到 `/`）。

## 🏗️ 核心功能（第一期规划）

- 自动依赖拓扑图：可视化展示服务、数据库、Redis等组件的调用关系。

- 架构健康报告：标识单点故障、高频慢接口、循环依赖风险。

- HTTP接口分析：统计端点QPS、平均耗时、错误率，并关联至具体代码变更。

## 📊 技术架构

<img width="958" height="529" alt="技术架构图" src="https://github.com/user-attachments/assets/7a32c348-4948-4e49-8df4-00f31bbd61a5" />

## 📅 开发路线图（画饼向）

- **Phase 1 (MVP):** 基础数据采集、拓扑发现与静态报告（~2026.Q2）

- **Phase 2:** 集成OpenTelemetry，增强数据采集能力（~2026.Q3）

- **Phase 3:** 引入智能分析规则与预测（~2026.Q4）

## 🗄️ 链路数据放哪

Trace / Span **只存在当前 JVM 的内存里**（有上限条数，超出会丢最旧的），**不用 JDBC、不配数据源**；进程一重启控制台历史就空。

### 用 H2 存 TraceSpan 能不能「看到所有链路」？

- **仅把 H2 当作「另一种存储介质」时**：可以实现跨重启保留、更大容量，但能否「全量」仍取决于你是否 **采集每一条请求**（采样率、排除路径）以及 **查询接口是否分页/截断**。
- **当前开源实现**：`TraceSpanPersistenceService` 是 **进程内环形缓冲**，**没有**接 H2/MySQL；要接 H2 需要新增 **持久化实现 + 与 Collector 的读写对接**（属于扩展开发，不是改个依赖就能自动生效）。
- **注意**：**内存模式 H2**（`jdbc:h2:mem:`）随进程退出同样会丢数据；需要持久化请用 **文件库**（如 `jdbc:h2:file:./data/insight`）或外部数据库。

## 未完待续...
