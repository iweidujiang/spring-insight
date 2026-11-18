# 贡献指南

感谢关注 Spring Insight。本仓库同时包含 **Agent（业务侧采集）**、**Server（监测中心）** 与 **控制台 UI**。提交前请先分清改动落在哪一条线，便于评审与发版。

## 三条线怎么改

| 线 | 目录 / 产物 | 何时改这里 |
|----|-------------|------------|
| **Agent** | `insight-agent`、`spring-insight-agent-starter` | 埋点、协议字段、Starter 默认行为、采样等 |
| **Server** | `insight-server`（Java API / 存储 / 设置） | 接收、查询、告警、持久化、运维接口 |
| **UI** | `insight-server/ui` | 控制台页面、图表、交互；构建后打进 Server jar |

- 只改 UI / Server 查询展示时，一般**不必**动 Agent。
- 改了 Agent 协议或采集行为时，请说明是否需要配套 Server / UI，以及业务侧如何验证。
- Spring Boot **2.7.x** 的 Agent 对齐在独立分支维护；若你的改动影响 Boot2 用户，请在 PR 里注明，维护者会协助移植。

## 环境

- **JDK 17+**（CI 校验字节码目标为 17）
- **Maven 3.9+**
- 改 UI 时另需 **Node.js 22+** 与 npm（见 `insight-server/ui`）

本地联调可另起 [spring-insight-sca-demo](https://github.com/iweidujiang/spring-insight-sca-demo)（或自有业务）向 `http://localhost:9966` 上报。

## 构建与测试

在仓库根目录：

```bash
# 全量校验（会打包控制台进 Server）
mvn -B -ntp verify

# 只跑某一模块（示例）
mvn -pl insight-server -am test
mvn -pl insight-agent,spring-insight-agent-starter -am test
```

前端单独构建：

```bash
cd insight-server/ui
npm ci
npm run build
```

开发 Server 时也可用根目录 `compose.dev.yaml` 起监测中心；官方 `compose.yaml` 面向已发版镜像。

## Issue

开 Issue 前请先搜是否已有同类问题。新建时请选用模板，并尽量写清：

1. **线**：Agent / Server / UI（可多选）
2. **版本**：Server 镜像或 jar 版本、Agent / Starter 版本、Spring Boot 大版本
3. **复现**：最小步骤、期望与实际；日志请脱敏

安全相关漏洞请勿在公开 Issue 贴利用细节，可先联系维护者。

## Pull Request

1. Fork 后从最新 `main` 拉分支；分支名简短说明意图即可（如 `fix/jdbc-empty-state`）。
2. 单一主题：一个 PR 只解决一类问题，避免混进无关重构。
3. 说明里写清：
   - 动机与用户可感知变化
   - 如何自测（命令或控制台路径 + 预期）
   - 是否需要升 Agent 版本 / 改配置默认值
4. 保持 CI 通过：`mvn verify`；动到 UI 时确保 `insight-server/ui` 可 `npm run build`。
5. 面向用户的行为变化请同步 `CHANGELOG.md` 的 `[Unreleased]`（写能力，不写类名 / 内部路径）。
6. 不要在公开文案或 commit 里堆内部计划编号。

提交信息建议：

```text
feat(server): 简短说明能力
fix(ui): …
feat(agent): …
```

## 行为与许可

- 请遵守 [Apache License 2.0](LICENSE)。
- 贡献代码即表示你同意以相同许可证授权本项目使用。
- 讨论请保持友善、具体；不同意见可以对事不对人。

有疑问可先开 Discussion / Issue 对齐方案，再动手改大块代码。
