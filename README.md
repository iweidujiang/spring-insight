# Spring Insight

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-21%2B-orange)](https://www.oracle.com/java/)

**Spring Insight** 是一个面向Spring Boot应用的轻量级诊断与架构洞察工具。它无需复杂配置，即可自动分析你的微服务依赖、识别潜在瓶颈，并生成可视化的健康报告，帮助开发者像架构师一样思考。

> ✨ **核心价值**：让每一个Spring Boot开发者，都能在5分钟内看清自己应用的架构脉络与运行健康度。

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
    <artifactId>spring-insight-core-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 2. 启用功能
在启动类或配置类上添加注解：`@EnableSpringInsight`

### 3. 查看报告
启动应用，访问：`http://localhost:8080/insight-ui`

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

## 未完待续...