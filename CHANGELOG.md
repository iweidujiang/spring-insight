# Changelog



| 线 | 产物 | 发版渠道 | 何时升版 |
|----|------|----------|----------|
| **Agent** | `insight-agent`、`spring-insight-agent-starter`（及 Boot2 对应 artifact） | Maven Central | 埋点、协议、Starter 行为变更 |
| **Server** | `insight-server`（含控制台 `ui/`） | GHCR 镜像、GitHub Release jar | UI、API、存储、运维能力变更 |



---

# Server

## [Unreleased]

### 新增 / 体验
- **时段洞察（开发中）**：近 N 小时事实摘要与上一窗环比（`GET /api/v1/ui/insights/period`）

---

### 新增 / 体验
- **告警附带 AI 解读**（默认关）：开启后 Webhook / 邮件附带 `aiSummary` / `aiSuggestions`；失败不阻断推送；每小时调用上限可配（默认 10）
- 设置页 AI：增加「告警附带 AI 解读」开关与每小时上限

### 兼容
- **Agent 可不升**：业务侧继续使用 Central `spring-insight-agent-starter:0.3.2`
- 未开启 `attachToAlerts` 时，告警 payload 与 0.5.x 一致

### 坐标 / 镜像
| 项 | 值 |
|----|-----|
| 配套 Starter | `0.3.2`（本版可不升） |
| Server 镜像 | `ghcr.io/iweidujiang/spring-insight-server:0.6.0` |

---

## [0.5.0] — 2026-09-24

### 新增 / 体验
- **AI 结构化解读（schemaVersion=1）**：Trace / 错误分析 / 拓扑边统一返回 `summary` + `evidence`（可跳转）+ `suggestions`；仍保留 `markdown` 兼容
- 控制台 AI 面板：展示结论与建议；证据可点回 Span / 链路列表 / 拓扑边
- AI 审计记录附带结论摘要（进程内，重启清空）

### 兼容
- **Agent 可不升**：业务侧继续使用 Central `spring-insight-agent-starter:0.3.2` 上报到本版 Server
- 正式支持 Boot 3.x + JDK 17+；暂不支持 Boot 4

### 坐标 / 镜像
| 项 | 值 |
|----|-----|
| 配套 Starter | `0.3.2`（本版可不升） |
| Server 镜像 | `ghcr.io/iweidujiang/spring-insight-server:0.5.0` |

---

## [0.4.0] — 2026-09-23

### 新增 / 体验
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
- 设置页：顶部卡片分节（告警推送 / AI 解读 / 数据清理）带说明；数据清理表单对齐为行动卡片

### 仓库 / 构建
- **版本分线**：`agent.version` 与 `server.version` 拆开；无 Agent 变更时可只升 Server
- 控制台前端迁入 `insight-server/ui`（不再单独顶层 `insight-ui-vue`）

### 兼容
- **Agent 可不升**：业务侧继续使用 Central `spring-insight-agent-starter:0.3.2` 上报到本版 Server
- 正式支持 Boot 3.x + JDK 17+；暂不支持 Boot 4

### 坐标 / 镜像
| 项 | 值 |
|----|-----|
| 配套 Starter | `0.3.2`（本版可不升） |
| Server 镜像 | `ghcr.io/iweidujiang/spring-insight-server:0.4.0` |

---

## [0.3.2] — 2026-09-20

### 修复
- **insight-server 亦可 JDK 17 运行**：Server 字节码目标改为 17；Docker 改用 `temurin:17-jre`；去掉 `List.getFirst` / `removeFirst` 等 JDK 21 API
- GHCR / Release jar 基于 Java 17；JDK 21+ 可继续使用
- CI 增加 Server class major version = 61 校验

### 坐标 / 镜像
| 项 | 值 |
|----|-----|
| 配套 Starter | `0.3.2`（须与 Agent 同升，见 Agent 节） |
| Server 镜像 | `ghcr.io/iweidujiang/spring-insight-server:0.3.2` |

---

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

### 兼容
- **无 Agent 埋点变更**；本版以 GHCR Server 为主，可不升 Central Starter（仍可用 `0.3.0`）
- 未开启 AI 时，一键解读 / 拓扑边解读不可用（默认关闭）

### 坐标 / 镜像
| 项 | 值 |
|----|-----|
| 配套 Starter | `0.3.0`（本版可不升） |
| Server 镜像 | `ghcr.io/iweidujiang/spring-insight-server:0.3.1` |

---

## [0.3.0] — 2026-09-18

### 亮点
- 可选 Webhook 告警，以及自定义 SMTP 邮件（错误率或错误次数阈值，默认关闭）
- 可选「解释 Trace」AI 实验（OpenAI 兼容，含 DeepSeek；默认关闭；失败可降级为仅 Context）
- 控制台「设置」页配置告警与 AI，写入数据目录 `runtime-settings.json`，保存后下一轮扫描或解释即生效

