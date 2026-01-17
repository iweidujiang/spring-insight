package io.github.iweidujiang.springinsight.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.iweidujiang.springinsight.storage.entity.ServiceMetricDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 应用指标聚合Mapper接口
 * 对应数据库表: insight_service_metrics
 */
@Mapper
public interface ServiceMetricMapper extends BaseMapper<ServiceMetricDO> {

    /**
     * 查询指定服务的指标数据
     */
    List<ServiceMetricDO> selectByServiceName(@Param("serviceName") String serviceName);

    /**
     * 查询指定服务和操作的指标数据
     */
    List<ServiceMetricDO> selectByServiceAndOperation(
            @Param("serviceName") String serviceName,
            @Param("operationName") String operationName);

    /**
     * 查询指定时间范围内的指标数据
     */
    List<ServiceMetricDO> selectByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 查询指定服务和时间范围的指标数据
     */
    List<ServiceMetricDO> selectByServiceAndTimeRange(
            @Param("serviceName") String serviceName,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 查询最近N小时的指标数据
     */
    List<ServiceMetricDO> selectRecentMetrics(
            @Param("sinceTime") LocalDateTime sinceTime,
            @Param("metricType") String metricType);

    /**
     * 获取服务的关键指标摘要
     */
    List<Map<String, Object>> selectServiceMetricSummary(
            @Param("sinceTime") LocalDateTime sinceTime);

    /**
     * 获取服务的错误率统计
     */
    List<Map<String, Object>> selectServiceErrorRates(
            @Param("sinceTime") LocalDateTime sinceTime);

    /**
     * 获取服务的延迟统计
     */
    List<Map<String, Object>> selectServiceLatencyStats(
            @Param("sinceTime") LocalDateTime sinceTime);

    /**
     * 批量更新或插入指标数据
     */
    int batchUpsert(List<ServiceMetricDO> metrics);

    /**
     * 删除指定时间范围之前的数据（用于数据清理）
     */
    int deleteBeforeTime(@Param("time") LocalDateTime time);
}