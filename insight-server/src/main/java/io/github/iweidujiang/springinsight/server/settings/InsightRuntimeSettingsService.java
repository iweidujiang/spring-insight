package io.github.iweidujiang.springinsight.server.settings;

import io.github.iweidujiang.springinsight.server.config.InsightServerAiProperties;
import io.github.iweidujiang.springinsight.server.config.InsightServerAlertProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 运行时设置：文件覆盖启动期 Properties；供告警 / AI / 控制台读写。
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Slf4j
@Service
public class InsightRuntimeSettingsService {

    private final InsightRuntimeSettingsStore store;
    private final InsightServerAlertProperties bootAlert;
    private final InsightServerAiProperties bootAi;

    private final AtomicReference<InsightRuntimeSettings> current = new AtomicReference<>();

    /**
     * @param store     文件存储
     * @param bootAlert 启动期告警配置
     * @param bootAi    启动期 AI 配置
     */
    public InsightRuntimeSettingsService(InsightRuntimeSettingsStore store,
                                         InsightServerAlertProperties bootAlert,
                                         InsightServerAiProperties bootAi) {
        this.store = store;
        this.bootAlert = bootAlert;
        this.bootAi = bootAi;
    }

    /**
     * 启动时加载文件，否则用 boot 默认。
     */
    @PostConstruct
    void init() {
        InsightRuntimeSettings fromFile = store.loadOrNull();
        if (fromFile != null) {
            current.set(fromFile);
            log.info("[设置] 已加载运行时配置: {}", store.settingsPath().toAbsolutePath());
        } else {
            current.set(fromBoot());
            log.info("[设置] 无 runtime-settings.json，使用启动默认（可在控制台「设置」页保存）");
        }
    }

    /**
     * @return 当前生效的完整设置（含密钥，仅内部用）
     */
    public InsightRuntimeSettings snapshot() {
        return deepCopy(current.get());
    }

    /**
     * @return 转为可调用的告警 Properties
     */
    public InsightServerAlertProperties effectiveAlert() {
        return toAlertProperties(current.get().getAlert());
    }

    /**
     * @return 转为可调用的 AI Properties
     */
    public InsightServerAiProperties effectiveAi() {
        return toAiProperties(current.get().getAi());
    }

    /**
     * 控制台 GET：密钥不回传明文。
     *
     * @return 脱敏视图
     */
    public Map<String, Object> publicView() {
        InsightRuntimeSettings s = current.get();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("settingsPath", store.settingsPath().toAbsolutePath().toString());
        body.put("alert", publicAlert(s.getAlert()));
        body.put("ai", publicAi(s.getAi()));
        return body;
    }

    /**
     * 控制台 PUT：空密码 / 空 apiKey 表示保留原值。
     *
     * @param patch 客户端提交的设置（可含空密钥）
     * @return 保存后的脱敏视图
     */
    public Map<String, Object> updateAndApply(InsightRuntimeSettings patch) {
        if (patch == null) {
            throw new IllegalArgumentException("settings 不能为空");
        }
        InsightRuntimeSettings merged = deepCopy(current.get());
        if (patch.getAlert() != null) {
            merged.setAlert(mergeAlert(merged.getAlert(), patch.getAlert()));
        }
        if (patch.getAi() != null) {
            merged.setAi(mergeAi(merged.getAi(), patch.getAi()));
        }
        store.save(merged);
        current.set(merged);
        return publicView();
    }

    private InsightRuntimeSettings fromBoot() {
        InsightRuntimeSettings s = new InsightRuntimeSettings();
        s.setAlert(fromAlertProperties(bootAlert));
        s.setAi(fromAiProperties(bootAi));
        return s;
    }

    private static InsightRuntimeSettings.AlertSettings mergeAlert(
            InsightRuntimeSettings.AlertSettings base,
            InsightRuntimeSettings.AlertSettings patch) {
        InsightRuntimeSettings.AlertSettings out = deepCopyAlert(base);
        out.setEnabled(patch.isEnabled());
        out.setWebhookUrl(nullToEmpty(patch.getWebhookUrl()));
        out.setMetric(StringUtils.hasText(patch.getMetric()) ? patch.getMetric() : out.getMetric());
        out.setThreshold(patch.getThreshold());
        out.setWindowMinutes(patch.getWindowMinutes());
        out.setCooldownMinutes(patch.getCooldownMinutes());
        if (patch.getEmail() != null) {
            InsightRuntimeSettings.EmailSettings e = deepCopyEmail(out.getEmail());
            InsightRuntimeSettings.EmailSettings p = patch.getEmail();
            e.setEnabled(p.isEnabled());
            e.setHost(nullToEmpty(p.getHost()));
            e.setPort(p.getPort() > 0 ? p.getPort() : e.getPort());
            e.setUsername(nullToEmpty(p.getUsername()));
            // 空密码：保留原授权码
            if (StringUtils.hasText(p.getPassword())) {
                e.setPassword(p.getPassword());
            }
            e.setFrom(nullToEmpty(p.getFrom()));
            e.setTo(nullToEmpty(p.getTo()));
            e.setStartTls(p.isStartTls());
            e.setSsl(p.isSsl());
            out.setEmail(e);
        }
        return out;
    }

