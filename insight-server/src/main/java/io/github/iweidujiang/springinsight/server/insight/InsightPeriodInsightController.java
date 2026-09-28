package io.github.iweidujiang.springinsight.server.insight;

import io.github.iweidujiang.springinsight.server.ai.InsightAiExplainService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 时段洞察 API（事实与环比；可选 AI 小结）。
 *
 * @since 2026-09-26
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@RestController
@RequestMapping("/api/v1/ui/insights")
@RequiredArgsConstructor
public class InsightPeriodInsightController {

    private final InsightPeriodInsightService periodInsightService;
    private final InsightAiExplainService explainService;

    /**
     * 近 N 小时时段事实；可与上一同等时段对比。
     *
     * @param hours 时段小时数，默认 24；{@code <=0} 表示全部且不做环比
     * @return schemaVersion=1 事实体
     */
    @GetMapping("/period")
    public Map<String, Object> period(
            @RequestParam(value = "hours", defaultValue = "24") int hours) {
        return periodInsightService.build(hours);
    }

    /**
     * 时段 AI 小结（先事实后模型；失败降级仍返回事实摘要）。
     *
     * @param hours 时段小时，默认 24
     * @return schemaVersion=1 解读
     */
    @PostMapping("/period/explain")
    public Map<String, Object> explainPeriod(
            @RequestParam(value = "hours", defaultValue = "24") int hours) {
        return explainService.explainPeriod(hours);
    }
}
