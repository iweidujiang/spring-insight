package io.github.iweidujiang.springinsight.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * insight-server 存储相关配置。
 * <p>
 * {@code memory}：进程内环形缓冲，重启清空。<br>
 * {@code file}：内存查询 + JSON 落盘。<br>
 * {@code sqlite}：SQLite 持久化，重启可恢复（需显式开启）。
 * </p>
 *
 * @since 2026-09-13
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Data
@ConfigurationProperties(prefix = "spring.insight.server.storage")
public class InsightServerStorageProperties {

    /**
     * 存储模式：memory（默认）| file | sqlite
     */
    private String mode = "memory";

    /**
     * 最多保留的 Span 条数（超限丢弃最旧）
     */
    private int maxSpans = 50_000;

    /**
     * file 模式下的持久化文件路径
     */
    private String filePath = "./data/spans.json";

    /**
     * file 模式写入防抖间隔（毫秒）
     */
    private long flushDelayMs = 2_000L;

    /**
     * 按时间保留策略
     */
    private Retention retention = new Retention();

    /**
     * sqlite 模式参数
     */
    private Sqlite sqlite = new Sqlite();

    /**
     * @return 是否 file 模式
     */
    public boolean isFileMode() {
        return "file".equalsIgnoreCase(mode);
    }

    /**
     * @return 是否 sqlite 模式
     */
    public boolean isSqliteMode() {
        return "sqlite".equalsIgnoreCase(mode);
    }

    /**
     * 规范化后的 mode 小写字符串。
     *
     * @return memory / file / sqlite
     */
    public String normalizedMode() {
        if (mode == null || mode.isBlank()) {
            return "memory";
        }
        String m = mode.trim().toLowerCase();
        if ("file".equals(m) || "sqlite".equals(m)) {
            return m;
        }
        return "memory";
    }

    /**
     * 按时间裁剪配置。
     */
    @Data
    public static class Retention {
        /**
         * 仅保留最近 N 小时；{@code 0} 表示不按时间裁
         */
        private int maxAgeHours = 0;

        /**
         * 时间裁剪扫描间隔（毫秒）
         */
        private long vacuumIntervalMs = 60_000L;
    }

    /**
     * SQLite 配置。
     */
    @Data
    public static class Sqlite {
        /**
         * 数据库文件路径
         */
        private String path = "./data/insight.db";
    }
}
