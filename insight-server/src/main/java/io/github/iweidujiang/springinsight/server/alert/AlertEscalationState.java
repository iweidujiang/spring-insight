package io.github.iweidujiang.springinsight.server.alert;

/**
 * 单服务活跃告警状态（内存）。
 *
 * @param firstFiredAtMs 首次触发 epoch ms
 * @param escalated      是否已发送过升级通知
 * @author 苏渡苇
 * @since 2026-10-24
 */
public record AlertEscalationState(long firstFiredAtMs, boolean escalated) {

    /**
     * @param firstFiredAtMs 首次触发
     * @return 未升级状态
     */
    public static AlertEscalationState open(long firstFiredAtMs) {
        return new AlertEscalationState(firstFiredAtMs, false);
    }

    /**
     * @return 标记已升级的副本
     */
    public AlertEscalationState markEscalated() {
        return new AlertEscalationState(firstFiredAtMs, true);
    }

    /**
     * 是否应发送升级通知（未升级且持续超过阈值）。
     *
     * @param nowMs        当前时刻
     * @param afterMinutes 持续分钟阈值
     * @return true 表示应升级
     */
    public boolean shouldEscalate(long nowMs, int afterMinutes) {
        if (escalated) {
            return false;
        }
        int minutes = afterMinutes > 0 ? afterMinutes : 30;
        return nowMs - firstFiredAtMs >= minutes * 60_000L;
    }

    /**
     * @param nowMs 当前时刻
     * @return 已持续分钟数（向下取整，至少 0）
     */
    public long durationMinutes(long nowMs) {
        long ms = Math.max(0L, nowMs - firstFiredAtMs);
        return ms / 60_000L;
    }
}
