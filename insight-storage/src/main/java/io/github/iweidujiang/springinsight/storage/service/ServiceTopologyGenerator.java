package io.github.iweidujiang.springinsight.storage.service;

import io.github.iweidujiang.springinsight.storage.entity.ServiceDependencyDO;
import io.github.iweidujiang.springinsight.storage.mapper.ServiceDependencyMapper;
import io.github.iweidujiang.springinsight.storage.mapper.TraceSpanMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务拓扑生成器
 * 从TraceSpan数据中提取服务依赖关系，生成服务拓扑图
 */
@Slf4j
@Service
public class ServiceTopologyGenerator {

    private final TraceSpanMapper traceSpanMapper;
    private final ServiceDependencyMapper serviceDependencyMapper;
    private final ServiceDependencyService serviceDependencyService;

    public ServiceTopologyGenerator(TraceSpanMapper traceSpanMapper, ServiceDependencyMapper serviceDependencyMapper, ServiceDependencyService serviceDependencyService) {
        this.traceSpanMapper = traceSpanMapper;
        this.serviceDependencyMapper = serviceDependencyMapper;
        this.serviceDependencyService = serviceDependencyService;
        log.info("[服务拓扑生成器] 初始化完成");
    }

    /**
     * 生成服务拓扑
     * @param lastHours 统计最近几小时的数据
     */
    @Transactional(rollbackFor = Exception.class)
    public void generateTopology(int lastHours) {
        log.info("[服务拓扑生成器] 开始生成服务拓扑，统计最近 {} 小时的数据", lastHours);
        
        long startTime = System.currentTimeMillis();
        long sinceTime = Instant.now().minus(lastHours, ChronoUnit.HOURS).toEpochMilli();
        
        try {
            // 1. 获取最近N小时的服务依赖关系数据
            List<Map<String, Object>> dependencyGraph = traceSpanMapper.selectServiceDependencies(sinceTime);
            log.debug("[服务拓扑生成器] 查询到 {} 条服务依赖关系数据", dependencyGraph.size());
            
            // 2. 聚合服务依赖关系
            List<ServiceDependencyDO> aggregatedDependencies = aggregateDependencies(dependencyGraph, sinceTime);
            log.debug("[服务拓扑生成器] 聚合后得到 {} 条服务依赖关系", aggregatedDependencies.size());
            
            // 3. 保存聚合结果
            if (!aggregatedDependencies.isEmpty()) {
                serviceDependencyService.saveDependencies(aggregatedDependencies);
                log.info("[服务拓扑生成器] 成功保存服务依赖关系: {}", aggregatedDependencies.size());
            }
            
            long endTime = System.currentTimeMillis();
            log.info("[服务拓扑生成器] 服务拓扑生成完成，耗时: {}ms", endTime - startTime);
        } catch (Exception e) {
            log.error("[服务拓扑生成器] 生成服务拓扑失败: {}", e.getMessage(), e);
            throw new RuntimeException("生成服务拓扑失败", e);
        }
    }
    
    /**
     * 聚合服务依赖关系
     */
    private List<ServiceDependencyDO> aggregateDependencies(List<Map<String, Object>> dependencyGraph, long sinceTime) {
        // 使用ConcurrentHashMap进行并行聚合
        Map<String, ServiceDependencyDO> dependencyMap = new ConcurrentHashMap<>();
        
        // 遍历服务依赖关系数据
        for (Map<String, Object> dependency : dependencyGraph) {
            String sourceService = (String) dependency.get("source_service");
            String targetService = (String) dependency.get("target_service");
            Long callCount = (Long) dependency.get("call_count");
            Double avgDuration = (Double) dependency.get("avg_duration");
            
            // 跳过空值
            if (sourceService == null || targetService == null || callCount == null) {
                continue;
            }
            
            // 生成依赖关系的唯一键
            String key = sourceService + "->" + targetService;
            
            // 聚合依赖关系
            dependencyMap.compute(key, (k, v) -> {
                if (v == null) {
                    ServiceDependencyDO dependencyDO = new ServiceDependencyDO();
                    dependencyDO.setSourceService(sourceService);
                    dependencyDO.setTargetService(targetService);
                    dependencyDO.setTotalCalls(callCount);
                    dependencyDO.setAvgDurationMs(avgDuration != null ? avgDuration : 0.0);
                    dependencyDO.setSuccessCalls(callCount); // 暂时假设所有调用都是成功的，后续可以根据实际数据调整
                    dependencyDO.setErrorCalls(0L);
                    dependencyDO.setStatWindow("hour");
                    dependencyDO.setWindowStartTime(sinceTime);
                    dependencyDO.setWindowEndTime(System.currentTimeMillis());
                    dependencyDO.setCalVersion(1);
                    return dependencyDO;
                } else {
                    // 累加调用次数
                    v.setTotalCalls(v.getTotalCalls() + callCount);
                    v.setSuccessCalls(v.getSuccessCalls() + callCount); // 暂时假设所有调用都是成功的
                    // 更新平均耗时
                    double newAvgDuration = (v.getAvgDurationMs() * v.getTotalCalls() + (avgDuration != null ? avgDuration : 0.0) * callCount) / v.getTotalCalls();
                    v.setAvgDurationMs(newAvgDuration);
                    return v;
                }
            });
        }
        
        return new ArrayList<>(dependencyMap.values());
    }
    
