package io.github.iweidujiang.springinsight.server.settings;

import io.github.iweidujiang.springinsight.server.alert.AlertCondition;
import io.github.iweidujiang.springinsight.server.config.InsightServerAiProperties;
import io.github.iweidujiang.springinsight.server.config.InsightServerAlertProperties;
import io.github.iweidujiang.springinsight.server.dashboard.DashboardLayout;
import io.github.iweidujiang.springinsight.server.dashboard.DashboardLayouts;
import io.github.iweidujiang.springinsight.server.dashboard.DashboardPanel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
     * @return 已保存的通知渠道副本；未配置时为空列表，调度继续使用原有 Webhook / 邮件字段
     */
    public List<InsightRuntimeSettings.ChannelConfig> effectiveChannels() {
        List<InsightRuntimeSettings.ChannelConfig> channels = current.get().getChannels();
        if (channels == null || channels.isEmpty()) {
            return List.of();
        }
        List<InsightRuntimeSettings.ChannelConfig> copy = new ArrayList<>();
        for (InsightRuntimeSettings.ChannelConfig channel : channels) {
            copy.add(copyChannel(channel));
        }
        return copy;
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
        body.put("alert", publicAlert(s.getAlert(), s.getChannels()));
        body.put("ai", publicAi(s.getAi()));
        body.put("channels", publicChannels(s));
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
        if (patch.getChannels() != null) {
            merged.setChannels(mergeChannels(merged.getChannels(), patch.getChannels()));
            syncLegacyFromChannels(merged.getAlert(), merged.getChannels());
        }
        store.save(merged);
        current.set(merged);
        return publicView();
    }

    /**
     * 当前仪表盘布局；未保存过时返回默认「全局概览」。
     *
     * @return 布局副本
     */
    public DashboardLayout getDashboardLayout() {
        DashboardLayout saved = current.get().getDashboardLayout();
        if (saved == null || saved.getPanels() == null || saved.getPanels().isEmpty()) {
            return DashboardLayouts.defaultOverview();
        }
        return DashboardLayouts.copy(saved);
    }

    /**
     * 校验并持久化仪表盘布局到 runtime-settings.json。
     *
     * @param layout 客户端提交的布局
     * @return 保存后的布局副本
     */
    public DashboardLayout updateDashboardLayout(DashboardLayout layout) {
        DashboardLayout normalized = normalizeLayout(layout);
        InsightRuntimeSettings merged = deepCopy(current.get());
        merged.setDashboardLayout(normalized);
        store.save(merged);
        current.set(merged);
        return DashboardLayouts.copy(normalized);
    }

    /**
     * @param layout 原始布局
     * @return 校验并裁剪后的布局
     */
    static DashboardLayout normalizeLayout(DashboardLayout layout) {
        if (layout == null || layout.getPanels() == null || layout.getPanels().isEmpty()) {
            throw new IllegalArgumentException("panels 不能为空");
        }
        if (layout.getPanels().size() > 40) {
            throw new IllegalArgumentException("面板数量不能超过 40");
        }
        DashboardLayout out = new DashboardLayout();
        out.setTemplateId(layout.getTemplateId() == null ? "" : layout.getTemplateId().trim());
        List<DashboardPanel> panels = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (DashboardPanel raw : layout.getPanels()) {
            if (raw == null) {
                continue;
            }
            String id = raw.getId() == null ? "" : raw.getId().trim();
            String type = raw.getType() == null ? "" : raw.getType().trim();
            if (!StringUtils.hasText(id) || !StringUtils.hasText(type)) {
                throw new IllegalArgumentException("每个面板必须包含 id 与 type");
            }
            if (!ids.add(id)) {
                throw new IllegalArgumentException("面板 id 重复: " + id);
            }
            DashboardPanel p = new DashboardPanel();
            p.setId(id);
            p.setType(type);
            p.setX(clamp(raw.getX(), 0, 32));
            p.setY(clamp(raw.getY(), 0, 64));
            p.setW(clamp(raw.getW(), 1, 12));
            p.setH(clamp(raw.getH(), 1, 12));
            p.setVisible(raw.isVisible());
            panels.add(p);
        }
        if (panels.isEmpty()) {
            throw new IllegalArgumentException("panels 不能为空");
        }
        out.setPanels(panels);
        return out;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
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
        if (patch.getConditions() != null) {
            out.setConditions(copyConditions(patch.getConditions()));
        }
        out.setWindowMinutes(patch.getWindowMinutes());
        out.setCooldownMinutes(patch.getCooldownMinutes());
        out.setQuietHoursEnabled(patch.isQuietHoursEnabled());
        if (StringUtils.hasText(patch.getQuietHoursStart())) {
            out.setQuietHoursStart(patch.getQuietHoursStart().trim());
        }
        if (StringUtils.hasText(patch.getQuietHoursEnd())) {
            out.setQuietHoursEnd(patch.getQuietHoursEnd().trim());
        }
        out.setQuietHoursZone(nullToEmpty(patch.getQuietHoursZone()));
        if (patch.getDingtalk() != null) {
            InsightRuntimeSettings.DingtalkSettings d = out.getDingtalk() != null
                    ? deepCopyDingtalk(out.getDingtalk()) : new InsightRuntimeSettings.DingtalkSettings();
            InsightRuntimeSettings.DingtalkSettings p = patch.getDingtalk();
            d.setEnabled(p.isEnabled());
            d.setWebhookUrl(nullToEmpty(p.getWebhookUrl()));
            if (StringUtils.hasText(p.getSecret())) {
                d.setSecret(p.getSecret());
            }
            d.setServerUrl(nullToEmpty(p.getServerUrl()));
            out.setDingtalk(d);
        }
        if (patch.getFeishu() != null) {
            InsightRuntimeSettings.FeishuSettings f = out.getFeishu() != null
                    ? deepCopyFeishu(out.getFeishu()) : new InsightRuntimeSettings.FeishuSettings();
            InsightRuntimeSettings.FeishuSettings p = patch.getFeishu();
            f.setEnabled(p.isEnabled());
            f.setWebhookUrl(nullToEmpty(p.getWebhookUrl()));
            if (StringUtils.hasText(p.getSecret())) {
                f.setSecret(p.getSecret());
            }
            f.setServerUrl(nullToEmpty(p.getServerUrl()));
            out.setFeishu(f);
        }
        if (patch.getWecom() != null) {
            InsightRuntimeSettings.WecomSettings w = out.getWecom() != null
                    ? deepCopyWecom(out.getWecom()) : new InsightRuntimeSettings.WecomSettings();
            InsightRuntimeSettings.WecomSettings p = patch.getWecom();
            w.setEnabled(p.isEnabled());
            w.setWebhookUrl(nullToEmpty(p.getWebhookUrl()));
            w.setServerUrl(nullToEmpty(p.getServerUrl()));
            out.setWecom(w);
        }
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
        out.setAttachToAlerts(patch.isAttachToAlerts());
        out.setAlertMaxPerHour(patch.getAlertMaxPerHour() > 0 ? patch.getAlertMaxPerHour() : out.getAlertMaxPerHour());
        out.setPeriodMaxPerHour(patch.getPeriodMaxPerHour() > 0 ? patch.getPeriodMaxPerHour() : out.getPeriodMaxPerHour());
        return out;
    }

    private static Map<String, Object> publicAlert(
            InsightRuntimeSettings.AlertSettings a,
            List<InsightRuntimeSettings.ChannelConfig> channels) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", a.isEnabled());
        m.put("webhookUrl", nullToEmpty(a.getWebhookUrl()));
        m.put("metric", a.getMetric());
        m.put("threshold", a.getThreshold());
        m.put("conditions", publicConditions(a));
        m.put("windowMinutes", a.getWindowMinutes());
        m.put("cooldownMinutes", a.getCooldownMinutes());
        m.put("quietHoursEnabled", a.isQuietHoursEnabled());
        m.put("quietHoursStart", nullToEmpty(a.getQuietHoursStart()));
        m.put("quietHoursEnd", nullToEmpty(a.getQuietHoursEnd()));
        m.put("quietHoursZone", nullToEmpty(a.getQuietHoursZone()));
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
        InsightRuntimeSettings.DingtalkSettings d = a.getDingtalk() != null
                ? a.getDingtalk() : new InsightRuntimeSettings.DingtalkSettings();
        Map<String, Object> dingtalk = new LinkedHashMap<>();
        dingtalk.put("enabled", d.isEnabled());
        dingtalk.put("webhookUrl", nullToEmpty(d.getWebhookUrl()));
        dingtalk.put("secretConfigured", StringUtils.hasText(d.getSecret()));
        dingtalk.put("serverUrl", nullToEmpty(d.getServerUrl()));
        m.put("dingtalk", dingtalk);
        InsightRuntimeSettings.FeishuSettings f = a.getFeishu() != null
                ? a.getFeishu() : new InsightRuntimeSettings.FeishuSettings();
        Map<String, Object> feishu = new LinkedHashMap<>();
        feishu.put("enabled", f.isEnabled());
        feishu.put("webhookUrl", nullToEmpty(f.getWebhookUrl()));
        feishu.put("secretConfigured", StringUtils.hasText(f.getSecret()));
        feishu.put("serverUrl", nullToEmpty(f.getServerUrl()));
        m.put("feishu", feishu);
        InsightRuntimeSettings.WecomSettings w = a.getWecom() != null
                ? a.getWecom() : new InsightRuntimeSettings.WecomSettings();
        Map<String, Object> wecom = new LinkedHashMap<>();
        wecom.put("enabled", w.isEnabled());
        wecom.put("webhookUrl", nullToEmpty(w.getWebhookUrl()));
        wecom.put("serverUrl", nullToEmpty(w.getServerUrl()));
        m.put("wecom", wecom);
        m.put("channels", publicChannelList(channels, a));
        return m;
    }

    /**
     * 已保存渠道的脱敏列表。还没有渠道列表时，用现有 Webhook / 邮件 / 机器人字段拼出同样的五条，方便设置页展示。
     */
    private static List<Map<String, Object>> publicChannelList(
            List<InsightRuntimeSettings.ChannelConfig> channels,
            InsightRuntimeSettings.AlertSettings alert) {
        List<InsightRuntimeSettings.ChannelConfig> source = channels == null || channels.isEmpty()
                ? synthesizedChannels(alert) : channels;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (InsightRuntimeSettings.ChannelConfig channel : source) {
            rows.add(publicChannel(channel));
        }
        return rows;
    }

    private static List<Map<String, Object>> publicChannels(InsightRuntimeSettings settings) {
        return publicChannelList(settings.getChannels(), settings.getAlert());
    }

    /**
     * @param channel 渠道；密钥只返回是否已配置
     * @return 脱敏行
     */
    private static Map<String, Object> publicChannel(InsightRuntimeSettings.ChannelConfig channel) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", nullToEmpty(channel.getId()));
        row.put("type", nullToEmpty(channel.getType()));
        row.put("enabled", channel.isEnabled());
        row.put("name", nullToEmpty(channel.getName()));
        row.put("webhookUrl", nullToEmpty(channel.getWebhookUrl()));
        row.put("secretConfigured", StringUtils.hasText(channel.getSecret()));
        row.put("tokenConfigured", StringUtils.hasText(channel.getToken()));
        row.put("serverUrl", nullToEmpty(channel.getServerUrl()));
        row.put("smtpHost", nullToEmpty(channel.getSmtpHost()));
        row.put("smtpPort", channel.getSmtpPort() > 0 ? channel.getSmtpPort() : 587);
        row.put("smtpUsername", nullToEmpty(channel.getSmtpUsername()));
        row.put("passwordConfigured", StringUtils.hasText(channel.getSmtpPassword()));
        row.put("smtpFrom", nullToEmpty(channel.getSmtpFrom()));
        row.put("smtpTo", nullToEmpty(channel.getSmtpTo()));
        row.put("startTls", channel.isStartTls());
        row.put("ssl", channel.isSsl());
        return row;
    }

    /**
     * 把旧字段收成渠道列表，仅用于展示。调度在列表真正保存之前仍走原字段。
     */
    static List<InsightRuntimeSettings.ChannelConfig> synthesizedChannels(InsightRuntimeSettings.AlertSettings alert) {
        InsightRuntimeSettings.AlertSettings a = alert != null ? alert : new InsightRuntimeSettings.AlertSettings();
        List<InsightRuntimeSettings.ChannelConfig> rows = new ArrayList<>();
        rows.add(channel("webhook", "Webhook", StringUtils.hasText(a.getWebhookUrl()), a.getWebhookUrl(), "", "", ""));
        InsightRuntimeSettings.EmailSettings email = a.getEmail() != null ? a.getEmail() : new InsightRuntimeSettings.EmailSettings();
        InsightRuntimeSettings.ChannelConfig mail = channel("email", "邮件", email.isEnabled(), "", "", "", "");
        mail.setSmtpHost(nullToEmpty(email.getHost()));
        mail.setSmtpPort(email.getPort() > 0 ? email.getPort() : 587);
        mail.setSmtpUsername(nullToEmpty(email.getUsername()));
        mail.setSmtpPassword(nullToEmpty(email.getPassword()));
        mail.setSmtpFrom(nullToEmpty(email.getFrom()));
        mail.setSmtpTo(nullToEmpty(email.getTo()));
        mail.setStartTls(email.isStartTls());
        mail.setSsl(email.isSsl());
        rows.add(mail);
        InsightRuntimeSettings.DingtalkSettings dingtalk = a.getDingtalk() != null
                ? a.getDingtalk() : new InsightRuntimeSettings.DingtalkSettings();
        rows.add(channel("dingtalk", "钉钉", dingtalk.isEnabled(), dingtalk.getWebhookUrl(), dingtalk.getSecret(), "", dingtalk.getServerUrl()));
        InsightRuntimeSettings.FeishuSettings feishu = a.getFeishu() != null
                ? a.getFeishu() : new InsightRuntimeSettings.FeishuSettings();
        rows.add(channel("feishu", "飞书", feishu.isEnabled(), feishu.getWebhookUrl(), feishu.getSecret(), "", feishu.getServerUrl()));
        InsightRuntimeSettings.WecomSettings wecom = a.getWecom() != null
                ? a.getWecom() : new InsightRuntimeSettings.WecomSettings();
        rows.add(channel("wecom", "企业微信", wecom.isEnabled(), wecom.getWebhookUrl(), "", "", wecom.getServerUrl()));
        return rows;
    }

    private static InsightRuntimeSettings.ChannelConfig channel(
            String type, String name, boolean enabled, String webhookUrl, String secret, String token, String serverUrl) {
        InsightRuntimeSettings.ChannelConfig channel = new InsightRuntimeSettings.ChannelConfig();
        channel.setId(type);
        channel.setType(type);
        channel.setName(name);
        channel.setEnabled(enabled);
        channel.setWebhookUrl(nullToEmpty(webhookUrl));
        channel.setSecret(nullToEmpty(secret));
        channel.setToken(nullToEmpty(token));
        channel.setServerUrl(nullToEmpty(serverUrl));
        return channel;
    }

    /**
     * 按 id 合并。密钥、令牌、SMTP 密码留空时保留原值。
     */
    private static List<InsightRuntimeSettings.ChannelConfig> mergeChannels(
            List<InsightRuntimeSettings.ChannelConfig> base,
            List<InsightRuntimeSettings.ChannelConfig> patch) {
        Map<String, InsightRuntimeSettings.ChannelConfig> previous = new LinkedHashMap<>();
        if (base != null) {
            for (InsightRuntimeSettings.ChannelConfig channel : base) {
                if (channel != null && StringUtils.hasText(channel.getId())) {
                    previous.put(channel.getId(), channel);
                }
            }
        }
        List<InsightRuntimeSettings.ChannelConfig> next = new ArrayList<>();
        for (InsightRuntimeSettings.ChannelConfig incoming : patch) {
            if (incoming == null || !StringUtils.hasText(incoming.getType())) {
                continue;
            }
            InsightRuntimeSettings.ChannelConfig old = previous.get(nullToEmpty(incoming.getId()));
            InsightRuntimeSettings.ChannelConfig copy = copyChannel(incoming);
            if (!StringUtils.hasText(copy.getId())) {
                copy.setId(copy.getType());
            }
            if (!StringUtils.hasText(copy.getSecret()) && old != null) {
                copy.setSecret(old.getSecret());
            }
            if (!StringUtils.hasText(copy.getToken()) && old != null) {
                copy.setToken(old.getToken());
            }
            if (!StringUtils.hasText(copy.getSmtpPassword()) && old != null) {
                copy.setSmtpPassword(old.getSmtpPassword());
            }
            next.add(copy);
        }
        return next;
    }

    /**
     * 渠道列表写回原有字段，让只读这些字段的预览和旧配置保持一致。
     */
    private static void syncLegacyFromChannels(
            InsightRuntimeSettings.AlertSettings alert,
            List<InsightRuntimeSettings.ChannelConfig> channels) {
        if (alert == null || channels == null) {
            return;
        }
        for (InsightRuntimeSettings.ChannelConfig channel : channels) {
            String type = channel.getType() == null ? "" : channel.getType().trim().toLowerCase();
            switch (type) {
                case "webhook" -> alert.setWebhookUrl(nullToEmpty(channel.getWebhookUrl()));
                case "email" -> {
                    InsightRuntimeSettings.EmailSettings email = alert.getEmail() != null
                            ? alert.getEmail() : new InsightRuntimeSettings.EmailSettings();
                    email.setEnabled(channel.isEnabled());
                    email.setHost(nullToEmpty(channel.getSmtpHost()));
                    email.setPort(channel.getSmtpPort() > 0 ? channel.getSmtpPort() : 587);
                    email.setUsername(nullToEmpty(channel.getSmtpUsername()));
                    if (StringUtils.hasText(channel.getSmtpPassword())) {
                        email.setPassword(channel.getSmtpPassword());
                    }
                    email.setFrom(nullToEmpty(channel.getSmtpFrom()));
                    email.setTo(nullToEmpty(channel.getSmtpTo()));
                    email.setStartTls(channel.isStartTls());
                    email.setSsl(channel.isSsl());
                    alert.setEmail(email);
                }
                case "dingtalk" -> {
                    InsightRuntimeSettings.DingtalkSettings dingtalk = new InsightRuntimeSettings.DingtalkSettings();
                    dingtalk.setEnabled(channel.isEnabled());
                    dingtalk.setWebhookUrl(nullToEmpty(channel.getWebhookUrl()));
                    dingtalk.setSecret(nullToEmpty(channel.getSecret()));
                    dingtalk.setServerUrl(nullToEmpty(channel.getServerUrl()));
                    alert.setDingtalk(dingtalk);
                }
                case "feishu" -> {
                    InsightRuntimeSettings.FeishuSettings feishu = new InsightRuntimeSettings.FeishuSettings();
                    feishu.setEnabled(channel.isEnabled());
                    feishu.setWebhookUrl(nullToEmpty(channel.getWebhookUrl()));
                    feishu.setSecret(nullToEmpty(channel.getSecret()));
                    feishu.setServerUrl(nullToEmpty(channel.getServerUrl()));
                    alert.setFeishu(feishu);
                }
                case "wecom" -> {
                    InsightRuntimeSettings.WecomSettings wecom = new InsightRuntimeSettings.WecomSettings();
                    wecom.setEnabled(channel.isEnabled());
                    wecom.setWebhookUrl(nullToEmpty(channel.getWebhookUrl()));
                    wecom.setServerUrl(nullToEmpty(channel.getServerUrl()));
                    alert.setWecom(wecom);
                }
                default -> {
                    // 未知类型只留在渠道列表里
                }
            }
        }
    }

    private static InsightRuntimeSettings.ChannelConfig copyChannel(InsightRuntimeSettings.ChannelConfig source) {
        InsightRuntimeSettings.ChannelConfig copy = new InsightRuntimeSettings.ChannelConfig();
        if (source == null) {
            return copy;
        }
        copy.setId(nullToEmpty(source.getId()));
        copy.setType(nullToEmpty(source.getType()));
        copy.setEnabled(source.isEnabled());
        copy.setName(nullToEmpty(source.getName()));
        copy.setWebhookUrl(nullToEmpty(source.getWebhookUrl()));
        copy.setSecret(nullToEmpty(source.getSecret()));
        copy.setToken(nullToEmpty(source.getToken()));
        copy.setServerUrl(nullToEmpty(source.getServerUrl()));
        copy.setSmtpHost(nullToEmpty(source.getSmtpHost()));
        copy.setSmtpPort(source.getSmtpPort());
        copy.setSmtpUsername(nullToEmpty(source.getSmtpUsername()));
        copy.setSmtpPassword(nullToEmpty(source.getSmtpPassword()));
        copy.setSmtpFrom(nullToEmpty(source.getSmtpFrom()));
        copy.setSmtpTo(nullToEmpty(source.getSmtpTo()));
        copy.setStartTls(source.isStartTls());
        copy.setSsl(source.isSsl());
        return copy;
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
        m.put("attachToAlerts", a.isAttachToAlerts());
        m.put("alertMaxPerHour", a.getAlertMaxPerHour());
        m.put("periodMaxPerHour", a.getPeriodMaxPerHour());
        return m;
    }

    /**
     * 对外返回实际生效的条件。未单独配置时由旧指标和阈值生成一条 gte。
     */
    private static List<Map<String, Object>> publicConditions(InsightRuntimeSettings.AlertSettings alert) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AlertCondition condition : AlertCondition.resolve(
                alert.getConditions(), alert.getMetric(), alert.getThreshold())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("metric", condition.getMetric());
            row.put("operator", condition.getOperator());
            row.put("threshold", condition.getThreshold());
            rows.add(row);
        }
        return rows;
    }

    /**
     * @param conditions 条件列表
     * @return 副本
     */
    private static List<AlertCondition> copyConditions(List<AlertCondition> conditions) {
        List<AlertCondition> copy = new ArrayList<>();
        for (AlertCondition condition : conditions) {
            if (condition == null) {
                continue;
            }
            AlertCondition item = new AlertCondition();
            item.setMetric(condition.getMetric());
            item.setOperator(condition.getOperator());
            item.setThreshold(condition.getThreshold());
            copy.add(item);
        }
        return copy;
    }

    static InsightRuntimeSettings.AlertSettings fromAlertProperties(InsightServerAlertProperties p) {
        InsightRuntimeSettings.AlertSettings a = new InsightRuntimeSettings.AlertSettings();
        a.setEnabled(p.isEnabled());
        a.setWebhookUrl(nullToEmpty(p.getWebhookUrl()));
        a.setMetric(p.getMetric());
        a.setThreshold(p.getThreshold());
        if (p.getConditions() != null) {
            a.setConditions(copyConditions(p.getConditions()));
        }
        a.setWindowMinutes(p.getWindowMinutes());
        a.setCooldownMinutes(p.getCooldownMinutes());
        a.setQuietHoursEnabled(p.isQuietHoursEnabled());
        a.setQuietHoursStart(nullToEmpty(p.getQuietHoursStart()));
        a.setQuietHoursEnd(nullToEmpty(p.getQuietHoursEnd()));
        a.setQuietHoursZone(nullToEmpty(p.getQuietHoursZone()));
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
        if (p.getDingtalk() != null) {
            InsightRuntimeSettings.DingtalkSettings d = new InsightRuntimeSettings.DingtalkSettings();
            d.setEnabled(p.getDingtalk().isEnabled());
            d.setWebhookUrl(nullToEmpty(p.getDingtalk().getWebhookUrl()));
            d.setSecret(nullToEmpty(p.getDingtalk().getSecret()));
            d.setServerUrl(nullToEmpty(p.getDingtalk().getServerUrl()));
            a.setDingtalk(d);
        }
        if (p.getFeishu() != null) {
            InsightRuntimeSettings.FeishuSettings f = new InsightRuntimeSettings.FeishuSettings();
            f.setEnabled(p.getFeishu().isEnabled());
            f.setWebhookUrl(nullToEmpty(p.getFeishu().getWebhookUrl()));
            f.setSecret(nullToEmpty(p.getFeishu().getSecret()));
            f.setServerUrl(nullToEmpty(p.getFeishu().getServerUrl()));
            a.setFeishu(f);
        }
        if (p.getWecom() != null) {
            InsightRuntimeSettings.WecomSettings w = new InsightRuntimeSettings.WecomSettings();
            w.setEnabled(p.getWecom().isEnabled());
            w.setWebhookUrl(nullToEmpty(p.getWecom().getWebhookUrl()));
            w.setServerUrl(nullToEmpty(p.getWecom().getServerUrl()));
            a.setWecom(w);
        }
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
        a.setAttachToAlerts(p.isAttachToAlerts());
        a.setAlertMaxPerHour(p.getAlertMaxPerHour());
        a.setPeriodMaxPerHour(p.getPeriodMaxPerHour());
        return a;
    }

    static InsightServerAlertProperties toAlertProperties(InsightRuntimeSettings.AlertSettings a) {
        InsightServerAlertProperties p = new InsightServerAlertProperties();
        p.setEnabled(a.isEnabled());
        p.setWebhookUrl(nullToEmpty(a.getWebhookUrl()));
        p.setMetric(a.getMetric());
        p.setThreshold(a.getThreshold());
        if (a.getConditions() != null) {
            p.setConditions(copyConditions(a.getConditions()));
        }
        p.setWindowMinutes(a.getWindowMinutes());
        p.setCooldownMinutes(a.getCooldownMinutes());
        p.setQuietHoursEnabled(a.isQuietHoursEnabled());
        p.setQuietHoursStart(nullToEmpty(a.getQuietHoursStart()));
        p.setQuietHoursEnd(nullToEmpty(a.getQuietHoursEnd()));
        p.setQuietHoursZone(nullToEmpty(a.getQuietHoursZone()));
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
        if (a.getDingtalk() != null) {
            InsightServerAlertProperties.Dingtalk d = new InsightServerAlertProperties.Dingtalk();
            d.setEnabled(a.getDingtalk().isEnabled());
            d.setWebhookUrl(nullToEmpty(a.getDingtalk().getWebhookUrl()));
            d.setSecret(nullToEmpty(a.getDingtalk().getSecret()));
            d.setServerUrl(nullToEmpty(a.getDingtalk().getServerUrl()));
            p.setDingtalk(d);
        }
        if (a.getFeishu() != null) {
            InsightServerAlertProperties.Feishu f = new InsightServerAlertProperties.Feishu();
            f.setEnabled(a.getFeishu().isEnabled());
            f.setWebhookUrl(nullToEmpty(a.getFeishu().getWebhookUrl()));
            f.setSecret(nullToEmpty(a.getFeishu().getSecret()));
            f.setServerUrl(nullToEmpty(a.getFeishu().getServerUrl()));
            p.setFeishu(f);
        }
        if (a.getWecom() != null) {
            InsightServerAlertProperties.Wecom w = new InsightServerAlertProperties.Wecom();
            w.setEnabled(a.getWecom().isEnabled());
            w.setWebhookUrl(nullToEmpty(a.getWecom().getWebhookUrl()));
            w.setServerUrl(nullToEmpty(a.getWecom().getServerUrl()));
            p.setWecom(w);
        }
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
        p.setAttachToAlerts(a.isAttachToAlerts());
        p.setAlertMaxPerHour(a.getAlertMaxPerHour() > 0 ? a.getAlertMaxPerHour() : 10);
        p.setPeriodMaxPerHour(a.getPeriodMaxPerHour() > 0 ? a.getPeriodMaxPerHour() : 20);
        return p;
    }

    private static InsightRuntimeSettings deepCopy(InsightRuntimeSettings s) {
        InsightRuntimeSettings c = new InsightRuntimeSettings();
        c.setAlert(deepCopyAlert(s.getAlert()));
        c.setAi(deepCopyAi(s.getAi()));
        if (s.getChannels() != null) {
            List<InsightRuntimeSettings.ChannelConfig> channels = new ArrayList<>();
            for (InsightRuntimeSettings.ChannelConfig channel : s.getChannels()) {
                channels.add(copyChannel(channel));
            }
            c.setChannels(channels);
        }
        if (s.getDashboardLayout() != null) {
            c.setDashboardLayout(DashboardLayouts.copy(s.getDashboardLayout()));
        }
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
        if (a.getConditions() != null) {
            c.setConditions(copyConditions(a.getConditions()));
        }
        c.setWindowMinutes(a.getWindowMinutes());
        c.setCooldownMinutes(a.getCooldownMinutes());
        c.setQuietHoursEnabled(a.isQuietHoursEnabled());
        c.setQuietHoursStart(a.getQuietHoursStart());
        c.setQuietHoursEnd(a.getQuietHoursEnd());
        c.setQuietHoursZone(a.getQuietHoursZone());
        c.setEmail(deepCopyEmail(a.getEmail()));
        c.setDingtalk(deepCopyDingtalk(a.getDingtalk()));
        c.setFeishu(deepCopyFeishu(a.getFeishu()));
        c.setWecom(deepCopyWecom(a.getWecom()));
        return c;
    }

    /**
     * @param w 企业微信设置，可空
     * @return 副本；入参为空时返回 null
     */
    private static InsightRuntimeSettings.WecomSettings deepCopyWecom(InsightRuntimeSettings.WecomSettings w) {
        if (w == null) {
            return null;
        }
        InsightRuntimeSettings.WecomSettings c = new InsightRuntimeSettings.WecomSettings();
        c.setEnabled(w.isEnabled());
        c.setWebhookUrl(w.getWebhookUrl());
        c.setServerUrl(w.getServerUrl());
        return c;
    }

    /**
     * @param f 飞书设置，可空
     * @return 副本；入参为空时返回 null
     */
    private static InsightRuntimeSettings.FeishuSettings deepCopyFeishu(InsightRuntimeSettings.FeishuSettings f) {
        if (f == null) {
            return null;
        }
        InsightRuntimeSettings.FeishuSettings c = new InsightRuntimeSettings.FeishuSettings();
        c.setEnabled(f.isEnabled());
        c.setWebhookUrl(f.getWebhookUrl());
        c.setSecret(f.getSecret());
        c.setServerUrl(f.getServerUrl());
        return c;
    }

    private static InsightRuntimeSettings.DingtalkSettings deepCopyDingtalk(InsightRuntimeSettings.DingtalkSettings d) {
        if (d == null) {
            return null;
        }
        InsightRuntimeSettings.DingtalkSettings c = new InsightRuntimeSettings.DingtalkSettings();
        c.setEnabled(d.isEnabled());
        c.setWebhookUrl(d.getWebhookUrl());
        c.setSecret(d.getSecret());
        c.setServerUrl(d.getServerUrl());
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
        c.setAttachToAlerts(a.isAttachToAlerts());
        c.setAlertMaxPerHour(a.getAlertMaxPerHour());
        c.setPeriodMaxPerHour(a.getPeriodMaxPerHour());
        return c;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
