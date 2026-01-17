package io.github.iweidujiang.springinsight.agent.util;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 Spring Insight 版本工具类
 * │ 用于检测当前运行环境的 JDK 和 Spring Boot 版本
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/17
 * └───────────────────────────────────────────────
 */
@Slf4j
public class VersionUtils {
    
    /**
     * 获取当前 JDK 版本
     * 
     * @return JDK 版本号（如 8、11、17、21）
     */
    public static int getJavaVersion() {
        String version = System.getProperty("java.version");
        log.debug("[版本工具] 原始 JDK 版本字符串: {}", version);
        
        // 处理 JDK 8 及以下版本格式（1.x.y_z）
        if (version.startsWith("1.")) {
            version = version.substring(2, 3);
        } 
        // 处理 JDK 9 及以上版本格式（x.y.z）
        else {
            int dotIndex = version.indexOf(".");
            if (dotIndex != -1) {
                version = version.substring(0, dotIndex);
            }
            
            // 处理版本号中的预发布标识符（如 17-ea、21-rc）
            int dashIndex = version.indexOf("-");
            if (dashIndex != -1) {
                version = version.substring(0, dashIndex);
            }
        }
        
        try {
            int javaVersion = Integer.parseInt(version);
            log.debug("[版本工具] 解析后的 JDK 版本: {}", javaVersion);
            return javaVersion;
        } catch (NumberFormatException e) {
            log.warn("[版本工具] 无法解析 JDK 版本: {}", version, e);
            return 8; // 默认返回最低兼容版本
        }
    }
    
    /**
     * 获取当前 Spring Boot 主版本号
     * 
     * @return Spring Boot 主版本号（如 2、3）
     */
    public static int getSpringBootMajorVersion() {
        try {
            // 尝试通过 Spring Boot 提供的 API 获取版本
            Class<?> versionClass = Class.forName("org.springframework.boot.SpringBootVersion");
            Method getVersionMethod = versionClass.getMethod("getVersion");
            String version = (String) getVersionMethod.invoke(null);
            log.debug("[版本工具] 原始 Spring Boot 版本字符串: {}", version);
            
            // 解析主版本号
            int dotIndex = version.indexOf(".");
            if (dotIndex != -1) {
                String majorVersion = version.substring(0, dotIndex);
                return Integer.parseInt(majorVersion);
            }
            return Integer.parseInt(version);
        } catch (Exception e) {
            log.warn("[版本工具] 无法获取 Spring Boot 版本，假设为 Spring Boot 2.x", e);
            return 2; // 默认返回最低兼容版本
        }
    }
    
    /**
     * 获取当前 Spring Boot 完整版本号
     * 
     * @return Spring Boot 完整版本号
     */
    public static String getSpringBootVersion() {
        try {
            Class<?> versionClass = Class.forName("org.springframework.boot.SpringBootVersion");
            Method getVersionMethod = versionClass.getMethod("getVersion");
            return (String) getVersionMethod.invoke(null);
        } catch (Exception e) {
            log.warn("[版本工具] 无法获取 Spring Boot 版本", e);
            return "unknown";
        }
    }
    
    /**
     * 判断当前 JDK 版本是否支持 var 关键字（Java 10+）
     * 
     * @return 是否支持 var 关键字
     */
    public static boolean isVarKeywordSupported() {
        return getJavaVersion() >= 10;
    }
    
    /**
     * 判断当前 JDK 版本是否支持 record 类型（Java 14+）
     * 
     * @return 是否支持 record 类型
     */
    public static boolean isRecordSupported() {
        return getJavaVersion() >= 14;
    }
    
    /**
     * 判断当前 JDK 版本是否支持 text block（Java 15+）
     * 
     * @return 是否支持 text block
     */
    public static boolean isTextBlockSupported() {
        return getJavaVersion() >= 15;
    }
    
    /**
     * 判断当前 JDK 版本是否支持 pattern matching for instanceof（Java 16+）
     * 
     * @return 是否支持 pattern matching for instanceof
     */
    public static boolean isPatternMatchingSupported() {
        return getJavaVersion() >= 16;
    }
    
    /**
     * 判断当前 JDK 版本是否支持 switch 表达式（Java 14+）
     * 
     * @return 是否支持 switch 表达式
     */
    public static boolean isSwitchExpressionSupported() {
        return getJavaVersion() >= 14;
    }
    
    /**
     * 判断当前 Spring Boot 版本是否使用 jakarta 包（Spring Boot 3.x+）
     * 
     * @return 是否使用 jakarta 包
     */
    public static boolean isJakartaPackageSupported() {
        return getSpringBootMajorVersion() >= 3;
    }
    
    /**
     * 判断当前 Spring Boot 版本是否支持 @ConfigurationPropertiesScan（Spring Boot 2.2+）
     * 
     * @return 是否支持 @ConfigurationPropertiesScan
     */
    public static boolean isConfigurationPropertiesScanSupported() {
        return getSpringBootMajorVersion() >= 2;
    }
    
    /**
     * 判断当前 JDK 版本是否支持 HttpClient（Java 11+）
     * 
     * @return 是否支持 HttpClient
     */
    public static boolean isHttpClientSupported() {
        return getJavaVersion() >= 11;
    }
    
    /**
     * 判断当前 JDK 版本是否支持 Flow API（Java 9+）
     * 
     * @return 是否支持 Flow API
     */
    public static boolean isFlowApiSupported() {
        return getJavaVersion() >= 9;
    }
    
    /**
     * 判断当前 JDK 版本是否支持 Reactive Streams（Java 9+）
     * 
     * @return 是否支持 Reactive Streams
     */
    public static boolean isReactiveStreamsSupported() {
        return getJavaVersion() >= 9;
    }
}