# Spring Insight — Boot 2.7 / Java 8 兼容线

独立于主仓 `spring-insight-parent`（Boot 3.5 / JDK 21）。**insight-server 仍只用主线构建。**

## 业务侧坐标（B3 固化）

| GAV | 说明 |
|-----|------|
| `io.github.iweidujiang:spring-insight-agent-starter-boot2:0.1.0-boot2-SNAPSHOT` | **业务请只依赖这个** |
| `io.github.iweidujiang:insight-agent-boot2:0.1.0-boot2-SNAPSHOT` | 采集核心（Starter 传递依赖，一般不用直接引） |

```xml
<dependency>
  <groupId>io.github.iweidujiang</groupId>
  <artifactId>spring-insight-agent-starter-boot2</artifactId>
  <version>0.1.0-boot2-SNAPSHOT</version>
</dependency>
```

> 尚未发 Maven Central：需先本地 `install`。与主线正式版 `0.1.0` **版本号不同**，避免坐标冲突。

## 构建

```bash
# 建议 JDK 8 或 11（也可用更高 JDK 交叉编译到 1.8）
cd boot2
mvn -DskipTests install
```

## 最小配置

```yaml
spring:
  application:
    name: my-boot2-app
  insight:
    server-url: http://localhost:9966
```

| 配置项 | 默认 | 说明 |
|--------|------|------|
| `spring.insight.enabled` | `true` | 总开关 |
| `spring.insight.server-url` | — | insight-server 根地址 |
| `spring.insight.service-name` | 回退 `spring.application.name` | 上报服务名 |
| `spring.insight.http-tracing-enabled` | `true` | MVC / WebFlux SERVER；WebClient / Gateway CLIENT |
| `spring.insight.micrometer-enabled` | `true` | 桥接宿主 MeterRegistry（需 classpath 有 Micrometer 且存在 Bean） |
| `spring.insight.diagnostic-logs` | `false` | 请求级诊断日志 |

能力摘要：Servlet MVC SERVER Span、OpenFeign CLIENT Span（`remoteService` 优先 `@FeignClient` name）、HttpURLConnection 批量上报、可选 Micrometer（`spring.insight.*`）、WebFlux 入口 SERVER Span、WebClient 出站 CLIENT Span、Spring Cloud Gateway 出站 CLIENT Span（`remoteService` 优先 `lb://` 服务名）。

## 阶段

| 阶段 | 内容 | 状态 |
|------|------|------|
| B0 | 独立父 POM、`spring.factories` 骨架 | 完成 |
| B1 | 核心采集（javax + HttpURLConnection） | 完成 |
| B2 | Feign CLIENT + demo | 完成 |
| B3 | Starter 坐标固化 + Boot2.7 冒烟 | 完成 |
| B4 | Micrometer 桥 + WebFlux/WebClient | 完成 |
| **B5** | Gateway 出站 CLIENT Span（`remoteService`） | **完成** |

## 冒烟演示

同级工程：`spring-insight-boot2-demo`（路径示例：`D:\a-github-project\spring-insight-boot2-demo`）。

```bash
# 1) install 本兼容线
cd D:\a-github-project\spring-insight\boot2 && mvn -DskipTests install

# 2) 启动主线 insight-server:9966（另开终端）
java -jar D:\a-github-project\spring-insight\insight-server\target\insight-server-0.1.1-SNAPSHOT.jar

# 3) 启动 demo，并冒烟
cd D:\a-github-project\spring-insight-boot2-demo
.\scripts\smoke-check.ps1
# 起 provider / consumer 后：
.\scripts\smoke-check.ps1 -HitEndpoints
```

期望控制台 http://localhost:9966/ 出现 `boot2-demo-consumer → boot2-demo-provider` 拓扑边。
