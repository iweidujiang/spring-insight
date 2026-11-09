package io.github.iweidujiang.springinsight.server.jvm;

import io.github.iweidujiang.springinsight.agent.model.JvmMetric;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 控制台 JVM 指标查询：组装曲线点与最新快照。
 *
 * @author 苏渡苇
 * @since 2026-11-09
 */
@Service
public class JvmMetricQueryService {

    private final JvmMetricStore jvmMetricStore;

    /**
     * @param jvmMetricStore 指标存储
     */
    public JvmMetricQueryService(JvmMetricStore jvmMetricStore) {
        this.jvmMetricStore = jvmMetricStore;
    }

    /**
     * 写入一批上报指标。
     *
     * @param serviceName     服务名
     * @param serviceInstance 实例
     * @param metrics         指标点
     * @return 实际写入条数
     */
    public int ingest(String serviceName, String serviceInstance, List<JvmMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return 0;
        }
        jvmMetricStore.saveAll(serviceName, serviceInstance, metrics);
        return metrics.size();
    }

    /**
     * 组装服务详情页所需的 JVM 视图。
     *
     * @param serviceName 服务名
     * @param hours       时间窗小时；0 表示尽量取全量缓冲
     * @param instance    可选实例过滤
     * @return 视图 Map
     */
    public Map<String, Object> buildServiceView(String serviceName, int hours, String instance) {
        Map<String, Object> body = new LinkedHashMap<>();
        String svc = serviceName == null ? "" : serviceName.trim();
        body.put("serviceName", svc);
        body.put("hours", hours);

        List<String> instances = jvmMetricStore.listInstances(svc);
        body.put("instances", instances);

        String inst = instance == null ? "" : instance.trim();
        if (inst.isEmpty() && instances.size() == 1) {
            inst = instances.get(0);
        }
        body.put("instance", inst.isEmpty() ? null : inst);

        long toMs = System.currentTimeMillis();
        long fromMs = hours <= 0 ? 0L : toMs - hours * 3600_000L;
        List<JvmMetric> points = jvmMetricStore.query(svc, inst, fromMs, toMs);
        body.put("found", !points.isEmpty());
        body.put("pointCount", points.size());
        body.put("points", toSeries(points));

        Optional<JvmMetric> latest = points.isEmpty()
                ? jvmMetricStore.latest(svc, inst)
                : Optional.of(points.get(points.size() - 1));
        body.put("latest", latest.map(this::toLatestSnapshot).orElse(null));
        return body;
    }

    private List<Map<String, Object>> toSeries(List<JvmMetric> points) {
        List<Map<String, Object>> series = new ArrayList<>(points.size());
        for (JvmMetric m : points) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("timestamp", m.getTimestamp());
            row.put("serviceInstance", m.getServiceInstance());
            row.put("heapMemoryUsed", m.getHeapMemoryUsed());
            row.put("heapMemoryMax", m.getHeapMemoryMax());
            row.put("heapMemoryCommitted", m.getHeapMemoryCommitted());
            row.put("nonHeapMemoryUsed", m.getNonHeapMemoryUsed());
            row.put("gcCount", m.getGcCount());
            row.put("gcTime", m.getGcTime());
            row.put("youngGcCount", m.getYoungGcCount());
            row.put("oldGcCount", m.getOldGcCount());
            row.put("activeThreads", m.getActiveThreads());
            row.put("peakThreads", m.getPeakThreads());
            row.put("daemonThreads", m.getDaemonThreads());
            row.put("systemLoadAverage", m.getSystemLoadAverage());
            series.add(row);
        }
        return series;
    }

    private Map<String, Object> toLatestSnapshot(JvmMetric m) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("timestamp", m.getTimestamp());
        snap.put("serviceInstance", m.getServiceInstance());
        snap.put("hostIp", m.getHostIp());
        snap.put("hostPort", m.getHostPort());
        snap.put("heapMemoryUsed", m.getHeapMemoryUsed());
        snap.put("heapMemoryMax", m.getHeapMemoryMax());
        snap.put("heapMemoryCommitted", m.getHeapMemoryCommitted());
        snap.put("nonHeapMemoryUsed", m.getNonHeapMemoryUsed());
        snap.put("gcCount", m.getGcCount());
        snap.put("gcTime", m.getGcTime());
        snap.put("activeThreads", m.getActiveThreads());
        snap.put("peakThreads", m.getPeakThreads());
        snap.put("daemonThreads", m.getDaemonThreads());
        snap.put("jvmName", m.getJvmName());
        snap.put("javaVersion", m.getJavaVersion());
        snap.put("availableProcessors", m.getAvailableProcessors());
        snap.put("systemLoadAverage", m.getSystemLoadAverage());
        return snap;
    }
}
