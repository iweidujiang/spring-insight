package io.github.iweidujiang.springinsight.demo.controller;

import io.github.iweidujiang.springinsight.agent.context.TraceContext;
import io.github.iweidujiang.springinsight.demo.service.UserService;
import io.github.iweidujiang.springinsight.demo.service.ProductService;
import io.github.iweidujiang.springinsight.demo.service.OrderService;
import io.github.iweidujiang.springinsight.demo.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 演示控制器
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/15
 * └───────────────────────────────────────────────
 */
@Slf4j
@RestController
@RequestMapping("/demo")
public class DemoController {

    @Autowired
    private UserService userService;

    @Autowired
    private ProductService productService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentService paymentService;

    /**
     * 测试完整的订单流程
     */
    @PostMapping("/order-flow")
    public Map<String, Object> testOrderFlow(@RequestBody Map<String, Object> request) {
        TraceContext.startSpan("DemoController.testOrderFlow");
        
        try {
            log.info("测试完整订单流程: {}", request);
            
            String userId = (String) request.get("userId");
            List<Map<String, Object>> items = (List<Map<String, Object>>) request.get("items");
            String paymentMethod = (String) request.get("paymentMethod");
            
            // 1. 创建订单
            log.info("1. 创建订单");
            TraceContext.setRemoteService("order-service");
            Map<String, Object> order = orderService.createOrder(userId, items);
            String orderId = (String) order.get("orderId");
            double totalPrice = (double) order.get("totalPrice");
            String orderStatus = (String) order.get("status");
            log.info("订单创建成功: orderId={}, status={}, totalPrice={}", orderId, orderStatus, totalPrice);
            
            // 2. 处理支付
            log.info("2. 处理支付: orderId={}, paymentMethod={}, amount={}", orderId, paymentMethod, totalPrice);
            TraceContext.setRemoteService("payment-service");
            Map<String, Object> payment = paymentService.processPayment(orderId, paymentMethod, totalPrice);
            
            // 3. 构造结果
            log.info("3. 构造结果");
            Map<String, Object> result = new HashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "完整订单流程测试成功");
            result.put("order", order);
            result.put("payment", payment);
            result.put("timestamp", System.currentTimeMillis());
            
            log.info("完整订单流程测试成功");
            return result;
            
        } catch (Exception e) {
            log.error("完整订单流程测试失败", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "FAILED");
            errorResult.put("message", "测试失败: " + e.getMessage());
            errorResult.put("timestamp", System.currentTimeMillis());
            return errorResult;
        } finally {
            TraceContext.endSpan();
        }
    }

    /**
     * 测试用户服务
     */
    @GetMapping("/user/{userId}")
    public Map<String, Object> testUserService(@PathVariable("userId") String userId) {
        TraceContext.startSpan("DemoController.testUserService");
        
        try {
            log.info("测试用户服务: {}", userId);
            
            // 1. 检查用户是否存在
            TraceContext.setRemoteService("user-service");
            boolean exists = userService.checkUserExists(userId);
            
            // 2. 获取用户信息
            TraceContext.setRemoteService("user-service");
            Map<String, Object> userInfo = userService.getUserInfo(userId);
            
            // 3. 构造结果
            Map<String, Object> result = new HashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "用户服务测试成功");
            result.put("userExists", exists);
            result.put("userInfo", userInfo);
            result.put("timestamp", System.currentTimeMillis());
            
            log.info("用户服务测试成功");
            return result;
            
        } catch (Exception e) {
            log.error("用户服务测试失败: {}", userId, e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "FAILED");
            errorResult.put("message", "测试失败: " + e.getMessage());
            errorResult.put("timestamp", System.currentTimeMillis());
            return errorResult;
        } finally {
            TraceContext.endSpan();
        }
    }

    /**
     * 测试产品服务
     */
    @GetMapping("/product/{productId}")
    public Map<String, Object> testProductService(@PathVariable("productId") String productId) {
        TraceContext.startSpan("DemoController.testProductService");
        
        try {
            log.info("测试产品服务: {}", productId);
            
            // 1. 获取产品详情
            TraceContext.setRemoteService("product-service");
            Map<String, Object> productDetail = productService.getProductDetail(productId);
            
            // 2. 检查产品库存
            TraceContext.setRemoteService("product-service");
            int stock = productService.checkProductStock(productId);
            
            // 3. 构造结果
            Map<String, Object> result = new HashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "产品服务测试成功");
            result.put("productDetail", productDetail);
            result.put("stock", stock);
            result.put("timestamp", System.currentTimeMillis());
            
            log.info("产品服务测试成功");
            return result;
            
        } catch (Exception e) {
            log.error("产品服务测试失败: {}", productId, e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "FAILED");
            errorResult.put("message", "测试失败: " + e.getMessage());
            errorResult.put("timestamp", System.currentTimeMillis());
            return errorResult;
        } finally {
            TraceContext.endSpan();
        }
    }

    /**
     * 测试订单服务
     */
    @PostMapping("/order")
    public Map<String, Object> testOrderService(@RequestBody Map<String, Object> request) {
        TraceContext.startSpan("DemoController.testOrderService");
        
        try {
            log.info("测试订单服务: {}", request);
            
            String userId = (String) request.get("userId");
            List<Map<String, Object>> items = (List<Map<String, Object>>) request.get("items");
            
            // 创建订单
            TraceContext.setRemoteService("order-service");
            Map<String, Object> order = orderService.createOrder(userId, items);
            
            // 构造结果
            Map<String, Object> result = new HashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "订单服务测试成功");
            result.put("order", order);
            result.put("timestamp", System.currentTimeMillis());
            
            log.info("订单服务测试成功");
            return result;
            
        } catch (Exception e) {
            log.error("订单服务测试失败", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "FAILED");
            errorResult.put("message", "测试失败: " + e.getMessage());
            errorResult.put("timestamp", System.currentTimeMillis());
            return errorResult;
        } finally {
            TraceContext.endSpan();
        }
    }

    /**
     * 测试支付服务
     */
    @PostMapping("/payment")
    public Map<String, Object> testPaymentService(@RequestBody Map<String, Object> request) {
        TraceContext.startSpan("DemoController.testPaymentService");
        
        try {
            log.info("测试支付服务: {}", request);
            
            String orderId = (String) request.get("orderId");
            String paymentMethod = (String) request.get("paymentMethod");
            
            // 处理 amount 参数，支持字符串和数字类型
            double amount;
            Object amountObj = request.get("amount");
            if (amountObj instanceof String) {
                amount = Double.parseDouble((String) amountObj);
            } else if (amountObj instanceof Double) {
                amount = (Double) amountObj;
            } else if (amountObj instanceof Integer) {
                amount = ((Integer) amountObj).doubleValue();
            } else {
                throw new RuntimeException("无效的金额类型: " + amountObj.getClass());
            }
            
            // 处理支付
            TraceContext.setRemoteService("payment-service");
            Map<String, Object> payment = paymentService.processPayment(orderId, paymentMethod, amount);
            
            // 构造结果
            Map<String, Object> result = new HashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "支付服务测试成功");
            result.put("payment", payment);
            result.put("timestamp", System.currentTimeMillis());
            
            log.info("支付服务测试成功");
            return result;
            
        } catch (Exception e) {
            log.error("支付服务测试失败", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "FAILED");
            errorResult.put("message", "测试失败: " + e.getMessage());
            errorResult.put("timestamp", System.currentTimeMillis());
            return errorResult;
        } finally {
            TraceContext.endSpan();
        }
    }

    /**
     * 测试服务依赖拓扑
     */
    @GetMapping("/topology-test")
    public Map<String, Object> testServiceTopology() {
        TraceContext.startSpan("DemoController.testServiceTopology");
        
        try {
            log.info("测试服务依赖拓扑");
            
            // 模拟一个复杂的服务调用链
            Map<String, Object> result = new HashMap<>();
            
            // 1. 用户服务调用
            TraceContext.setRemoteService("user-service");
            Map<String, Object> userInfo = userService.getUserInfo("1001");
            
            // 2. 产品服务调用
            TraceContext.setRemoteService("product-service");
            List<Map<String, Object>> productList = productService.getProductList(1, 5);
            TraceContext.setRemoteService("product-service");
            Map<String, Object> productDetail = productService.getProductDetail("P1");
            
            // 3. 订单服务调用
            List<Map<String, Object>> orderItems = new ArrayList<>();
            Map<String, Object> item = new HashMap<>();
            item.put("productId", "P1");
            item.put("quantity", 2);
            orderItems.add(item);
            
            TraceContext.setRemoteService("order-service");
            Map<String, Object> order = orderService.createOrder("1001", orderItems);
            
            // 4. 支付服务调用
            String orderId = (String) order.get("orderId");
            double totalPrice = (double) order.get("totalPrice");
            TraceContext.setRemoteService("payment-service");
            Map<String, Object> payment = paymentService.processPayment(orderId, "ALIPAY", totalPrice);
            
            // 构造结果
            result.put("status", "SUCCESS");
            result.put("message", "服务依赖拓扑测试成功");
            result.put("serviceCalls", Map.of(
                    "userService", userInfo,
                    "productService", Map.of(
                            "list", productList.size(),
                            "detail", productDetail
                    ),
                    "orderService", order,
                    "paymentService", payment
            ));
            result.put("timestamp", System.currentTimeMillis());
            
            log.info("服务依赖拓扑测试成功");
            return result;
            
        } catch (Exception e) {
            log.error("服务依赖拓扑测试失败", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "FAILED");
            errorResult.put("message", "测试失败: " + e.getMessage());
            errorResult.put("timestamp", System.currentTimeMillis());
            return errorResult;
        } finally {
            TraceContext.endSpan();
        }
    }

    /**
     * 测试错误场景
     */
    @GetMapping("/error-test")
    public Map<String, Object> testErrorScenarios(@RequestParam String type) {
        TraceContext.startSpan("DemoController.testErrorScenarios");
        
        try {
            log.info("测试错误场景: {}", type);
            
            Map<String, Object> result = new HashMap<>();
            
            switch (type) {
                case "user":
                    // 测试用户服务错误
                    userService.getUserInfo("0");
                    break;
                case "product":
                    // 测试产品服务错误
                    productService.getProductDetail("P999");
                    break;
                case "order":
                    // 测试订单服务错误
                    List<Map<String, Object>> items = new ArrayList<>();
                    Map<String, Object> item = new HashMap<>();
                    item.put("productId", "P1");
                    item.put("quantity", 99999); // 大量商品，触发金额过大错误
                    items.add(item);
                    orderService.createOrder("1001", items);
                    break;
                case "payment":
                    // 测试支付服务错误
                    paymentService.processPayment("ORD9999999999", "ALIPAY", 100);
                    break;
                default:
                    throw new IllegalArgumentException("未知的错误类型: " + type);
            }
            
            result.put("status", "SUCCESS");
            result.put("message", "错误场景测试成功");
            result.put("timestamp", System.currentTimeMillis());
            return result;
            
        } catch (Exception e) {
            log.error("错误场景测试失败", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "EXPECTED_ERROR");
            errorResult.put("message", "预期的错误: " + e.getMessage());
            errorResult.put("errorType", e.getClass().getSimpleName());
            errorResult.put("timestamp", System.currentTimeMillis());
            return errorResult;
        } finally {
            TraceContext.endSpan();
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Map<String, Object> healthCheck() {
        TraceContext.startSpan("DemoController.healthCheck");
        
        try {
            log.info("健康检查");
            
            Map<String, Object> result = new HashMap<>();
            result.put("status", "UP");
            result.put("service", "demo-service");
            result.put("timestamp", System.currentTimeMillis());
            result.put("version", "1.0.0");
            result.put("services", Map.of(
                    "userService", "AVAILABLE",
                    "productService", "AVAILABLE",
                    "orderService", "AVAILABLE",
                    "paymentService", "AVAILABLE"
            ));
            
            log.info("健康检查成功");
            return result;
            
        } catch (Exception e) {
            log.error("健康检查失败", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "DOWN");
            errorResult.put("message", "检查失败: " + e.getMessage());
            errorResult.put("timestamp", System.currentTimeMillis());
            return errorResult;
        } finally {
            TraceContext.endSpan();
        }
    }
}