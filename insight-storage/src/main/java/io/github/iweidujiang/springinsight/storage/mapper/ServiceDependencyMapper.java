package io.github.iweidujiang.springinsight.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.iweidujiang.springinsight.storage.entity.ServiceDependencyDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 服务依赖关系Mapper接口
 * 对应数据库表: insight_service_dependencies
 */
@Mapper
public interface ServiceDependencyMapper extends BaseMapper<ServiceDependencyDO> {

    /**
     * 查询指定时间范围内的服务依赖关系
     */
    List<ServiceDependencyDO> selectByTimeRange(
            @Param("startTime") Long startTime,
            @Param("endTime") Long endTime);

    /**
     * 查询最近N小时的服务依赖关系
     */
    List<ServiceDependencyDO> selectRecentDependencies(@Param("sinceTime") Long sinceTime);

    /**
     * 获取服务之间的调用关系（用于拓扑图）
     * 返回：调用方服务、被调用方服务、调用次数、平均耗时
     */
    List<Map<String, Object>> selectServiceDependencyGraph(@Param("sinceTime") Long sinceTime);

    /**
     * 根据源服务和目标服务查询依赖关系
     */
    ServiceDependencyDO selectBySourceAndTarget(
            @Param("sourceService") String sourceService,
            @Param("targetService") String targetService,
            @Param("windowStartTime") Long windowStartTime);

    /**
     * 批量更新或插入服务依赖关系
     */
    int batchUpsert(List<ServiceDependencyDO> dependencies);

    /**
     * 删除指定时间范围之前的数据（用于数据清理）
     */
    int deleteBeforeTime(@Param("time") Long time);
}