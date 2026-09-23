# Changelog

## [Unreleased] — 0.4.0-SNAPSHOT

### 0.3.3（拟定） / 0.3.2 发版补充
- 未配置 `spring.application.name` / `spring.insight.service-name` 时不再中断启动，仅 WARN 并跳过本次采集；未配置 `server-url` 时 WARN 且不上报
- **insight-server 亦可 JDK 17 运行**：Server 字节码目标改为 17；Docker 改用 `temurin:17-jre`；去掉 `List.getFirst` / `removeFirst` 等 JDK 21 API
- Agent 显式依赖 Jackson 2；HTTP 上报内置 `JavaTimeModule`（修复 Instant 序列化）
- 打 `v*` tag 时 Actions **自动**将 `insight-server-x.y.z.jar` 挂到 GitHub Release（无 Docker 可用 `java -jar`，需 JDK 17+）
- 文档明确：正式支持 Boot 3.x + JDK 17+；暂不支持 Boot 4

### 0.4（体验，开发中）
- 链路列表改为请求主舞台：方法、路径和耗时条；点击整行打开详情
- 登录页保持分栏卡片，背景恢复动态链路轨迹
- 仪表盘 / 拓扑对 p50、p95 提供可读说明
- 单体应用进入拓扑页时改为服务概览，不再留空画布
- 多服务有调用边时，仪表盘以拓扑为主舞台，慢服务 / 热点依赖收进窄辅栏
- 链路详情以瀑布为主舞台：摘要细条、选中 Span 进右栏，Span 表默认折叠，AI 结果在瀑布下方
- 仪表盘 / 拓扑 / 链路 / 错误分析：时间范围统一为「近 N 小时」，空状态文案与时间窗口对齐
- 设置页清空 Span：改为纸感确认弹层；无数据时禁用「清空全部」
- 拓扑多服务：画布为主舞台，点击节点/边先选中高亮再旁栏下钻；依赖表默认折叠
- 错误分析：与链路列表对齐（摘要细条、可点行主舞台、墨青图表色）；AI 结果下移

## [0.3.2] — 2026-09-20

### 修复
- **Boot 3 + JDK 17**：Agent / Starter / **insight-server** 字节码目标均为 **Java 17**，修复业务侧 `UnsupportedClassVersionError`；无 Docker 时 `java -jar` 亦可用 JDK 17
- GHCR / Release jar 基于 Java 17；JDK 21+ 业务与 Server 均可继续使用
- CI 增加 Agent / Server class major version = 61 校验

### 兼容性
- **须升级 Central Starter** 至 `0.3.2`（`0.3.0` / 未修复包在 JDK 17 上不可用）
- JDK 21+ 业务应用可继续使用（字节码向上兼容）

### 坐标 / 镜像
| 项 | 值 |
|----|-----|
| Starter | `io.github.iweidujiang:spring-insight-agent-starter:0.3.2` |
| Server 镜像 | `ghcr.io/iweidujiang/spring-insight-server:0.3.2` |

## [0.3.1] — 2026-09-20

### 亮点
- **清除历史 Span**：设置 → 数据；支持清空全部、按时间（早于 N 小时）、按服务清理；均需二次确认
- **存储摘要 API**：`GET /api/v1/ui/storage/summary`、`POST /api/v1/ui/storage/clear`
- **错误分析「一键解读」**：将错误聚合摘要送入已配置的 AI（复用设置页 AI 开关）
- **拓扑边 AI 解读**与进程内调用审计：`GET /api/v1/ui/ai/audit`
- 控制台侧栏分组（观测 / 运维 / 关于）、路由过渡与设计 token；设置页告警 / AI / 数据分节导航

### 清除语义（重要）
- **按服务**：只删除 `serviceName` **精确匹配**的 Span；相关 Trace 可能因此不完整
- 清除不可恢复；建议清理后刷新仪表盘 / 拓扑 / 链路列表

### 兼容性
- **无 Agent 埋点变更**；本版以 **GHCR Server 镜像**为主，可不升 Central Starter
- 未开启 AI 时，一键解读 / 拓扑边解读不可用（与 0.3.0 一致，默认关闭）

### 坐标 / 镜像
| 项 | 值 |
|----|-----|
| Starter | `io.github.iweidujiang:spring-insight-agent-starter:0.3.0`（本版可不升） |
| Server 镜像 | `ghcr.io/iweidujiang/spring-insight-server:0.3.1` |

## [0.3.0] — 2026-09-18

### 亮点
- RestTemplate / RestClient 出站 CLIENT Span（拓扑更完整；HTTP ≥400 与 IO 失败记错误）
- 可选 Webhook 告警，以及自定义 SMTP 邮件（错误率或错误次数阈值，默认关闭）
- 可选「解释 Trace」AI 实验（OpenAI 兼容，含 DeepSeek；默认关闭；失败可降级为仅 Context）
- 控制台「设置」页配置告警与 AI，写入数据目录 `runtime-settings.json`，保存后下一轮扫描或解释即生效

### 兼容性
- 未开启 alert / ai 时，与 0.2.x 默认行为一致
- 仅用新埋点时需升级 Agent；仅用告警 / AI / 设置页时可只升级 Server 镜像
- 启动期 `spring.insight.server.alert.*` / `ai.*` 仍可读；控制台保存后文件覆盖启动默认

### 已知限制
- AI 为实验能力，结论须人工核对 Span，不落库
- 告警不是完整规则引擎；无多通道值班表、无密钥保险箱
- 错误分析页「一键解读」、清除历史 Span 见后续 `0.3.1`

### 坐标 / 镜像
| 项 | 值 |
|----|-----|
| Starter | `io.github.iweidujiang:spring-insight-agent-starter:0.3.0` |
| Server 镜像 | `ghcr.io/iweidujiang/spring-insight-server:0.3.0` |

## [0.2.1] — 2026-09-17

### 修复
- 控制台开启登录后，右上角恢复账户菜单与「退出登录」
- 关于页 / health 版本号改为自动读取（build-info / Manifest），不再写死 `0.1.1-SNAPSHOT`
- 侧栏与功能特性图标：改用 Font Awesome 4 可用类名（此前误用 FA5 名称导致缺图标）

## [0.2.0] — 2026-09-14

### 亮点
- 可选 **SQLite** 存储与按条数/时间的保留策略（`memory` / `file` / `sqlite`）
- 可选 **上报 Token**（`X-Insight-Token` / Bearer）与可选 **控制台登录**（默认均关闭，兼容 0.1.x）
- Server **容量可见**：health / UI stats / Micrometer（stored / max / evicted）
- **错误分析增强**：按 HTTP 状态码与异常类聚合，支持下钻 Trace
- **Trace Context 导出**：`GET /api/v1/ui/traces/{id}/context`（`schemaVersion=1`，不调大模型；详情页可复制）

### 兼容性
- 未开启 ingest Token 时，**0.1.x Agent 仍可上报到 0.2 Server**
- 存储默认仍为 `memory`；换 `sqlite`/`file` 需显式配置；换 mode **不做**自动迁移

### 坐标 / 镜像
| 项 | 值 |
|----|-----|
| Starter | `io.github.iweidujiang:spring-insight-agent-starter:0.2.0` |
| Server 镜像 | `ghcr.io/iweidujiang/spring-insight-server:0.2.0` |


## [0.1.0] — 2026-09

首个正式可用版：Agent Starter + insight-server（GHCR）+ 拓扑 / 链路 / 控制台。
