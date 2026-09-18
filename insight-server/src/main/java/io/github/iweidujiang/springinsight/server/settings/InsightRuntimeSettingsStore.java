package io.github.iweidujiang.springinsight.server.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 将运行时设置读写到数据目录 {@code runtime-settings.json}。
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Slf4j
@Component
public class InsightRuntimeSettingsStore {

    static final String FILE_NAME = "runtime-settings.json";

    private final InsightServerStorageProperties storageProperties;
    private final ObjectMapper objectMapper;

    /**
     * @param storageProperties 用于推断数据目录
     * @param objectMapper      JSON
     */
    public InsightRuntimeSettingsStore(InsightServerStorageProperties storageProperties,
                                       ObjectMapper objectMapper) {
        this.storageProperties = storageProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * @return 设置文件路径
     */
    public Path settingsPath() {
        return resolveDataDir().resolve(FILE_NAME);
    }

    /**
     * @return 已存在的设置；文件不存在或损坏时返回 {@code null}
     */
    public InsightRuntimeSettings loadOrNull() {
        Path path = settingsPath();
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            return objectMapper.readValue(path.toFile(), InsightRuntimeSettings.class);
        } catch (Exception e) {
            log.warn("[设置] 读取 {} 失败: {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * 原子写入设置文件。
     *
     * @param settings 完整设置
     */
    public void save(InsightRuntimeSettings settings) {
        Path path = settingsPath();
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(FILE_NAME + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), settings);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.info("[设置] 已保存到 {}", path.toAbsolutePath());
        } catch (Exception e) {
            throw new IllegalStateException("保存运行时设置失败: " + e.getMessage(), e);
        }
    }

    /**
     * 按存储模式推断数据目录（与 spans / sqlite 同级）。
     *
     * @return 目录 Path
     */
    Path resolveDataDir() {
        if (storageProperties.isSqliteMode()
                && StringUtils.hasText(storageProperties.getSqlite().getPath())) {
            Path db = Path.of(storageProperties.getSqlite().getPath()).toAbsolutePath().normalize();
            Path parent = db.getParent();
            return parent != null ? parent : Path.of(".").toAbsolutePath().normalize();
        }
        if (storageProperties.isFileMode()
                && StringUtils.hasText(storageProperties.getFilePath())) {
            Path file = Path.of(storageProperties.getFilePath()).toAbsolutePath().normalize();
            Path parent = file.getParent();
            return parent != null ? parent : Path.of(".").toAbsolutePath().normalize();
        }
        return Path.of("./data").toAbsolutePath().normalize();
    }
}
