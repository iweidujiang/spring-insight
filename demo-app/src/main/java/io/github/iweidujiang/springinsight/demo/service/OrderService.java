package io.github.iweidujiang.springinsight.demo.service;

import io.github.iweidujiang.springinsight.agent.context.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 订单服务
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
public class OrderService {

    @Autowired
    private UserService userService;

    @Autowired
    private ProductService productService;

    private final Random random = new Random();
    private final Map<String, Map<String, Object>> orderStore = new HashMap<>();

    /**
     * 创建订单
     */
    public Map<String, Object> createOrder(String userId, List<Map<String, Object>> items) {
        TraceContext.startSpan("OrderService.createOrder");
        
        try {
            log.info("创建订单: userId={}, items={}", userId, items);
            
            // 1. 检查用户是否存在
            TraceContext.setRemoteService("user-service");
            boolean userExists = userService.checkUserExists(userId);
            if (!userExists) {
                throw new RuntimeException("用户不存在: " + userId);
            }
            
            // 2. 获取用户信息
            TraceContext.setRemoteService("user-service");
            Map<String, Object> userInfo = userService.getUserInfo(userId);
            
            // 3. 验证商品库存并计算总价
            double totalPrice = 0;
            List<Map<String, Object>> orderItems = new ArrayList<>();
            
            for (Map<String, Object> item : items) {
                String productId = (String) item.get("productId");
                int quantity;
                Object quantityObj = item.get("quantity");
                if (quantityObj instanceof String) {
                    quantity = Integer.parseInt((String) quantityObj);
                } else if (quantityObj instanceof Integer) {
                    quantity = (Integer) quantityObj;
                } else {
                    throw new RuntimeException("无效的商品数量类型: " + quantityObj.getClass());
                }
                
                // 检查库存
                TraceContext.setRemoteService("product-service");
                int stock = productService.checkProductStock(productId);
                if (stock < quantity) {
                    throw new RuntimeException("商品库存不足: " + productId);
                }
                
                // 获取商品详情
                TraceContext.setRemoteService("product-service");
                Map<String, Object> product = productService.getProductDetail(productId);
                double price = (double) product.get("price");
                
                // 计算商品总价
                double itemPrice = price * quantity;
                totalPrice += itemPrice;
                
                // 添加到订单商品列表
                Map<String, Object> orderItem = new HashMap<>();
                orderItem.put("productId", productId);
                orderItem.put("productName", product.get("name"));
                orderItem.put("quantity", quantity);
                orderItem.put("price", price);
                orderItem.put("totalPrice", itemPrice);
                orderItems.add(orderItem);
            }
            
            // 4. 生成订单号
            String orderId = "ORD" + System.currentTimeMillis() + random.nextInt(1000);
            
            // 5. 模拟处理时间
            Thread.sleep(100 + random.nextInt(150));
            
            // 6. 模拟错误场景
            if (totalPrice > 50000) {
                throw new RuntimeException("订单金额过大，需要人工审核");
            }
            
            // 7. 创建订单
            Map<String, Object> order = new HashMap<>();
            order.put("orderId", orderId);
            order.put("userId", userId);
            order.put("userName", userInfo.get("name"));
            order.put("items", orderItems);
            order.put("totalPrice", totalPrice);
            order.put("status", "CREATED");
            order.put("createTime", new Date());
            order.put("paymentStatus", "UNPAID");
            order.put("shippingAddress", userInfo.get("address"));
            order.put("phone", userInfo.get("phone"));
            
            orderStore.put(orderId, order);
            log.info("订单创建成功: {}", order);
            return order;
            
        } catch (Exception e) {
            log.error("创建订单失败", e);
            throw new RuntimeException("创建订单失败: " + e.getMessage(), e);
        } finally {
            TraceContext.endSpan();
        }
    }

