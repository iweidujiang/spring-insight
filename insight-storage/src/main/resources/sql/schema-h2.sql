-- Spring Insight H2 数据库初始化脚本
-- 适用于嵌入式H2数据库（兼容模式：MySQL）

-- 1. 链路追踪表
CREATE TABLE IF NOT EXISTS insight_trace_spans (
                                                   id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                   trace_id VARCHAR(64) NOT NULL,
                                                   span_id VARCHAR(64) NOT NULL,
                                                   parent_span_id VARCHAR(64),
                                                   service_name VARCHAR(128) NOT NULL,
                                                   service_instance VARCHAR(256),
                                                   host_ip VARCHAR(64),
                                                   host_port INT,
                                                   operation_name VARCHAR(512) NOT NULL,
                                                   span_kind VARCHAR(32) NOT NULL DEFAULT 'INTERNAL',
                                                   component VARCHAR(64),
                                                   endpoint VARCHAR(512),
                                                   start_time BIGINT NOT NULL,
                                                   end_time BIGINT,
                                                   duration_ms BIGINT,
                                                   status_code VARCHAR(32) NOT NULL DEFAULT 'OK',
                                                   error_code VARCHAR(64),
                                                   error_message TEXT,
                                                   remote_service VARCHAR(128),
                                                   remote_endpoint VARCHAR(512),
                                                   tags_json TEXT,
                                                   gmt_create TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3),
                                                   gmt_modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_trace_id ON insight_trace_spans(trace_id);
CREATE INDEX IF NOT EXISTS idx_service_name ON insight_trace_spans(service_name);
CREATE INDEX IF NOT EXISTS idx_start_time ON insight_trace_spans(start_time);
CREATE INDEX IF NOT EXISTS idx_operation_name ON insight_trace_spans(operation_name);
CREATE INDEX IF NOT EXISTS idx_service_dependency ON insight_trace_spans(service_name, remote_service);
CREATE INDEX IF NOT EXISTS idx_time_range ON insight_trace_spans(start_time, end_time);

-- 2. 服务依赖聚合表
CREATE TABLE IF NOT EXISTS insight_service_dependencies (
                                                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                            source_service VARCHAR(128) NOT NULL,
                                                            target_service VARCHAR(128) NOT NULL,
                                                            total_calls BIGINT DEFAULT 0,
                                                            success_calls BIGINT DEFAULT 0,
                                                            error_calls BIGINT DEFAULT 0,
                                                            total_duration_ms BIGINT DEFAULT 0,
                                                            avg_duration_ms DECIMAL(12, 2),
                                                            max_duration_ms BIGINT,
                                                            min_duration_ms BIGINT,
                                                            stat_window VARCHAR(20) NOT NULL,
                                                            window_start_time BIGINT NOT NULL,
                                                            window_end_time BIGINT NOT NULL,
                                                            cal_version INT DEFAULT 1,
                                                            gmt_create TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3),
                                                            gmt_modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3),
                                                            CONSTRAINT uk_source_target_window UNIQUE (source_service, target_service, stat_window, window_start_time)
);

-- 3. 应用指标聚合表
CREATE TABLE IF NOT EXISTS insight_service_metrics (
                                                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                       service_name VARCHAR(128) NOT NULL,
                                                       operation_name VARCHAR(512),
                                                       qps DECIMAL(10, 2),
                                                       avg_latency_ms DECIMAL(12, 2),
                                                       p95_latency_ms DECIMAL(12, 2),
                                                       p99_latency_ms DECIMAL(12, 2),
                                                       error_rate DECIMAL(5, 4),
                                                       metric_type VARCHAR(32) NOT NULL,
                                                       window_time TIMESTAMP NOT NULL,
                                                       gmt_create TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3),
                                                       CONSTRAINT uk_service_operation_time UNIQUE (service_name, operation_name, metric_type, window_time)
);