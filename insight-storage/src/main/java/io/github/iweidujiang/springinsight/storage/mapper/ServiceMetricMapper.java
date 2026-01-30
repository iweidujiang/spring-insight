package io.github.iweidujiang.springinsight.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.iweidujiang.springinsight.storage.entity.ServiceMetricDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

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
    @Select("SELECT * FROM insight_service_metrics WHERE service_name = #{serviceName} ORDER BY window_time DESC")
    List<ServiceMetricDO> selectByServiceName(@Param("serviceName") String serviceName);

    /**
     * 查询指定服务和操作的指标数据
     */
    @Select("SELECT * FROM insight_service_metrics WHERE service_name = #{serviceName} AND operation_name = #{operationName} ORDER BY window_time DESC")
    List<ServiceMetricDO> selectByServiceAndOperation(
            @Param("serviceName") String serviceName,
            @Param("operationName") String operationName);

    /**
     * 查询指定时间范围内的指标数据
     */
    @Select("SELECT * FROM insight_service_metrics WHERE window_time BETWEEN #{startTime} AND #{endTime} ORDER BY window_time DESC")
    List<ServiceMetricDO> selectByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 查询指定服务和时间范围的指标数据
     */
    @Select("SELECT * FROM insight_service_metrics WHERE service_name = #{serviceName} AND window_time BETWEEN #{startTime} AND #{endTime} ORDER BY window_time DESC")
    List<ServiceMetricDO> selectByServiceAndTimeRange(
            @Param("serviceName") String serviceName,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 查询最近N小时的指标数据
     */
    @Select("SELECT * FROM insight_service_metrics WHERE window_time >= #{sinceTime} AND metric_type = #{metricType} ORDER BY window_time DESC")
    List<ServiceMetricDO> selectRecentMetrics(
            @Param("sinceTime") LocalDateTime sinceTime,
            @Param("metricType") String metricType);

    /**
     * 获取服务的关键指标摘要
     */
    @Select("SELECT service_name, AVG(qps) as avg_qps, AVG(avg_latency_ms) as avg_latency, AVG(error_rate) as avg_error_rate FROM insight_service_metrics WHERE window_time >= #{sinceTime} GROUP BY service_name ORDER BY avg_qps DESC")
    List<Map<String, Object>> selectServiceMetricSummary(
            @Param("sinceTime") LocalDateTime sinceTime);

    /**
     * 获取服务的错误率统计
     */
    @Select("SELECT service_name, AVG(error_rate) as avg_error_rate, MAX(error_rate) as max_error_rate FROM insight_service_metrics WHERE window_time >= #{sinceTime} GROUP BY service_name ORDER BY avg_error_rate DESC")
    List<Map<String, Object>> selectServiceErrorRates(
            @Param("sinceTime") LocalDateTime sinceTime);

    /**
     * 获取服务的延迟统计
     */
    @Select("SELECT service_name, AVG(avg_latency_ms) as avg_latency, AVG(p95_latency_ms) as p95_latency, AVG(p99_latency_ms) as p99_latency FROM insight_service_metrics WHERE window_time >= #{sinceTime} GROUP BY service_name ORDER BY avg_latency DESC")
    List<Map<String, Object>> selectServiceLatencyStats(
            @Param("sinceTime") LocalDateTime sinceTime);

    /**
     * 批量更新或插入指标数据
     * 注意：这里使用MyBatis-Plus的BaseMapper的saveBatch方法来实现
     */
    int batchUpsert(List<ServiceMetricDO> metrics);

    /**
     * 删除指定时间范围之前的数据（用于数据清理）
     */
    @Delete("DELETE FROM insight_service_metrics WHERE window_time < #{time}")
    int deleteBeforeTime(@Param("time") LocalDateTime time);
}