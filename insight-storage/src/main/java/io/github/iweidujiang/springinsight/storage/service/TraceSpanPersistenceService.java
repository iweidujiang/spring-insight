package io.github.iweidujiang.springinsight.storage.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.storage.entity.TraceSpanDO;
import io.github.iweidujiang.springinsight.storage.mapper.TraceSpanMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ┌───────────────────────────────────────────────┐
 * │ 📦 链路追踪数据持久化服务
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/9
 * └───────────────────────────────────────────────┘
 */
@Slf4j
@Service
public class TraceSpanPersistenceService extends ServiceImpl<TraceSpanMapper, TraceSpanDO> {

    /**
     * 保存单个 TraceSpan
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveTraceSpan(TraceSpan span) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        try {
            TraceSpanDO entity = TraceSpanDO.fromModel(span);
            boolean success = this.save(entity);

            stopWatch.stop();

            if (success) {
                log.debug("成功保存追踪链路：traceId={}, spanId={}, 操作名称={}, 耗时={}毫秒, 数据库耗时={}毫秒",
                        span.getTraceId(),
                        span.getSpanId(),
                        span.getOperationName(),
                        span.getDurationMs(),
                        stopWatch.getTotalTimeMillis());
            } else {
                log.warn("保存追踪链路失败: traceId={}, spanId={}",
                        span.getTraceId(), span.getSpanId());
            }
        } catch (Exception e) {
            stopWatch.stop();
            log.error("保存追踪链路失败: traceId={}, spanId={}, error={}",
                    span.getTraceId(), span.getSpanId(), e.getMessage(), e);
            throw new RuntimeException("保存追踪链路失败", e);
        }
    }

    /**
     * 批量保存 TraceSpan（性能优化）
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveTraceSpans(List<TraceSpan> spans) {
        if (spans == null || spans.isEmpty()) {
            log.debug("链路trace spans列表为空，跳过批量保存");
            return;
        }

        StopWatch stopWatch = new StopWatch();
        stopWatch.start("convert-to-entities");

        try {
            // 转换为实体列表
            List<TraceSpanDO> entities = spans.stream()
                    .map(TraceSpanDO::fromModel)
                    .collect(Collectors.toList());

            stopWatch.stop();
            stopWatch.start("batch-save-to-db");

            // 批量保存，每批1000条
            boolean success = this.saveBatch(entities, 1000);

            stopWatch.stop();

            if (success) {
                log.info("成功保存了 {} 条链路追踪, totalTime={}ms, convertTime={}ms, dbTime={}ms",
                        spans.size(),
                        stopWatch.getTotalTimeMillis(),
                        stopWatch.getTaskInfo()[0].getTimeMillis(),
                        stopWatch.getTaskInfo()[1].getTimeMillis());
            } else {
                log.warn("{} 条追踪链路的批量保存可能失败", spans.size());
            }

        } catch (Exception e) {
            stopWatch.stop();
            log.error("批量保存 {} 条追踪链路失败：{}", spans.size(), e.getMessage(), e);
            throw new RuntimeException("批量保存追踪链路失败", e);
        }
    }

    /**
     * 根据 TraceID 获取完整的调用链
     */
    public List<TraceSpan> getTraceById(String traceId) {
        log.debug("根据ID获取追踪链路：{}", traceId);

        try {
            List<TraceSpanDO> entities = baseMapper.selectByTraceId(traceId);

            List<TraceSpan> result = entities.stream()
                    .map(TraceSpanDO::toModel)
                    .collect(Collectors.toList());

            log.debug("为traceId：{}找到{}条链路追踪", result.size(), traceId);
            return result;

        } catch (Exception e) {
            log.error("根据ID获取追踪链路失败：{}，错误信息：{}", traceId, e.getMessage(), e);
            throw new RuntimeException("获取追踪链路失败", e);
        }
    }