    /**
     * 获取订单详情
     */
    public Map<String, Object> getOrderDetail(String orderId) {
        TraceContext.startSpan("OrderService.getOrderDetail");
        
        try {
            log.info("获取订单详情: {}", orderId);
            
            // 模拟处理时间
            Thread.sleep(50 + random.nextInt(80));
            
            // 模拟错误场景
            if ("ORD9999999999".equals(orderId)) {
                throw new RuntimeException("订单不存在: " + orderId);
            }
            
            // 从订单存储中获取订单详情
            if (orderStore.containsKey(orderId)) {
                Map<String, Object> order = orderStore.get(orderId);
                log.info("从订单存储中获取订单详情: {}", order);
                return order;
            }
            
            // 模拟订单详情
            log.info("从订单存储中未找到订单，返回模拟订单详情");
            Map<String, Object> order = new HashMap<>();
            order.put("orderId", orderId);
            order.put("userId", "1001");
            order.put("userName", "用户1001");
            order.put("totalPrice", ThreadLocalRandom.current().nextDouble(100, 10000));
            order.put("status", "CREATED");
            order.put("createTime", new Date(System.currentTimeMillis() - ThreadLocalRandom.current().nextLong(86400000L * 30L)));
            order.put("paymentStatus", "UNPAID");
            order.put("shippingAddress", "北京市朝阳区" + random.nextInt(1000) + "号");
            order.put("phone", "138" + String.format("%08d", random.nextInt(100000000)));
            
            // 模拟订单商品
            List<Map<String, Object>> items = new ArrayList<>();
            int itemCount = random.nextInt(3) + 1;
            
            for (int i = 0; i < itemCount; i++) {
                Map<String, Object> item = new HashMap<>();
                item.put("productId", "P" + (random.nextInt(10) + 1));
                item.put("productName", List.of("笔记本电脑", "智能手机", "平板电脑", "无线耳机").get(random.nextInt(4)));
                item.put("quantity", random.nextInt(3) + 1);
                item.put("price", ThreadLocalRandom.current().nextDouble(100, 5000));
                item.put("totalPrice", ThreadLocalRandom.current().nextDouble(100, 15000));
                items.add(item);
            }
            
            order.put("items", items);
            
            log.info("订单详情获取成功: {}", order);
            return order;
            
        } catch (Exception e) {
            log.error("获取订单详情失败: {}", orderId, e);
            throw new RuntimeException("获取订单详情失败: " + e.getMessage(), e);
        } finally {
            TraceContext.endSpan();
        }
    }

    /**
     * 获取用户订单列表
     */
    public List<Map<String, Object>> getUserOrders(String userId, int page, int size) {
        TraceContext.startSpan("OrderService.getUserOrders");
        
        try {
            log.info("获取用户订单列表: userId={}, page={}, size={}", userId, page, size);
            
            // 1. 检查用户是否存在
            boolean userExists = userService.checkUserExists(userId);
            if (!userExists) {
                throw new RuntimeException("用户不存在: " + userId);
            }
            
            // 2. 模拟处理时间
            Thread.sleep(80 + random.nextInt(120));
            
            // 3. 模拟订单列表
            List<Map<String, Object>> orders = new ArrayList<>();
            int orderCount = Math.min(size, 10);
            
            for (int i = 0; i < orderCount; i++) {
                Map<String, Object> order = new HashMap<>();
                order.put("orderId", "ORD" + System.currentTimeMillis() + i);
                order.put("userId", userId);
                order.put("totalPrice", ThreadLocalRandom.current().nextDouble(100, 10000));
                order.put("status", List.of("CREATED", "PAID", "SHIPPING", "DELIVERED", "COMPLETED").get(random.nextInt(5)));
                order.put("createTime", new Date(System.currentTimeMillis() - ThreadLocalRandom.current().nextLong(86400000L * 30L)));
                order.put("itemCount", random.nextInt(5) + 1);
                orders.add(order);
            }
            
            log.info("用户订单列表获取成功: 共{}个订单", orders.size());
            return orders;
            
        } catch (Exception e) {
            log.error("获取用户订单列表失败: {}", userId, e);
            throw new RuntimeException("获取用户订单列表失败: " + e.getMessage(), e);
        } finally {
            TraceContext.endSpan();
        }
    }

    /**
     * 更新订单状态
     */
    public Map<String, Object> updateOrderStatus(String orderId, String status) {
        TraceContext.startSpan("OrderService.updateOrderStatus");
        
        try {
            log.info("更新订单状态: {} -> {}", orderId, status);
            
            // 模拟处理时间
            Thread.sleep(30 + random.nextInt(50));
            
            // 模拟错误场景
            if ("CANCELLED".equals(status) && random.nextDouble() > 0.7) {
                throw new RuntimeException("订单已超过取消时限");
            }
            
            // 从订单存储中获取订单并更新状态
            if (orderStore.containsKey(orderId)) {
                Map<String, Object> order = orderStore.get(orderId);
                String oldStatus = (String) order.get("status");
                order.put("status", status);
                if ("PAID".equals(status)) {
                    order.put("paymentStatus", "PAID");
                }
                log.info("更新订单状态成功: {} -> {} (旧状态: {})", orderId, status, oldStatus);
                
                // 构造更新结果
                Map<String, Object> result = new HashMap<>();
                result.put("orderId", orderId);
                result.put("oldStatus", oldStatus);
                result.put("newStatus", status);
                result.put("updateTime", new Date());
                result.put("success", true);
                
                return result;
            }
            
            // 模拟更新结果
            Map<String, Object> result = new HashMap<>();
            result.put("orderId", orderId);
            result.put("oldStatus", "CREATED");
            result.put("newStatus", status);
            result.put("updateTime", new Date());
            result.put("success", true);
            
            log.info("订单状态更新成功: {}", result);
            return result;
            
        } catch (Exception e) {
            log.error("更新订单状态失败: {}", orderId, e);
            throw new RuntimeException("更新订单状态失败: " + e.getMessage(), e);
        } finally {
            TraceContext.endSpan();
        }
    }
}