    /**
     * 清理过期的服务依赖关系数据
     * @param retentionHours 保留最近几小时的数据
     */
    @Transactional(rollbackFor = Exception.class)
    public void cleanExpiredDependencies(int retentionHours) {
        log.info("[服务拓扑生成器] 开始清理过期的服务依赖关系数据，保留最近 {} 小时的数据", retentionHours);
        
        long cutoffTime = Instant.now().minus(retentionHours, ChronoUnit.HOURS).toEpochMilli();
        
        try {
            int deletedCount = serviceDependencyMapper.deleteBeforeTime(cutoffTime);
            log.info("[服务拓扑生成器] 已清理 {} 条过期的服务依赖关系数据", deletedCount);
        } catch (Exception e) {
            log.error("[服务拓扑生成器] 清理过期服务依赖关系数据失败: {}", e.getMessage(), e);
            throw new RuntimeException("清理过期服务依赖关系数据失败", e);
        }
    }
    
    /**
     * 获取服务拓扑图数据
     * @param lastHours 统计最近几小时的数据
     * @return 服务拓扑图数据
     */
    public List<Map<String, Object>> getTopologyGraph(int lastHours) {
        log.info("[服务拓扑生成器] 获取服务拓扑图数据，统计最近 {} 小时的数据", lastHours);
        
        long sinceTime = Instant.now().minus(lastHours, ChronoUnit.HOURS).toEpochMilli();
        
        try {
            // 从TraceSpan中获取最新的服务依赖关系
            List<Map<String, Object>> dependencyGraph = traceSpanMapper.selectServiceDependencies(sinceTime);
            log.debug("[服务拓扑生成器] 获取到 {} 条服务依赖关系数据", dependencyGraph.size());
            
            // 转换为前端需要的格式
            return convertToGraphFormat(dependencyGraph);
        } catch (Exception e) {
            log.error("[服务拓扑生成器] 获取服务拓扑图数据失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取服务拓扑图数据失败", e);
        }
    }
    
    /**
     * 转换为前端需要的拓扑图格式
     */
    private List<Map<String, Object>> convertToGraphFormat(List<Map<String, Object>> dependencyGraph) {
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (Map<String, Object> dependency : dependencyGraph) {
            String sourceService = (String) dependency.get("source_service");
            String targetService = (String) dependency.get("target_service");
            Long callCount = (Long) dependency.get("call_count");
            Double avgDuration = (Double) dependency.get("avg_duration");
            
            Map<String, Object> edge = new HashMap<>();
            edge.put("source", sourceService);
            edge.put("target", targetService);
            edge.put("callCount", callCount);
            edge.put("avgDuration", avgDuration);
            
            result.add(edge);
        }
        
        return result;
    }
    
    /**
     * 获取服务节点列表
     * @param lastHours 统计最近几小时的数据
     * @return 服务节点列表
     */
    public List<String> getServiceNodes(int lastHours) {
        log.info("[服务拓扑生成器] 获取服务节点列表，统计最近 {} 小时的数据", lastHours);
        
        long sinceTime = Instant.now().minus(lastHours, ChronoUnit.HOURS).toEpochMilli();
        
        try {
            // 从TraceSpan中获取服务名称
            List<Map<String, Object>> dependencyGraph = traceSpanMapper.selectServiceDependencies(sinceTime);
            
            // 提取唯一的服务名称
            Set<String> serviceNodes = new HashSet<>();
            for (Map<String, Object> dependency : dependencyGraph) {
                String sourceService = (String) dependency.get("source_service");
                String targetService = (String) dependency.get("target_service");
                if (sourceService != null) {
                    serviceNodes.add(sourceService);
                }
                if (targetService != null) {
                    serviceNodes.add(targetService);
                }
            }
            
            // 转换为列表并排序
            List<String> result = new ArrayList<>(serviceNodes);
            Collections.sort(result);
            
            log.debug("[服务拓扑生成器] 获取到 {} 个服务节点", result.size());
            return result;
        } catch (Exception e) {
            log.error("[服务拓扑生成器] 获取服务节点列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取服务节点列表失败", e);
        }
    }
}