package io.github.iweidujiang.springinsight.demo.service;

import io.github.iweidujiang.springinsight.agent.context.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 产品服务
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
public class ProductService {

    private final Random random = new Random();
    private final List<String> products = List.of(
            "笔记本电脑", "智能手机", "平板电脑", "无线耳机",
            "智能手表", "游戏机", "数码相机", "蓝牙音箱",
            "显示器", "键盘", "鼠标", "打印机"
    );

    /**
     * 获取产品列表
     */
    public List<Map<String, Object>> getProductList(int page, int size) {
        TraceContext.startSpan("ProductService.getProductList");
        
        try {
            log.info("获取产品列表: page={}, size={}", page, size);
            
            // 模拟处理时间
            Thread.sleep(50 + random.nextInt(80));
            
            // 模拟错误场景
            if (page < 0 || size < 1) {
                throw new IllegalArgumentException("参数错误: page和size必须为正数");
            }
            
            // 模拟产品列表
            List<Map<String, Object>> productList = new ArrayList<>();
            int start = (page - 1) * size;
            int end = Math.min(start + size, products.size());
            
            for (int i = start; i < end; i++) {
                Map<String, Object> product = new HashMap<>();
                product.put("id", "P" + (i + 1));
                product.put("name", products.get(i));
                product.put("price", ThreadLocalRandom.current().nextDouble(100, 5000));
                product.put("stock", ThreadLocalRandom.current().nextInt(10, 1000));
                product.put("category", "电子产品");
                productList.add(product);
            }
            
            log.info("产品列表获取成功: 共{}个产品", productList.size());
            return productList;
            
        } catch (Exception e) {
            log.error("获取产品列表失败", e);
            throw new RuntimeException("获取产品列表失败: " + e.getMessage(), e);
        } finally {
            TraceContext.endSpan();
        }
    }

    /**
     * 获取产品详情
     */
    public Map<String, Object> getProductDetail(String productId) {
        TraceContext.startSpan("ProductService.getProductDetail");
        
        try {
            log.info("获取产品详情: {}", productId);
            
            // 模拟处理时间
            Thread.sleep(30 + random.nextInt(40));
            
            // 模拟错误场景
            if ("P999".equals(productId)) {
                throw new RuntimeException("产品不存在: " + productId);
            }
            
            // 模拟产品详情
            int index = Math.abs(productId.hashCode()) % products.size();
            String productName = products.get(index);
            
            Map<String, Object> product = new HashMap<>();
            product.put("id", productId);
            product.put("name", productName);
            product.put("price", ThreadLocalRandom.current().nextDouble(100, 5000));
            product.put("stock", ThreadLocalRandom.current().nextInt(10, 1000));
            product.put("description", "这是" + productName + "的详细描述，包含产品的功能、规格、使用方法等信息。");
            product.put("category", "电子产品");
            product.put("brand", "品牌" + (index % 5 + 1));
            product.put("rating", ThreadLocalRandom.current().nextDouble(3.5, 5.0));
            product.put("reviews", ThreadLocalRandom.current().nextInt(10, 1000));
            
            log.info("产品详情获取成功: {}", product);
            return product;
            
        } catch (Exception e) {
            log.error("获取产品详情失败: {}", productId, e);
            throw new RuntimeException("获取产品详情失败: " + e.getMessage(), e);
        } finally {
            TraceContext.endSpan();
        }
    }

    /**
     * 检查产品库存
     */
    public int checkProductStock(String productId) {
        TraceContext.startSpan("ProductService.checkProductStock");
        
        try {
            log.info("检查产品库存: {}", productId);
            
            // 模拟处理时间
            Thread.sleep(20 + random.nextInt(30));
            
            // 模拟错误场景
            if ("P888".equals(productId)) {
                throw new RuntimeException("库存系统故障");
            }
            
            // 模拟库存检查
            int stock = ThreadLocalRandom.current().nextInt(0, 1000);
            
            log.info("产品库存检查结果: {} - {}", productId, stock);
            return stock;
            
        } catch (Exception e) {
            log.error("检查产品库存失败: {}", productId, e);
            throw new RuntimeException("检查产品库存失败: " + e.getMessage(), e);
        } finally {
            TraceContext.endSpan();
        }
    }

    /**
     * 搜索产品
     */
    public List<Map<String, Object>> searchProducts(String keyword, int limit) {
        TraceContext.startSpan("ProductService.searchProducts");
        
        try {
            log.info("搜索产品: keyword={}, limit={}", keyword, limit);
            
            // 模拟处理时间
            Thread.sleep(60 + random.nextInt(100));
            
            // 模拟错误场景
            if (keyword == null || keyword.trim().isEmpty()) {
                throw new IllegalArgumentException("搜索关键词不能为空");
            }
            
            // 模拟搜索结果
            List<Map<String, Object>> results = new ArrayList<>();
            int count = 0;
            
            for (int i = 0; i < products.size() && count < limit; i++) {
                String productName = products.get(i);
                if (productName.contains(keyword) || keyword.length() < 2) {
                    Map<String, Object> product = new HashMap<>();
                    product.put("id", "P" + (i + 1));
                    product.put("name", productName);
                    product.put("price", ThreadLocalRandom.current().nextDouble(100, 5000));
                    product.put("score", ThreadLocalRandom.current().nextDouble(0.5, 1.0));
                    results.add(product);
                    count++;
                }
            }
            
            log.info("产品搜索成功: 找到{}个结果", results.size());
            return results;
            
        } catch (Exception e) {
            log.error("搜索产品失败", e);
            throw new RuntimeException("搜索产品失败: " + e.getMessage(), e);
        } finally {
            TraceContext.endSpan();
        }
    }
}