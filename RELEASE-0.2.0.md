# 发版操作清单：0.2.0

> 代码侧版本号、README、Compose、CHANGELOG 已改到 `0.2.0` / `0.2.0-boot2`。  
> **下面步骤需你在本机执行**（含密钥、Central、打 tag、GitHub Release）。

---

## 0. 发版前自检（约 5～10 分钟）

### 0.1 工作区干净

```powershell
cd D:\a-github-project\spring-insight
git status
git pull origin main
```

确认发版相关改动（版本号 / README / CHANGELOG / compose）已提交，或按第 1 节一起提交。  
**不要**把 `target/`、`.env`、含 Token 的本地 override 提交上去。

### 0.2 本地构建与测试

```powershell
$env:JAVA_HOME = "D:\Java\jdk-21.0.4"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

# 主线（会构建 UI 进 Server；无 Node 时加 "-Dskip.ui=true" 仅测后端）
mvn clean verify

# Boot2（建议 JDK 11）
cd boot2
mvn clean verify "-DskipTests"   # 或去掉 skip 跑全量
cd ..
```

### 0.3 密钥与 Central（你已配过 0.1.0 则可跳过核对）

| 项 | 检查 |
|----|------|
| GPG | `gpg --list-secret-keys` 有签名密钥 |
| Maven | `%USERPROFILE%\.m2\settings.xml` 里 `<server><id>central</id>...` 为 Sonatype Portal Token |
| 网络 | 能访问 `central.sonatype.com` |

签名若需口令：在 deploy 时用 `"-Dgpg.passphrase=你的口令"`（PowerShell 必须给带点的 `-D` 加引号）。

---

## 1. 提交发版准备提交（若尚未提交）

```powershell
cd D:\a-github-project\spring-insight
git add pom.xml insight-agent/pom.xml insight-server/pom.xml spring-insight-agent-starter/pom.xml `
  boot2/pom.xml boot2/insight-agent-boot2/pom.xml boot2/spring-insight-agent-starter-boot2/pom.xml `
  README.md boot2/README.md compose.yaml CHANGELOG.md RELEASE-0.2.0.md
git status
git commit -m "$( @'
chore(release): 准备 0.2.0 / 0.2.0-boot2 发版

'@ )"
git push origin main
```

---

## 2. 发布 Maven Central（主线 Boot3）

在仓库根目录：

```powershell
cd D:\a-github-project\spring-insight
$env:JAVA_HOME = "D:\Java\jdk-21.0.4"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

# 正式发布（会 GPG 签名；PowerShell 注意引号）
mvn -Prelease clean deploy "-Dskip.ui=true"
```

