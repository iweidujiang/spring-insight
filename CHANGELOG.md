# Changelog

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
- Boot2 同步发版：`0.2.0-boot2`（含 ingest-token 配置）

### 坐标 / 镜像
| 项 | 值 |
|----|-----|
| Boot3 Starter | `io.github.iweidujiang:spring-insight-agent-starter:0.2.0` |
| Boot2 Starter | `io.github.iweidujiang:spring-insight-agent-starter-boot2:0.2.0-boot2` |
| Server 镜像 | `ghcr.io/iweidujiang/spring-insight-server:0.2.0` |


## [0.1.0] — 2026-09

首个正式可用版：Agent Starter + insight-server（GHCR）+ 拓扑 / 链路 / 控制台；Boot2 `0.1.0-boot2`。
