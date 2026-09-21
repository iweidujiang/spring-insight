# Spring Insight（Spring Boot 2.7）

面向 **Spring Boot 2.7 / Java 8+** 的轻量监测 Agent：业务侧加一个 Starter，把 Span 上报到监测中心，即可查看服务拓扑与调用链路。

- 仓库分支：`2.7.x`
- 当前版本：**`0.3.2`**
- 监测中心（Docker / jar）请使用同仓库 **`main`** 分支发布的 `insight-server`（与本 Agent 协议兼容）

---

## 快速开始

### 1. 依赖

```xml
<dependency>
  <groupId>io.github.iweidujiang</groupId>
  <artifactId>spring-insight-agent-starter-boot2</artifactId>
  <version>0.3.2</version>
</dependency>
```

| GAV | 说明 |
|-----|------|
| `spring-insight-agent-starter-boot2` | **业务请只依赖这个** |
| `insight-agent-boot2` | 采集核心（Starter 传递依赖） |

> Maven Central 若尚未同步 `0.3.2`，可先用已发布的 `0.3.0-boot2`，或本分支 `mvn install` 后使用本地包。已发出的旧包版本仍带 `-boot2` 后缀，新版本起与主线数字一致。

### 2. 配置

```yaml
spring:
  application:
    name: my-boot2-app
  insight:
    server-url: http://localhost:9966
```

未配置 `spring.application.name` / `spring.insight.service-name` 时不会中断启动，仅打 WARN 并跳过采集；未配置 `server-url` 时 WARN，Span 不上报。

| 配置项 | 默认 | 说明 |
|--------|------|------|
| `spring.insight.enabled` | `true` | 总开关 |
| `spring.insight.server-url` | — | 监测中心根地址 |
| `spring.insight.service-name` | 回退 `spring.application.name` | 上报服务名 |
| `spring.insight.http-tracing-enabled` | `true` | MVC / WebFlux SERVER；WebClient / Gateway CLIENT |
| `spring.insight.micrometer-enabled` | `true` | 桥接宿主 MeterRegistry |
| `spring.insight.diagnostic-logs` | `false` | 请求级诊断日志 |

### 3. 启动监测中心

```bash
docker run --rm -p 9966:9966 \
  ghcr.io/iweidujiang/spring-insight-server:0.3.2
```

或从 [Releases](https://github.com/iweidujiang/spring-insight/releases) 下载 `insight-server-*.jar`，JDK 17+ 执行 `java -jar`。

---

## 能力

Servlet MVC SERVER Span、OpenFeign CLIENT（`remoteService` 优先 `@FeignClient` name）、RestTemplate CLIENT（须 `RestTemplateBuilder`）、HttpURLConnection 批量上报、可选 Micrometer（`spring.insight.*`）、WebFlux 入口、WebClient / Gateway 出站 CLIENT。

---

## 构建

```bash
# 建议 JDK 8 或 11（也可用更高 JDK 交叉编译到 1.8）
mvn -DskipTests install
```

发布干跑（PowerShell 给 `-D` 加引号）：

```powershell
mvn -Prelease clean verify "-DskipTests" "-Dgpg.skip=true"
```

---

## 冒烟演示

同级工程示例：`spring-insight-boot2-demo`。

```bash
mvn -DskipTests install
# 另开终端启动 insight-server:9966 后启动 demo，访问业务接口，刷新控制台
```

发版说明见 [CHANGELOG.md](CHANGELOG.md)。
