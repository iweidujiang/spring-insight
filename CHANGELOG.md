# Changelog

## [Unreleased]

### 修复
- 未配置 `spring.application.name` / `spring.insight.service-name` 时不再中断启动，仅 WARN 并跳过本次采集；未配置 `server-url` 时 WARN 且不上报

### 工程
- 本分支独立为仓库根工程（`2.7.x`），父 POM `spring-insight-boot2-parent:0.3.2`
- 版本号去掉 `-boot2` 后缀，与主线同为 `0.3.2`（artifactId 仍为 `*-boot2`）

## [0.3.2] — 待发

### 变更
- 版本号与主线同为 `0.3.2`（不再使用 `0.3.2-boot2`）
- 启动缺省配置行为与上述 Unreleased 修复一致（发出时并入本版）

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
