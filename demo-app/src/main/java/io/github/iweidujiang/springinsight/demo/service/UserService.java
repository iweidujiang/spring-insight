package io.github.iweidujiang.springinsight.demo.service;

import io.github.iweidujiang.springinsight.agent.context.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 用户服务
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/15
 * └───────────────────────────────────────────────
 */
@Slf4j
@Service
public class UserService {

    private final Random random = new Random();

    /**
     * 获取用户信息
     */
    public Map<String, Object> getUserInfo(String userId) {
        TraceContext.startSpan("UserService.getUserInfo");
        
        try {
            log.info("获取用户信息: {}", userId);
            
            // 模拟处理时间
            Thread.sleep(30 + random.nextInt(50));
            
            // 模拟错误场景
            if ("0".equals(userId)) {
                throw new RuntimeException("用户ID不能为0");
            }
            
            // 模拟用户数据
            Map<String, Object> user = new HashMap<>();
            user.put("id", userId);
            user.put("name", "用户" + userId);
            user.put("email", "user" + userId + "@example.com");
            user.put("age", ThreadLocalRandom.current().nextInt(18, 60));
            user.put("phone", "138" + String.format("%08d", userId.hashCode() % 100000000));
            user.put("address", "北京市朝阳区" + random.nextInt(1000) + "号");
            
            log.info("用户信息获取成功: {}", user);
            return user;
            
        } catch (Exception e) {
            log.error("获取用户信息失败: {}", userId, e);
            throw new RuntimeException("获取用户信息失败: " + e.getMessage(), e);
        } finally {
            TraceContext.endSpan();
        }
    }

    /**
     * 检查用户是否存在
     */
    public boolean checkUserExists(String userId) {
        TraceContext.startSpan("UserService.checkUserExists");
        
        try {
            log.info("检查用户是否存在: {}", userId);
            
            // 模拟处理时间
            Thread.sleep(10 + random.nextInt(20));
            
            // 模拟错误场景
            if ("999".equals(userId)) {
                throw new RuntimeException("数据库连接失败");
            }
            
            // 模拟用户存在性检查
            boolean exists = !"0".equals(userId) && !"100".equals(userId);
            
            log.info("用户存在性检查结果: {} - {}", userId, exists);
            return exists;
            
        } catch (Exception e) {
            log.error("检查用户存在性失败: {}", userId, e);
            throw new RuntimeException("检查用户存在性失败: " + e.getMessage(), e);
        } finally {
            TraceContext.endSpan();
        }
    }

    /**
     * 更新用户信息
     */
    public Map<String, Object> updateUserInfo(String userId, Map<String, Object> userInfo) {
        TraceContext.startSpan("UserService.updateUserInfo");
        
        try {
            log.info("更新用户信息: {}", userId);
            
            // 模拟处理时间
            Thread.sleep(40 + random.nextInt(60));
            
            // 模拟错误场景
            if ("500".equals(userId)) {
                throw new RuntimeException("权限不足，无法更新用户信息");
            }
            
            // 模拟更新操作
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("userId", userId);
            result.put("updatedFields", userInfo.keySet());
            result.put("timestamp", System.currentTimeMillis());
            
            log.info("用户信息更新成功: {}", result);
            return result;
            
        } catch (Exception e) {
            log.error("更新用户信息失败: {}", userId, e);
            throw new RuntimeException("更新用户信息失败: " + e.getMessage(), e);
        } finally {
            TraceContext.endSpan();
        }
    }
}