    private static InsightRuntimeSettings.AiSettings mergeAi(
            InsightRuntimeSettings.AiSettings base,
            InsightRuntimeSettings.AiSettings patch) {
        InsightRuntimeSettings.AiSettings out = deepCopyAi(base);
        out.setEnabled(patch.isEnabled());
        out.setProvider(StringUtils.hasText(patch.getProvider()) ? patch.getProvider() : out.getProvider());
        out.setBaseUrl(nullToEmpty(patch.getBaseUrl()));
        if (StringUtils.hasText(patch.getApiKey())) {
            out.setApiKey(patch.getApiKey());
        }
        out.setModel(StringUtils.hasText(patch.getModel()) ? patch.getModel() : out.getModel());
        out.setTimeoutMs(patch.getTimeoutMs() > 0 ? patch.getTimeoutMs() : out.getTimeoutMs());
        out.setMaxInputSpans(patch.getMaxInputSpans() > 0 ? patch.getMaxInputSpans() : out.getMaxInputSpans());
        out.setMaxTokens(patch.getMaxTokens() > 0 ? patch.getMaxTokens() : out.getMaxTokens());
        return out;
    }

    private static Map<String, Object> publicAlert(InsightRuntimeSettings.AlertSettings a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", a.isEnabled());
        m.put("webhookUrl", nullToEmpty(a.getWebhookUrl()));
        m.put("metric", a.getMetric());
        m.put("threshold", a.getThreshold());
        m.put("windowMinutes", a.getWindowMinutes());
        m.put("cooldownMinutes", a.getCooldownMinutes());
        InsightRuntimeSettings.EmailSettings e = a.getEmail() != null ? a.getEmail() : new InsightRuntimeSettings.EmailSettings();
        Map<String, Object> email = new LinkedHashMap<>();
        email.put("enabled", e.isEnabled());
        email.put("host", nullToEmpty(e.getHost()));
        email.put("port", e.getPort());
        email.put("username", nullToEmpty(e.getUsername()));
        email.put("passwordConfigured", StringUtils.hasText(e.getPassword()));
        email.put("from", nullToEmpty(e.getFrom()));
        email.put("to", nullToEmpty(e.getTo()));
        email.put("startTls", e.isStartTls());
        email.put("ssl", e.isSsl());
        m.put("email", email);
        return m;
    }

    private static Map<String, Object> publicAi(InsightRuntimeSettings.AiSettings a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", a.isEnabled());
        m.put("provider", nullToEmpty(a.getProvider()));
        m.put("baseUrl", nullToEmpty(a.getBaseUrl()));
        m.put("apiKeyConfigured", StringUtils.hasText(a.getApiKey()));
        m.put("model", nullToEmpty(a.getModel()));
        m.put("timeoutMs", a.getTimeoutMs());
        m.put("maxInputSpans", a.getMaxInputSpans());
        m.put("maxTokens", a.getMaxTokens());
        return m;
    }

    static InsightRuntimeSettings.AlertSettings fromAlertProperties(InsightServerAlertProperties p) {
        InsightRuntimeSettings.AlertSettings a = new InsightRuntimeSettings.AlertSettings();
        a.setEnabled(p.isEnabled());
        a.setWebhookUrl(nullToEmpty(p.getWebhookUrl()));
        a.setMetric(p.getMetric());
        a.setThreshold(p.getThreshold());
        a.setWindowMinutes(p.getWindowMinutes());
        a.setCooldownMinutes(p.getCooldownMinutes());
        InsightServerAlertProperties.Email pe = p.getEmail() != null ? p.getEmail() : new InsightServerAlertProperties.Email();
        InsightRuntimeSettings.EmailSettings e = new InsightRuntimeSettings.EmailSettings();
        e.setEnabled(pe.isEnabled());
        e.setHost(nullToEmpty(pe.getHost()));
        e.setPort(pe.getPort());
        e.setUsername(nullToEmpty(pe.getUsername()));
        e.setPassword(nullToEmpty(pe.getPassword()));
        e.setFrom(nullToEmpty(pe.getFrom()));
        e.setTo(nullToEmpty(pe.getTo()));
        e.setStartTls(pe.isStartTls());
        e.setSsl(pe.isSsl());
        a.setEmail(e);
        return a;
    }

