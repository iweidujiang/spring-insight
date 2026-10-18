package io.github.iweidujiang.springinsight.server.alert;

/**
 * 告警推送渠道。新增渠道时实现本接口并注册为 Spring Bean，调度器按 {@link #channelType()} 选择。
 */
public interface InsightAlertChannelSender {

    /**
     * @return 渠道标识，如 {@code webhook}、{@code email}、{@code dingtalk}、{@code feishu}、{@code wecom}
     */
    String channelType();

    /**
     * 发送一条告警。失败时抛出异常，由调度器记为该渠道失败。
     *
     * @param payload 告警内容
     * @param config  本渠道所需配置
     * @throws Exception 发送失败
     */
    void send(AlertPayload payload, ChannelConfig config) throws Exception;
}
