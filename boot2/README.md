# Spring Insight — Boot 2.7 / Java 8 兼容线

独立于主仓 `spring-insight-parent`（Boot 3.5 / JDK 21）。**insight-server 仍只用主线构建。**

## 构建

```bash
# 建议 JDK 8 或 11
cd boot2
mvn -DskipTests install
```

坐标（业务侧）：

```xml
<dependency>
  <groupId>io.github.iweidujiang</groupId>
  <artifactId>spring-insight-agent-starter-boot2</artifactId>
  <version>0.1.0-boot2-SNAPSHOT</version>
</dependency>
```

## 阶段

见仓库根 README「Boot 2.7 / Java 8 分期」。

- **B0** 骨架：已完成  
- **B1** 核心采集：TraceSpan / TraceContext / Async 上报 / HttpURLConnection Sink / javax MVC 拦截 — **已完成**

业务侧最小配置：

```yaml
spring:
  application:
    name: my-boot2-app
  insight:
    server-url: http://localhost:9966
```
