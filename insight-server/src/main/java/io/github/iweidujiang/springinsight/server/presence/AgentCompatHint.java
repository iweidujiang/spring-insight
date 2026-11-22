package io.github.iweidujiang.springinsight.server.presence;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组装 Server ↔ Agent 版本配套说明（供健康检查与关于页）。
 *
 * @author 苏渡苇
 * @since 2026-11-22
 */
public final class AgentCompatHint {

    /** 与推荐版本一致 */
    public static final String COMPAT_OK = "ok";
    /** 已上报但与推荐不同 */
    public static final String COMPAT_MISMATCH = "mismatch";
    /** 尚未见到有效版本 */
    public static final String COMPAT_UNKNOWN = "unknown";

    private AgentCompatHint() {
    }

    /**
     * 比较已上报版本与推荐版本。
     *
     * @param recommended 推荐 Agent 版本
     * @param reported    某服务上报的版本
     * @return {@link #COMPAT_OK} / {@link #COMPAT_MISMATCH} / {@link #COMPAT_UNKNOWN}
     */
    public static String classify(String recommended, String reported) {
        String rec = normalize(recommended);
        String rep = normalize(reported);
        if (rep.isEmpty() || "unknown".equals(rep)) {
            return COMPAT_UNKNOWN;
        }
        if (rec.isEmpty() || "unknown".equals(rec)) {
            return COMPAT_UNKNOWN;
        }
        return rec.equals(rep) ? COMPAT_OK : COMPAT_MISMATCH;
    }

    /**
     * 构建兼容性摘要，并给已上报行挂上 {@code compat} 字段。
     *
     * @param serverVersion            Server 版本
     * @param recommendedAgentVersion  推荐 Agent 版本
     * @param reportedRows             {@link ReportedAgentMetaStore#listRows()} 结果（可被原地补充）
     * @return 供 API 嵌入的 compatibility 对象
     */
    public static Map<String, Object> build(String serverVersion,
                                            String recommendedAgentVersion,
                                            List<Map<String, Object>> reportedRows) {
        List<Map<String, Object>> rows = reportedRows != null ? reportedRows : List.of();
        int mismatch = 0;
        int known = 0;
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Object raw = row.get("agent_version");
            String reported = raw == null ? "" : String.valueOf(raw);
            String compat = classify(recommendedAgentVersion, reported);
            row.put("compat", compat);
            if (COMPAT_MISMATCH.equals(compat)) {
                mismatch++;
                known++;
            } else if (COMPAT_OK.equals(compat)) {
                known++;
            }
        }

        String rec = StringUtils.hasText(recommendedAgentVersion) ? recommendedAgentVersion.trim() : "unknown";
        String srv = StringUtils.hasText(serverVersion) ? serverVersion.trim() : "unknown";

        String summary;
        if (rows.isEmpty()) {
            summary = "本监测中心（Server " + srv + "）推荐配套 Agent " + rec
                    + "。当前尚无服务上报版本信息；接入后可在此查看是否与推荐一致。";
        } else if (mismatch == 0) {
            summary = "本监测中心（Server " + srv + "）推荐配套 Agent " + rec
                    + "。已有 " + rows.size() + " 个服务上报版本"
                    + (known > 0 ? "，均与推荐一致。" : "。");
        } else {
            summary = "本监测中心（Server " + srv + "）推荐配套 Agent " + rec
                    + "。已有 " + rows.size() + " 个服务上报版本，其中 "
                    + mismatch + " 个与推荐不同，建议升级后重启业务服务。";
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("serverVersion", srv);
        out.put("recommendedAgentVersion", rec);
        out.put("summary", summary);
        out.put("hint", "常规链路观测可继续使用较旧 Agent；存活心跳与版本识别需使用推荐版本或更新的 Agent。");
        out.put("reportedCount", rows.size());
        out.put("mismatchCount", mismatch);
        out.put("reportedAgents", new ArrayList<>(rows));
        return out;
    }

    private static String normalize(String version) {
        if (version == null) {
            return "";
        }
        return version.trim().toLowerCase();
    }
}