- 成功后到 [Central Publishing Portal](https://central.sonatype.com/publishing) 查看 Deployment，必要时 **Publish**。  
- 验证（通常数分钟～数小时后可搜到）：  
  https://central.sonatype.com/artifact/io.github.iweidujiang/spring-insight-agent-starter/0.2.0  

**注意：** 同版本号不可覆盖；失败修完必须改版本或走 Portal 丢弃草稿后再发。

---

## 3. 发布 Maven Central（Boot2）

```powershell
cd D:\a-github-project\spring-insight\boot2
$env:JAVA_HOME = "D:\Java\jdk-11"   # 按你本机 JDK 11/8 路径改
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

mvn -Prelease clean deploy "-DskipTests"
```

验证：  
https://central.sonatype.com/artifact/io.github.iweidujiang/spring-insight-agent-starter-boot2/0.2.0-boot2  

---

## 4. 打 Git Tag 并推送（触发 GHCR）

主线 tag 会触发 `.github/workflows/publish-server-image.yml`，推送：

- `ghcr.io/iweidujiang/spring-insight-server:0.2.0`
- `ghcr.io/iweidujiang/spring-insight-server:latest`

```powershell
cd D:\a-github-project\spring-insight

# 确认当前 main 已是发版提交
git log -1 --oneline

git tag -a v0.2.0 -m "Spring Insight 0.2.0"
git tag -a v0.2.0-boot2 -m "Spring Insight 0.2.0-boot2"

git push origin v0.2.0
git push origin v0.2.0-boot2
```

然后打开 GitHub → **Actions** → **Publish Server Image**，确认 `v0.2.0` 任务成功（`v0.2.0-boot2` 会被 workflow 跳过，属正常）。

若 Actions 失败，可在 Actions 里对 **Publish Server Image** 使用 **Run workflow**，`image_tag` 填 `0.2.0` 手动重推。

包可见性：首次若拉取 403，到 GitHub → Packages → `spring-insight-server` → Package settings → 设为 **Public**。

---

## 5. 创建 GitHub Release（网页）

1. 打开：https://github.com/iweidujiang/spring-insight/releases/new  
2. **Choose tag**：`v0.2.0`  
3. **Release title**：`Spring Insight 0.2.0`  
4. 描述可直接粘贴 `CHANGELOG.md` 里 `## [0.2.0]` 整节  
5. 勾选 Publish release  

（可选）再为 `v0.2.0-boot2` 建一条较短 Release，说明「Boot2 Agent 坐标，Server 仍用主线镜像」。

本机若已安装 [GitHub CLI](https://cli.github.com/)：

```powershell
gh release create v0.2.0 --title "Spring Insight 0.2.0" --notes-file CHANGELOG.md
```

---

## 6. 发版后：把仓库改回 SNAPSHOT（下一迭代）

```powershell
cd D:\a-github-project\spring-insight
# 主线 → 0.2.1-SNAPSHOT
(Get-Content pom.xml -Raw) -replace '>0\.2\.0<','>0.2.1-SNAPSHOT<' | Set-Content pom.xml -Encoding utf8
(Get-Content insight-agent/pom.xml -Raw) -replace '>0\.2\.0<','>0.2.1-SNAPSHOT<' | Set-Content insight-agent/pom.xml -Encoding utf8
(Get-Content insight-server/pom.xml -Raw) -replace '>0\.2\.0<','>0.2.1-SNAPSHOT<' | Set-Content insight-server/pom.xml -Encoding utf8
(Get-Content spring-insight-agent-starter/pom.xml -Raw) -replace '>0\.2\.0<','>0.2.1-SNAPSHOT<' | Set-Content spring-insight-agent-starter/pom.xml -Encoding utf8

# Boot2 → 0.2.1-boot2-SNAPSHOT
(Get-Content boot2/pom.xml -Raw) -replace '>0\.2\.0-boot2<','>0.2.1-boot2-SNAPSHOT<' | Set-Content boot2/pom.xml -Encoding utf8
(Get-Content boot2/insight-agent-boot2/pom.xml -Raw) -replace '>0\.2\.0-boot2<','>0.2.1-boot2-SNAPSHOT<' | Set-Content boot2/insight-agent-boot2/pom.xml -Encoding utf8
(Get-Content boot2/spring-insight-agent-starter-boot2/pom.xml -Raw) -replace '>0\.2\.0-boot2<','>0.2.1-boot2-SNAPSHOT<' | Set-Content boot2/spring-insight-agent-starter-boot2/pom.xml -Encoding utf8

git add pom.xml insight-*/pom.xml spring-insight-agent-starter/pom.xml boot2/**/pom.xml boot2/pom.xml
git commit -m "chore: 发版后开发版本升至 0.2.1-SNAPSHOT / 0.2.1-boot2-SNAPSHOT"
git push origin main
```

（也可用 IDE / 搜索替换；提交前 `git diff` 确认只改了版本号。）

---

## 7. 联调 Demo（可选）

在 `spring-insight-sca-demo`：

1. `pom.xml` 里 `spring.insight.version` → `0.2.0`  
2. 本地：`mvn -pl spring-insight-agent-starter -am install` 已不需要（改拉 Central）  
3. **重建** Demo 镜像（勿只 recreate），避免继续打进旧的 `0.1.0` agent  
4. Server：`docker pull ghcr.io/iweidujiang/spring-insight-server:0.2.0` 后按 README 启动  

冒烟：

```powershell
curl.exe -sS http://localhost:9966/api/v1/health
curl.exe -sS "http://localhost:8080/order/create?userId=1&productId=1"
# 打开 http://localhost:9966/ ，看拓扑 / 错误分析 / 某条 Trace 的「复制 Context」
```

---

## 顺序小结

```text
自检 verify → 提交并 push 0.2.0 准备提交
    → mvn -Prelease deploy（主线）
    → cd boot2 && mvn -Prelease deploy
    → git tag v0.2.0 / v0.2.0-boot2 && push tags
    → 等 GHCR Actions 绿
    → GitHub Release
    → 升回 SNAPSHOT 并 push
    →（可选）更新 sca-demo
```
