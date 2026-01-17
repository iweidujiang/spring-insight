package io.github.iweidujiang.springinsight.storage.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.github.iweidujiang.springinsight.storage.entity.ServiceDependencyDO;
import io.github.iweidujiang.springinsight.storage.mapper.ServiceDependencyMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * 服务依赖关系服务层
 * 处理服务依赖关系的业务逻辑
 */
@Slf4j
@Service
public class ServiceDependencyService extends ServiceImpl<ServiceDependencyMapper, ServiceDependencyDO> {

    /**
     * 保存单个服务依赖关系
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveDependency(ServiceDependencyDO dependency) {
        try {
            boolean success = this.save(dependency);
            if (success) {
                log.debug("成功保存服务依赖关系: {} -> {}, 调用次数={}",
                        dependency.getSourceService(),
                        dependency.getTargetService(),
                        dependency.getTotalCalls());
            } else {
                log.warn("保存服务依赖关系失败: {} -> {}",
                        dependency.getSourceService(),
                        dependency.getTargetService());
            }
        } catch (Exception e) {
            log.error("保存服务依赖关系失败: {} -> {}, 错误: {}",
                    dependency.getSourceService(),
                    dependency.getTargetService(),
                    e.getMessage(), e);
            throw new RuntimeException("保存服务依赖关系失败", e);
        }
    }

    /**
     * 批量保存服务依赖关系
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveDependencies(List<ServiceDependencyDO> dependencies) {
        if (dependencies == null || dependencies.isEmpty()) {
            log.debug("服务依赖关系列表为空，跳过批量保存");
            return;
        }

        try {
            // 批量保存，每批1000条
            boolean success = this.saveBatch(dependencies, 1000);
            if (success) {
                log.info("成功保存了 {} 条服务依赖关系", dependencies.size());
            } else {
                log.warn("{} 条服务依赖关系的批量保存可能失败", dependencies.size());
            }
        } catch (Exception e) {
            log.error("批量保存服务依赖关系失败：{}", e.getMessage(), e);
            throw new RuntimeException("批量保存服务依赖关系失败", e);
        }
    }

    /**
     * 查询最近N小时的服务依赖关系
     */
    public List<ServiceDependencyDO> getRecentDependencies(int lastHours) {
        Long sinceTime = Instant.now().minus(lastHours, ChronoUnit.HOURS).toEpochMilli();
        log.debug("获取最近 {} 小时的服务依赖关系，从 {} 开始", lastHours, sinceTime);

        try {
            List<ServiceDependencyDO> dependencies = baseMapper.selectRecentDependencies(sinceTime);
            log.debug("查询到 {} 条服务依赖关系", dependencies.size());
            return dependencies;
        } catch (Exception e) {
            log.error("获取服务依赖关系失败：{}", e.getMessage(), e);
            throw new RuntimeException("获取服务依赖关系失败", e);
        }
    }

    /**
     * 获取服务依赖关系图数据
     */
    public List<Map<String, Object>> getServiceDependencyGraph(int lastHours) {
        Long sinceTime = Instant.now().minus(lastHours, ChronoUnit.HOURS).toEpochMilli();
        log.debug("获取服务依赖关系图数据，时间范围：最近 {} 小时", lastHours);

        try {
            List<Map<String, Object>> dependencyGraph = baseMapper.selectServiceDependencyGraph(sinceTime);
            log.debug("查询到 {} 条服务依赖关系图数据", dependencyGraph.size());
            return dependencyGraph;
        } catch (Exception e) {
            log.error("获取服务依赖关系图数据失败：{}", e.getMessage(), e);
            throw new RuntimeException("获取服务依赖关系图数据失败", e);
        }
    }

    /**
     * 根据源服务和目标服务查询依赖关系
     */
    public ServiceDependencyDO getDependencyBySourceAndTarget(String sourceService, String targetService, Long windowStartTime) {
        log.debug("查询服务依赖关系: {} -> {}, 时间窗口: {}", sourceService, targetService, windowStartTime);

        try {
            return baseMapper.selectBySourceAndTarget(sourceService, targetService, windowStartTime);
        } catch (Exception e) {
            log.error("查询服务依赖关系失败: {} -> {}, 错误: {}",
                    sourceService, targetService, e.getMessage(), e);
            throw new RuntimeException("查询服务依赖关系失败", e);
        }
    }

    /**
     * 删除指定时间之前的数据
     */
    @Transactional(rollbackFor = Exception.class)
    public void cleanDataBefore(Long time) {
        log.debug("清理 {} 之前的服务依赖关系数据", time);

        try {
            int deletedCount = baseMapper.deleteBeforeTime(time);
            log.info("已清理 {} 条服务依赖关系数据", deletedCount);
        } catch (Exception e) {
            log.error("清理服务依赖关系数据失败：{}", e.getMessage(), e);
            throw new RuntimeException("清理服务依赖关系数据失败", e);
        }
    }
}