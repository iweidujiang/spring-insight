package io.github.iweidujiang.springinsight.storage.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.github.iweidujiang.springinsight.storage.entity.ServiceMetricDO;
import io.github.iweidujiang.springinsight.storage.mapper.ServiceMetricMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * 应用指标聚合服务层
 * 处理应用指标的业务逻辑
 */
@Slf4j
@Service
public class ServiceMetricService extends ServiceImpl<ServiceMetricMapper, ServiceMetricDO> {

    /**
     * 保存单个指标数据
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveMetric(ServiceMetricDO metric) {
        try {
            boolean success = this.save(metric);
            if (success) {
                log.debug("成功保存指标数据: {} - {}, QPS={}, 平均延迟={}ms",
                        metric.getServiceName(),
                        metric.getOperationName(),
                        metric.getQps(),
                        metric.getAvgLatencyMs());
            } else {
                log.warn("保存指标数据失败: {} - {}",
                        metric.getServiceName(),
                        metric.getOperationName());
            }
        } catch (Exception e) {
            log.error("保存指标数据失败: {} - {}, 错误: {}",
                    metric.getServiceName(),
                    metric.getOperationName(),
                    e.getMessage(), e);
            throw new RuntimeException("保存指标数据失败", e);
        }
    }

    /**
     * 批量保存指标数据
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveMetrics(List<ServiceMetricDO> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            log.debug("指标数据列表为空，跳过批量保存");
            return;
        }

        try {
            // 批量保存，每批1000条
            boolean success = this.saveBatch(metrics, 1000);
            if (success) {
                log.info("成功保存了 {} 条指标数据", metrics.size());
            } else {
                log.warn("{} 条指标数据的批量保存可能失败", metrics.size());
            }
        } catch (Exception e) {
            log.error("批量保存指标数据失败：{}", e.getMessage(), e);
            throw new RuntimeException("批量保存指标数据失败", e);
        }
    }

    /**
     * 查询指定服务的指标数据
     */
    public List<ServiceMetricDO> getMetricsByService(String serviceName) {
        log.debug("查询服务 {} 的指标数据", serviceName);

        try {
            List<ServiceMetricDO> metrics = baseMapper.selectByServiceName(serviceName);
            log.debug("查询到 {} 条指标数据", metrics.size());
            return metrics;
        } catch (Exception e) {
            log.error("查询服务指标数据失败：{}", e.getMessage(), e);
            throw new RuntimeException("查询服务指标数据失败", e);
        }
    }

    /**
     * 查询指定服务和操作的指标数据
     */
    public List<ServiceMetricDO> getMetricsByServiceAndOperation(String serviceName, String operationName) {
        log.debug("查询服务 {} 操作 {} 的指标数据", serviceName, operationName);

        try {
            List<ServiceMetricDO> metrics = baseMapper.selectByServiceAndOperation(serviceName, operationName);
            log.debug("查询到 {} 条指标数据", metrics.size());
            return metrics;
        } catch (Exception e) {
            log.error("查询服务操作指标数据失败：{}", e.getMessage(), e);
            throw new RuntimeException("查询服务操作指标数据失败", e);
        }
    }

    /**
     * 查询最近N小时的指标数据
     */
    public List<ServiceMetricDO> getRecentMetrics(int lastHours, String metricType) {
        LocalDateTime sinceTime = LocalDateTime.now().minusHours(lastHours);
        log.debug("获取最近 {} 小时的指标数据，类型：{}，从 {} 开始", lastHours, metricType, sinceTime);

        try {
            List<ServiceMetricDO> metrics = baseMapper.selectRecentMetrics(sinceTime, metricType);
            log.debug("查询到 {} 条指标数据", metrics.size());
            return metrics;
        } catch (Exception e) {
            log.error("获取最近指标数据失败：{}", e.getMessage(), e);
            throw new RuntimeException("获取最近指标数据失败", e);
        }
    }

    /**
     * 获取服务的关键指标摘要
     */
    public List<Map<String, Object>> getServiceMetricSummary(int lastHours) {
        LocalDateTime sinceTime = LocalDateTime.now().minusHours(lastHours);
        log.debug("获取最近 {} 小时的服务指标摘要", lastHours);

        try {
            List<Map<String, Object>> summary = baseMapper.selectServiceMetricSummary(sinceTime);
            log.debug("查询到 {} 条服务指标摘要", summary.size());
            return summary;
        } catch (Exception e) {
            log.error("获取服务指标摘要失败：{}", e.getMessage(), e);
            throw new RuntimeException("获取服务指标摘要失败", e);
        }
    }

    /**
     * 获取服务的错误率统计
     */
    public List<Map<String, Object>> getServiceErrorRates(int lastHours) {
        LocalDateTime sinceTime = LocalDateTime.now().minusHours(lastHours);
        log.debug("获取最近 {} 小时的服务错误率统计", lastHours);

        try {
            List<Map<String, Object>> errorRates = baseMapper.selectServiceErrorRates(sinceTime);
            log.debug("查询到 {} 条服务错误率统计", errorRates.size());
            return errorRates;
        } catch (Exception e) {
            log.error("获取服务错误率统计失败：{}", e.getMessage(), e);
            throw new RuntimeException("获取服务错误率统计失败", e);
        }
    }

    /**
     * 获取服务的延迟统计
     */
    public List<Map<String, Object>> getServiceLatencyStats(int lastHours) {
        LocalDateTime sinceTime = LocalDateTime.now().minusHours(lastHours);
        log.debug("获取最近 {} 小时的服务延迟统计", lastHours);

        try {
            List<Map<String, Object>> latencyStats = baseMapper.selectServiceLatencyStats(sinceTime);
            log.debug("查询到 {} 条服务延迟统计", latencyStats.size());
            return latencyStats;
        } catch (Exception e) {
            log.error("获取服务延迟统计失败：{}", e.getMessage(), e);
            throw new RuntimeException("获取服务延迟统计失败", e);
        }
    }

    /**
     * 删除指定时间之前的数据
     */
    @Transactional(rollbackFor = Exception.class)
    public void cleanDataBefore(Long time) {
        LocalDateTime cutoffTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault());
        log.debug("清理 {} 之前的指标数据", cutoffTime);

        try {
            int deletedCount = baseMapper.deleteBeforeTime(cutoffTime);
            log.info("已清理 {} 条指标数据", deletedCount);
        } catch (Exception e) {
            log.error("清理指标数据失败：{}", e.getMessage(), e);
            throw new RuntimeException("清理指标数据失败", e);
        }
    }
}