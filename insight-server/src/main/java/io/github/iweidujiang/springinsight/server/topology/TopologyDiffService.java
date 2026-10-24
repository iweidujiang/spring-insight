package io.github.iweidujiang.springinsight.server.topology;

import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 拓扑时段对比：拉取两窗依赖边并计算差异。
 * <p>
 * 窗口为相邻半开区间（与变更归因一致）：
 * </p>
 * <ul>
 *   <li>B（对比）：{@code [now - hoursB, now)}</li>
 *   <li>A（基线）：{@code [now - hoursB - hoursA, now - hoursB)}</li>
 * </ul>
 *
 * @author 苏渡苇
 * @since 2026-10-24
 */
@Service
@RequiredArgsConstructor
public class TopologyDiffService {

    private final TraceSpanPersistenceService persistenceService;

    /**
     * @param hoursA 基线窗口小时
     * @param hoursB 对比窗口小时
     * @return added / removed / changed，以及窗口元数据
     */
    public Map<String, Object> diff(int hoursA, int hoursB) {
        return diff(hoursA, hoursB, System.currentTimeMillis());
    }

    /**
     * @param hoursA 基线小时
     * @param hoursB 对比小时
     * @param nowMs  当前时刻（单测可注入）
     * @return 差异体
     */
    public Map<String, Object> diff(int hoursA, int hoursB, long nowMs) {
        int a = Math.max(0, hoursA);
        int b = Math.max(0, hoursB);
        long bMs = b > 0 ? b * 3_600_000L : 0L;
        long aMs = a > 0 ? a * 3_600_000L : 0L;

        long afterFrom = bMs > 0 ? nowMs - bMs : 0L;
        long afterTo = nowMs;
        long beforeFrom;
        long beforeTo;
        if (aMs <= 0L && bMs <= 0L) {
            beforeFrom = 0L;
            beforeTo = 0L;
            afterFrom = 0L;
            afterTo = 0L;
        } else if (aMs <= 0L) {
            beforeFrom = 0L;
            beforeTo = afterFrom > 0 ? afterFrom : 0L;
        } else {
            beforeTo = afterFrom > 0 ? afterFrom : nowMs;
            beforeFrom = beforeTo - aMs;
        }

        List<Map<String, Object>> edgesA = persistenceService.getServiceDependenciesBetween(beforeFrom, beforeTo);
        List<Map<String, Object>> edgesB = persistenceService.getServiceDependenciesBetween(afterFrom, afterTo);
        Map<String, Object> body = new LinkedHashMap<>(TopologyDiff.diff(edgesA, edgesB));
        body.put("hoursA", a);
        body.put("hoursB", b);
        body.put("windowAFromEpochMs", beforeFrom);
        body.put("windowAToEpochMs", beforeTo);
        body.put("windowBFromEpochMs", afterFrom);
        body.put("windowBToEpochMs", afterTo);
        body.put("schemaVersion", 1);
        return body;
    }
}
