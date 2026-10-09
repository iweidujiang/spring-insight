# Changelog

## [Unreleased]

## [0.4.1] — 2026-10-09

### 修复
- **Servlet 入口 SERVER Span 必上报**：`HttpRequestInterceptor.afterCompletion` 以请求属性上的 Span 结束并快照上报，不再仅依赖 ThreadLocal `endSpan`（异步派发 / 栈漂移时不再丢失入口操作名，导致列表与瀑布根落成出站 CLIENT）

### 坐标
| 项 | 值 |
|----|-----|
| Starter | `io.github.iweidujiang:spring-insight-agent-starter-boot2:0.4.1` |
| Agent | `io.github.iweidujiang:insight-agent-boot2:0.4.1` |

## [0.4.0] — 2026-09-30

### 新增
- **W3C `traceparent` 跨服务透传**：Servlet / WebFlux 入站提取；RestTemplate / OpenFeign / WebClient / Gateway 出站注入；`spring.insight.http-trace-propagation-enabled`（默认 true）
- **头部采样真正生效**：`spring.insight.sample-rate`（0～1，默认 1）；本地根按概率，入站跟随 flags，子 Span 继承；未采样不上报且出站 flags=`00`
- **CLIENT `remoteService` 规范化**：优先 `lb://` / Feign Target / 非 IP Host 头，避免拓扑边落成 `127.0.0.1`
- TraceId / SpanId 改为 W3C 长度（32 / 16 hex）

### 坐标（已发 Central）
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
