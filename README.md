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
```

**优先级：** 配置文件 > 注解属性 > 默认值

### 3. 查看报告
启动应用，访问：`http://localhost:8088/insight-ui`

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

## 🗄️ 数据库支持

Spring Insight 支持 **MySQL 数据库**，并提供自动数据库和表创建功能。

### 配置示例
```yaml
spring:
  insight:
    datasource:
      url: jdbc:mysql://localhost:3306/spring_insight?useSSL=false&serverTimezone=UTC
      username: root
      password: 123456
    server:
      port: 8088  # 自定义端口，默认8088
```

**说明：**
- 引入starter后，只需配置MySQL连接信息，Spring Insight会自动创建数据库和表。
- 支持自定义端口，默认端口为8088。
- 所有数据存储在MySQL中，确保数据持久化和可靠性。

## 未完待续...