    /**
     * 获取最近 N 小时的 Span（用于调试和测试）
     */
    public List<TraceSpan> getRecentSpans(int lastHours, int limit) {
        Long sinceTime = Instant.now().minus(lastHours, ChronoUnit.HOURS).toEpochMilli();
        log.debug("获取自 {} 以来的近期链路（最近 {} 小时），限制条数：{}", sinceTime, lastHours, limit);

        try {
            List<TraceSpanDO> entities = baseMapper.selectRecentSpans(sinceTime, limit);

            List<TraceSpan> result = entities.stream()
                    .map(TraceSpanDO::toModel)
                    .collect(Collectors.toList());

            log.debug("找到 {} 条近期链路追踪", result.size());
            return result;

        } catch (Exception e) {
            log.error("获取近期链路追踪失败：{}", e.getMessage(), e);
            throw new RuntimeException("获取近期链路追踪失败", e);
        }
    }

    /**
     * 获取所有服务名称
     */
    public List<String> getAllServiceNames() {
        log.debug("获取所有服务名称");

        try {
            List<String> serviceNames = baseMapper.selectAllServiceNames();
            log.debug("找到 {} 个服务名称", serviceNames.size());
            return serviceNames;

        } catch (Exception e) {
            log.error("获取服务名称失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取服务名称失败", e);
        }
    }

    /**
     * 获取服务依赖关系（用于拓扑图）
     */
    public List<Map<String, Object>> getServiceDependencies(int lastHours) {
        Long sinceTime = Instant.now().minus(lastHours, ChronoUnit.HOURS).toEpochMilli();
        log.debug("获取服务依赖关系，时间范围：{}（最近 {} 小时）", sinceTime, lastHours);

        try {
            List<Map<String, Object>> dependencies = baseMapper.selectServiceDependencies(sinceTime);
            log.debug("查询到 {} 条服务依赖关系", dependencies.size());
            return dependencies;

        } catch (Exception e) {
            log.error("获取服务依赖关系失败：{}", e.getMessage(), e);
            throw new RuntimeException("获取服务依赖关系失败", e);
        }
    }

    /**
     * 统计各服务的 Span 数量
     */
    public List<Map<String, Object>> getSpanCountByService() {
        log.debug("统计各服务的链路跨度数量");

        try {
            List<Map<String, Object>> counts = baseMapper.countSpansByService();
            log.debug("获取到 {} 个服务的链路跨度统计数据", counts.size());
            return counts;

        } catch (Exception e) {
            log.error("统计各服务的链路跨度数量失败：{}", e.getMessage(), e);
            throw new RuntimeException("获取链路跨度统计数据失败", e);
        }
    }

    /**
     * 查找错误率高的服务
     */
    public List<Map<String, Object>> findHighErrorServices(int lastHours) {
        Long sinceTime = Instant.now().minus(lastHours, ChronoUnit.HOURS).toEpochMilli();
        log.debug("查找高错误率服务，时间范围：{}（最近 {} 小时）", sinceTime, lastHours);

        try {
            List<Map<String, Object>> errorStats = baseMapper.findHighErrorServices(sinceTime);
            log.debug("查询到 {} 个存在错误的服务", errorStats.size());
            return errorStats;

        } catch (Exception e) {
            log.error("查找高错误率服务失败：{}", e.getMessage(), e);
            throw new RuntimeException("查找高错误率服务失败", e);
        }
    }

    /**
     * 查询指定服务的最近记录
     */
    public List<TraceSpan> getRecentSpansByService(String serviceName, int limit) {
        log.debug("获取指定服务的近期链路跨度，服务名：{}，限制条数：{}", serviceName, limit);

        try {
            List<TraceSpanDO> entities = baseMapper.selectRecentByService(serviceName, limit);

            List<TraceSpan> result = entities.stream()
                    .map(TraceSpanDO::toModel)
                    .collect(Collectors.toList());

            log.debug("为服务 {} 查询到 {} 条近期链路跨度", serviceName, result.size());
            return result;

        } catch (Exception e) {
            log.error("获取服务 {} 的近期链路跨度失败：{}", serviceName, e.getMessage(), e);
            throw new RuntimeException("获取指定服务的近期链路跨度失败", e);
        }
    }
}
