package io.github.iweidujiang.springinsight.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * insight-server 存储相关配置。
 * <p>
 * 默认 {@code memory}：进程内环形缓冲，重启清空。<br>
 * 可选 {@code file}：内存查询 + JSON 文件落盘，重启后可恢复。
 * </p>
 */
@Data
@ConfigurationProperties(prefix = "spring.insight.server.storage")
public class InsightServerStorageProperties {

    /**
     * 存储模式：{@code memory}（默认）或 {@code file}
     */
    private String mode = "memory";

    /**
     * 内存中最多保留的 Span 条数（超限丢弃最旧）
     */
    private int maxSpans = 50_000;

    /**
     * file 模式下的持久化文件路径（相对路径相对进程工作目录）
     */
    private String filePath = "./data/spans.json";

    /**
     * file 模式下写入防抖间隔（毫秒）；批量上报时合并刷盘
     */
    private long flushDelayMs = 2_000L;

    public boolean isFileMode() {
        return "file".equalsIgnoreCase(mode);
    }
}
