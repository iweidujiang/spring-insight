package io.github.iweidujiang.springinsight.server.jvm;

import io.github.iweidujiang.springinsight.agent.model.JvmMetric;

import java.util.List;
import java.util.Optional;

/**
 * JVM 指标环形缓冲存储（按服务 / 实例）。
 *
 * @author 苏渡苇
 * @since 2026-11-09
 */
public interface JvmMetricStore {

    /**
     * 写入一批指标；会回填空的服务名 / 实例并按时间戳有序保留。
     *
     * @param serviceName     批次服务名
     * @param serviceInstance 批次实例
     * @param metrics         指标点
     */
    void saveAll(String serviceName, String serviceInstance, List<JvmMetric> metrics);

    /**
     * @param serviceName 服务名
     * @return 该服务下出现过的实例标识（字典序）
     */
    List<String> listInstances(String serviceName);

    /**
     * 查询时间窗内指标点（按时间升序）。
     *
     * @param serviceName 服务名
     * @param instance    实例；空则合并该服务全部实例
     * @param fromMs      起始毫秒（含）
     * @param toMs        结束毫秒（含）
     * @return 命中点
     */
    List<JvmMetric> query(String serviceName, String instance, long fromMs, long toMs);

    /**
     * @param serviceName 服务名
     * @param instance    实例；空则取该服务任意最新点
     * @return 最新一点
     */
    Optional<JvmMetric> latest(String serviceName, String instance);
}