    static InsightRuntimeSettings.AiSettings fromAiProperties(InsightServerAiProperties p) {
        InsightRuntimeSettings.AiSettings a = new InsightRuntimeSettings.AiSettings();
        a.setEnabled(p.isEnabled());
        a.setProvider(p.getProvider());
        a.setBaseUrl(p.getBaseUrl());
        a.setApiKey(nullToEmpty(p.getApiKey()));
        a.setModel(p.getModel());
        a.setTimeoutMs(p.getTimeoutMs());
        a.setMaxInputSpans(p.getMaxInputSpans());
        a.setMaxTokens(p.getMaxTokens());
        return a;
    }

    static InsightServerAlertProperties toAlertProperties(InsightRuntimeSettings.AlertSettings a) {
        InsightServerAlertProperties p = new InsightServerAlertProperties();
        p.setEnabled(a.isEnabled());
        p.setWebhookUrl(nullToEmpty(a.getWebhookUrl()));
        p.setMetric(a.getMetric());
        p.setThreshold(a.getThreshold());
        p.setWindowMinutes(a.getWindowMinutes());
        p.setCooldownMinutes(a.getCooldownMinutes());
        InsightRuntimeSettings.EmailSettings e = a.getEmail() != null ? a.getEmail() : new InsightRuntimeSettings.EmailSettings();
        InsightServerAlertProperties.Email pe = new InsightServerAlertProperties.Email();
        pe.setEnabled(e.isEnabled());
        pe.setHost(nullToEmpty(e.getHost()));
        pe.setPort(e.getPort());
        pe.setUsername(nullToEmpty(e.getUsername()));
        pe.setPassword(nullToEmpty(e.getPassword()));
        pe.setFrom(nullToEmpty(e.getFrom()));
        pe.setTo(nullToEmpty(e.getTo()));
        pe.setStartTls(e.isStartTls());
        pe.setSsl(e.isSsl());
        p.setEmail(pe);
        return p;
    }

    static InsightServerAiProperties toAiProperties(InsightRuntimeSettings.AiSettings a) {
        InsightServerAiProperties p = new InsightServerAiProperties();
        p.setEnabled(a.isEnabled());
        p.setProvider(a.getProvider());
        p.setBaseUrl(a.getBaseUrl());
        p.setApiKey(nullToEmpty(a.getApiKey()));
        p.setModel(a.getModel());
        p.setTimeoutMs(a.getTimeoutMs());
        p.setMaxInputSpans(a.getMaxInputSpans());
        p.setMaxTokens(a.getMaxTokens());
        return p;
    }

    private static InsightRuntimeSettings deepCopy(InsightRuntimeSettings s) {
        InsightRuntimeSettings c = new InsightRuntimeSettings();
        c.setAlert(deepCopyAlert(s.getAlert()));
        c.setAi(deepCopyAi(s.getAi()));
        return c;
    }

    private static InsightRuntimeSettings.AlertSettings deepCopyAlert(InsightRuntimeSettings.AlertSettings a) {
        if (a == null) {
            return new InsightRuntimeSettings.AlertSettings();
        }
        InsightRuntimeSettings.AlertSettings c = new InsightRuntimeSettings.AlertSettings();
        c.setEnabled(a.isEnabled());
        c.setWebhookUrl(a.getWebhookUrl());
        c.setMetric(a.getMetric());
        c.setThreshold(a.getThreshold());
        c.setWindowMinutes(a.getWindowMinutes());
        c.setCooldownMinutes(a.getCooldownMinutes());
        c.setEmail(deepCopyEmail(a.getEmail()));
        return c;
    }

    private static InsightRuntimeSettings.EmailSettings deepCopyEmail(InsightRuntimeSettings.EmailSettings e) {
        if (e == null) {
            return new InsightRuntimeSettings.EmailSettings();
        }
        InsightRuntimeSettings.EmailSettings c = new InsightRuntimeSettings.EmailSettings();
        c.setEnabled(e.isEnabled());
        c.setHost(e.getHost());
        c.setPort(e.getPort());
        c.setUsername(e.getUsername());
        c.setPassword(e.getPassword());
        c.setFrom(e.getFrom());
        c.setTo(e.getTo());
        c.setStartTls(e.isStartTls());
        c.setSsl(e.isSsl());
        return c;
    }

    private static InsightRuntimeSettings.AiSettings deepCopyAi(InsightRuntimeSettings.AiSettings a) {
        if (a == null) {
            return new InsightRuntimeSettings.AiSettings();
        }
        InsightRuntimeSettings.AiSettings c = new InsightRuntimeSettings.AiSettings();
        c.setEnabled(a.isEnabled());
        c.setProvider(a.getProvider());
        c.setBaseUrl(a.getBaseUrl());
        c.setApiKey(a.getApiKey());
        c.setModel(a.getModel());
        c.setTimeoutMs(a.getTimeoutMs());
        c.setMaxInputSpans(a.getMaxInputSpans());
        c.setMaxTokens(a.getMaxTokens());
        return c;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
