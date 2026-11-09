package io.github.iweidujiang.springinsight.server.jvm;

import io.github.iweidujiang.springinsight.agent.model.JvmMetric;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 进程内 JVM 指标环形缓冲：按服务+实例分桶，超限丢最旧点。
 *
 * @author 苏渡苇
 * @since 2026-11-09
 */
@Slf4j
public class InMemoryJvmMetricStore implements JvmMetricStore {

    /** 单实例最多保留的点数（约 30s 间隔 × 24h） */
    static final int MAX_POINTS_PER_INSTANCE = 2880;

    /** 默认保留窗口（毫秒） */
    static final long RETENTION_MS = 72L * 60 * 60 * 1000;

    private final Map<String, ConcurrentLinkedDeque<JvmMetric>> buckets = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveAll(String serviceName, String serviceInstance, List<JvmMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return;
        }
        String svc = normalize(serviceName);
        String inst = normalize(serviceInstance);
        if (svc.isEmpty() || inst.isEmpty()) {
            log.warn("[JVM存储] 忽略无效批次: service={}, instance={}", serviceName, serviceInstance);
            return;
        }
        ConcurrentLinkedDeque<JvmMetric> deque = buckets.computeIfAbsent(key(svc, inst), k -> new ConcurrentLinkedDeque<>());
        long now = System.currentTimeMillis();
        for (JvmMetric raw : metrics) {
            if (raw == null) {
                continue;
            }
            JvmMetric point = copyFilled(raw, svc, inst);
            if (point.getTimestamp() == null || point.getTimestamp() <= 0) {
                point.setTimestamp(now);
            }
            deque.addLast(point);
        }
        trim(deque, now);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> listInstances(String serviceName) {
        String svc = normalize(serviceName);
        if (svc.isEmpty()) {
            return List.of();
        }
        String prefix = svc + "|";
        return buckets.keySet().stream()
                .filter(k -> k.startsWith(prefix))
                .map(k -> k.substring(prefix.length()))
                .sorted()
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<JvmMetric> query(String serviceName, String instance, long fromMs, long toMs) {
        String svc = normalize(serviceName);
        if (svc.isEmpty()) {
            return List.of();
        }
        List<JvmMetric> out = new ArrayList<>();
        String instFilter = normalize(instance);
        for (Map.Entry<String, ConcurrentLinkedDeque<JvmMetric>> e : buckets.entrySet()) {
            String key = e.getKey();
            if (!key.startsWith(svc + "|")) {
                continue;
            }
            String inst = key.substring(svc.length() + 1);
            if (!instFilter.isEmpty() && !instFilter.equals(inst)) {
                continue;
            }
            for (JvmMetric m : e.getValue()) {
                long ts = m.getTimestamp() != null ? m.getTimestamp() : 0L;
                if (ts >= fromMs && ts <= toMs) {
                    out.add(m);
                }
            }
        }
        out.sort(Comparator.comparingLong(m -> m.getTimestamp() != null ? m.getTimestamp() : 0L));
        return out;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<JvmMetric> latest(String serviceName, String instance) {
        List<JvmMetric> points = query(serviceName, instance, 0L, Long.MAX_VALUE);
        if (points.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(points.get(points.size() - 1));
    }

    private void trim(ConcurrentLinkedDeque<JvmMetric> deque, long now) {
        long cutoff = now - RETENTION_MS;
        while (!deque.isEmpty()) {
            JvmMetric head = deque.peekFirst();
            if (head == null) {
                break;
            }
            long ts = head.getTimestamp() != null ? head.getTimestamp() : 0L;
            if (ts >= cutoff && deque.size() <= MAX_POINTS_PER_INSTANCE) {
                break;
            }
            if (ts < cutoff || deque.size() > MAX_POINTS_PER_INSTANCE) {
                deque.pollFirst();
            } else {
                break;
            }
        }
        while (deque.size() > MAX_POINTS_PER_INSTANCE) {
            deque.pollFirst();
        }
    }

    private static JvmMetric copyFilled(JvmMetric src, String svc, String inst) {
        JvmMetric m = new JvmMetric();
        m.setServiceName(blankTo(src.getServiceName(), svc));
        m.setServiceInstance(blankTo(src.getServiceInstance(), inst));
        m.setHostIp(src.getHostIp());
        m.setHostPort(src.getHostPort());
        m.setTimestamp(src.getTimestamp());
        m.setHeapMemoryUsed(src.getHeapMemoryUsed());
        m.setHeapMemoryMax(src.getHeapMemoryMax());
        m.setHeapMemoryCommitted(src.getHeapMemoryCommitted());
        m.setHeapMemoryInit(src.getHeapMemoryInit());
        m.setNonHeapMemoryUsed(src.getNonHeapMemoryUsed());
        m.setNonHeapMemoryMax(src.getNonHeapMemoryMax());
        m.setNonHeapMemoryCommitted(src.getNonHeapMemoryCommitted());
        m.setNonHeapMemoryInit(src.getNonHeapMemoryInit());
        m.setGcCount(src.getGcCount());
        m.setGcTime(src.getGcTime());
        m.setYoungGcCount(src.getYoungGcCount());
        m.setYoungGcTime(src.getYoungGcTime());
        m.setOldGcCount(src.getOldGcCount());
        m.setOldGcTime(src.getOldGcTime());
        m.setActiveThreads(src.getActiveThreads());
        m.setPeakThreads(src.getPeakThreads());
        m.setDaemonThreads(src.getDaemonThreads());
        m.setTotalThreads(src.getTotalThreads());
        m.setLoadedClasses(src.getLoadedClasses());
        m.setUnloadedClasses(src.getUnloadedClasses());
        m.setJvmName(src.getJvmName());
        m.setJvmVersion(src.getJvmVersion());
        m.setJvmVendor(src.getJvmVendor());
        m.setJavaVersion(src.getJavaVersion());
        m.setSystemLoadAverage(src.getSystemLoadAverage());
        m.setAvailableProcessors(src.getAvailableProcessors());
        return m;
    }

    private static String key(String service, String instance) {
        return service + "|" + instance;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
