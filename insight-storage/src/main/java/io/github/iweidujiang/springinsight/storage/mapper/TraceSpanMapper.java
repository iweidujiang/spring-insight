package io.github.iweidujiang.springinsight.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.iweidujiang.springinsight.storage.entity.TraceSpanDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 链路追踪数据 Mapper 接口
 *
 * @author 公众号：苏渡苇，Github：https://github.com/iweidujiang
 * @since 2026/1/9
 */
@Mapper
public interface TraceSpanMapper extends BaseMapper<TraceSpanDO> {

    /**
     * 根据 TraceID 查找完整的调用链
     */
    @Select("SELECT * FROM insight_trace_spans WHERE trace_id = #{traceId} ORDER BY start_time ASC, id ASC")
    List<TraceSpanDO> selectByTraceId(@Param("traceId") String traceId);

    /**
     * 查询某个服务最近 N 条记录
     */
    @Select("SELECT * FROM insight_trace_spans WHERE service_name = #{serviceName} ORDER BY start_time DESC LIMIT #{limit}")
    List<TraceSpanDO> selectRecentByService(
            @Param("serviceName") String serviceName,
            @Param("limit") int limit);

    /**
     * 查询时间范围内的 Span
     */
    @Select("SELECT * FROM insight_trace_spans WHERE start_time BETWEEN #{startTime} AND #{endTime} ORDER BY start_time ")
    List<TraceSpanDO> selectByTimeRange(
            @Param("startTime") Long startTime,
            @Param("endTime") Long endTime);

    /**
     * 查询最近 N 小时的 Span（用于测试和调试）
     */
    @Select("SELECT * FROM insight_trace_spans WHERE start_time >= #{sinceTime} ORDER BY start_time DESC LIMIT #{limit}")
    List<TraceSpanDO> selectRecentSpans(
            @Param("sinceTime") Long sinceTime,
            @Param("limit") Integer limit);

    /**
     * 获取所有唯一的服务名称
     */
    @Select("SELECT DISTINCT service_name FROM insight_trace_spans ORDER BY service_name")
    List<String> selectAllServiceNames();

    /**
     * 获取服务之间的调用关系（用于拓扑图）
     * 返回：调用方服务、被调用方服务、调用次数、平均耗时
     */
    @Select("SELECT " +
            "service_name as source_service, " +
            "remote_service as target_service, " +
            "COUNT(*) as call_count, " +
            "AVG(duration_ms) as avg_duration " +
            "FROM insight_trace_spans " +
            "WHERE remote_service IS NOT NULL AND remote_service != '' " +
            "AND start_time >= #{sinceTime} " +
            "GROUP BY service_name, remote_service " +
            "HAVING call_count > 0")
    List<Map<String, Object>> selectServiceDependencies(@Param("sinceTime") Long sinceTime);

    /**
     * 统计各服务的 Span 数量
     */
    @Select("SELECT service_name, COUNT(*) as span_count FROM insight_trace_spans GROUP BY service_name ORDER BY span_count DESC")
    List<Map<String, Object>> countSpansByService();

    /**
     * 查询错误率高的服务
     */
    @Select("SELECT service_name, " +
            "COUNT(*) as total_calls, " +
            "SUM(CASE WHEN status_code = 'ERROR' THEN 1 ELSE 0 END) as error_calls, " +
            "ROUND(SUM(CASE WHEN status_code = 'ERROR' THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2) as error_rate " +
            "FROM insight_trace_spans " +
            "WHERE start_time >= #{sinceTime} " +
            "GROUP BY service_name " +
            "HAVING error_calls > 0 " +
            "ORDER BY error_rate DESC")
    List<Map<String, Object>> findHighErrorServices(@Param("sinceTime") Long sinceTime);
}