### 兼容
- 未开启 alert / ai 时，与 0.2.x 默认行为一致
- 仅用告警 / AI / 设置页时可只升级 Server；新出站埋点见 **Agent 0.3.0**
- 启动期 `spring.insight.server.alert.*` / `ai.*` 仍可读；控制台保存后文件覆盖启动默认

### 已知限制
- AI 为实验能力，结论须人工核对 Span，不落库
- 告警不是完整规则引擎；无多通道值班表、无密钥保险箱
- 错误分析页「一键解读」、清除历史 Span 见 Server `0.3.1`

### 坐标 / 镜像
| 项 | 值 |
|----|-----|
| 配套 Starter | `0.3.0` |
| Server 镜像 | `ghcr.io/iweidujiang/spring-insight-server:0.3.0` |

---

## [0.2.1] — 2026-09-17

### 修复
- 控制台开启登录后，右上角恢复账户菜单与「退出登录」
- 关于页 / health 版本号改为自动读取（build-info / Manifest），不再写死 `0.1.1-SNAPSHOT`
- 侧栏与功能特性图标：改用 Font Awesome 4 可用类名（此前误用 FA5 名称导致缺图标）

---

## [0.2.0] — 2026-09-14

### 亮点
- 可选 **SQLite** 存储与按条数/时间的保留策略（`memory` / `file` / `sqlite`）
- 可选 **上报 Token**（`X-Insight-Token` / Bearer）与可选 **控制台登录**（默认均关闭，兼容 0.1.x）
- Server **容量可见**：health / UI stats / Micrometer（stored / max / evicted）
- **错误分析增强**：按 HTTP 状态码与异常类聚合，支持下钻 Trace
- **Trace Context 导出**：`GET /api/v1/ui/traces/{id}/context`（`schemaVersion=1`，不调大模型；详情页可复制）

### 兼容
- 未开启 ingest Token 时，**0.1.x Agent 仍可上报到 0.2 Server**
- 存储默认仍为 `memory`；换 `sqlite`/`file` 需显式配置；换 mode **不做**自动迁移

### 坐标 / 镜像
| 项 | 值 |
|----|-----|
| 配套 Starter | `0.2.0` |
| Server 镜像 | `ghcr.io/iweidujiang/spring-insight-server:0.2.0` |

---

## [0.1.0] — 2026-09

首个正式可用监测中心：Span 接收、存储、拓扑 / 链路 / 控制台（GHCR）。

---

# Agent


---

## [0.3.2] — 2026-09-20

### 修复
- **Boot 3 + JDK 17**：Agent / Starter 字节码目标为 **Java 17**，修复业务侧 `UnsupportedClassVersionError`
- 显式依赖 Jackson 2；HTTP 上报内置 `JavaTimeModule`（修复 Instant 序列化失败）
- 未配置 `spring.application.name` / `spring.insight.service-name` 时不再中断启动，仅 WARN 并跳过本次采集；未配置 `server-url` 时 WARN 且不上报
- CI 增加 Agent class major version = 61 校验

### 兼容性
- **须升级 Central Starter** 至 `0.3.2`（`0.3.0` / 未修复包在 JDK 17 上不可用）
- JDK 21+ 业务应用可继续使用（字节码向上兼容）
- 可上报到 Server `0.3.2` 及后续 `0.4.x`（协议兼容）

### 坐标
| 项 | 值 |
|----|-----|
| Starter | `io.github.iweidujiang:spring-insight-agent-starter:0.3.2` |
| Boot2 Starter | `io.github.iweidujiang:spring-insight-agent-starter-boot2:0.3.2`（分支 `2.7.x`） |

---

## [0.3.0] — 2026-09-18

### 亮点
- RestTemplate / RestClient 出站 CLIENT Span（拓扑更完整；HTTP ≥400 与 IO 失败记错误）

### 兼容性
- 未使用新出站埋点时，与 0.2.x Agent 行为接近
- 要用新埋点须升级本 Starter；仅用 Server 告警 / AI 时可只升 Server

### 坐标
| 项 | 值 |
|----|-----|
| Starter | `io.github.iweidujiang:spring-insight-agent-starter:0.3.0` |

---

## [0.2.0] — 2026-09-14

与当时 Server `0.2.0` 同号发版的业务侧 Starter（上报协议兼容 0.2 Server）。

### 坐标
| 项 | 值 |
|----|-----|
| Starter | `io.github.iweidujiang:spring-insight-agent-starter:0.2.0` |

---

## [0.1.0] — 2026-09

首个正式可用 Starter：HTTP / Feign 等埋点与批量上报。
