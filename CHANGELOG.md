# Changelog

## [Unreleased]

### 新增
- **W3C `traceparent` 跨服务透传**：Servlet 入站提取；RestTemplate / OpenFeign / WebClient / **Gateway** 出站注入；`spring.insight.http-trace-propagation-enabled`（默认 true）
- TraceId / SpanId 改为 W3C 长度（32 / 16 hex）
- （待补）WebFlux 入站提取

### 工程
- 开发中版本升至 **`0.4.0`**（与主线 Agent 对齐；artifactId 仍为 `*-boot2`）

## [0.4.0] — 开发中

### 坐标（发 Central 前用本地 `mvn install`）
| 项 | 值 |
|----|-----|
| Starter | `io.github.iweidujiang:spring-insight-agent-starter-boot2:0.4.0` |
| Agent | `io.github.iweidujiang:insight-agent-boot2:0.4.0` |

## [0.3.2] — 待发 / 已与主线数字对齐

### 变更
- 版本号与主线同为 `0.3.2`（不再使用 `0.3.2-boot2`）
- 本分支独立为仓库根工程（`2.7.x`），父 POM `spring-insight-boot2-parent`
- 未配置 service-name / server-url 时的 WARN 行为与主线一致

### 坐标
| 项 | 值 |
|----|-----|
| Starter | `io.github.iweidujiang:spring-insight-agent-starter-boot2:0.3.2` |
| Agent | `io.github.iweidujiang:insight-agent-boot2:0.3.2` |

## [0.3.0-boot2] — 已发 Central

### 亮点
- RestTemplate 出站 CLIENT Span（须走 `RestTemplateBuilder`）
- 与同期监测中心协议对齐

### 坐标
| 项 | 值 |
|----|-----|
| Starter | `io.github.iweidujiang:spring-insight-agent-starter-boot2:0.3.0-boot2` |

## [0.2.0-boot2] / [0.1.0-boot2]

早期 Boot 2.7 Agent 发版；细节见历史 tag。
