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
 * │ 📦 支付服务
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
public class PaymentService {

    @Autowired
    private OrderService orderService;

    private final Random random = new Random();

    /**
     * 处理支付
     */
    public Map<String, Object> processPayment(String orderId, String paymentMethod, double amount) {
        TraceContext.startSpan("PaymentService.processPayment");
        
        try {
            log.info("处理支付: orderId={}, paymentMethod={}, amount={}", orderId, paymentMethod, amount);
            
            // 1. 获取订单详情
            log.info("1. 获取订单详情: orderId={}", orderId);
            Map<String, Object> order = orderService.getOrderDetail(orderId);
            log.info("获取订单详情成功: {}", order);
            
            // 2. 验证订单状态
            log.info("2. 验证订单状态");
            String orderStatus = (String) order.get("status");
            log.info("订单状态: {}", orderStatus);
            if (!"CREATED".equals(orderStatus)) {
                throw new RuntimeException("订单状态不正确: " + orderStatus);
            }
            
            // 3. 验证金额
            log.info("3. 验证金额");
            double orderAmount = (double) order.get("totalPrice");
            log.info("订单金额: {}, 支付金额: {}", orderAmount, amount);
            if (Math.abs(amount - orderAmount) > 0.01) {
                throw new RuntimeException("支付金额与订单金额不符");
            }
            
            // 4. 模拟支付处理时间
            log.info("4. 模拟支付处理时间");
            Thread.sleep(150 + random.nextInt(200));
            
            // 5. 模拟错误场景
            log.info("5. 模拟错误场景");
            if (random.nextDouble() > 0.85) {
                throw new RuntimeException("支付网关暂时不可用");
            }
            
            // 6. 模拟支付成功
            log.info("6. 模拟支付成功");
            String paymentId = "PAY" + System.currentTimeMillis() + random.nextInt(1000);
            String transactionId = "TXN" + System.currentTimeMillis() + random.nextInt(1000000);
            
            // 7. 更新订单状态
            log.info("7. 更新订单状态: {} -> PAID", orderId);
            orderService.updateOrderStatus(orderId, "PAID");
            
            // 8. 构造支付结果
            log.info("8. 构造支付结果");
            Map<String, Object> payment = new HashMap<>();
            payment.put("paymentId", paymentId);
            payment.put("orderId", orderId);
            payment.put("amount", amount);
            payment.put("paymentMethod", paymentMethod);
            payment.put("transactionId", transactionId);
            payment.put("status", "SUCCESS");
            payment.put("paymentTime", new Date());
            payment.put("currency", "CNY");
            payment.put("cardLast4", "****" + String.format("%04d", random.nextInt(10000)));
            
            log.info("支付处理成功: {}", payment);
            return payment;
            
        } catch (Exception e) {
            log.error("支付处理失败", e);
            throw new RuntimeException("支付处理失败: " + e.getMessage(), e);
        } finally {
            TraceContext.endSpan();
        }
    }

    /**
     * 查询支付状态
     */
    public Map<String, Object> getPaymentStatus(String paymentId) {
        TraceContext.startSpan("PaymentService.getPaymentStatus");
        
        try {
            log.info("查询支付状态: {}", paymentId);
            
            // 模拟处理时间
            Thread.sleep(30 + random.nextInt(50));
            
            // 模拟错误场景
            if ("PAY9999999999".equals(paymentId)) {
                throw new RuntimeException("支付记录不存在");
            }
            
            // 模拟支付状态
            Map<String, Object> status = new HashMap<>();
            status.put("paymentId", paymentId);
            status.put("status", List.of("SUCCESS", "PENDING", "FAILED").get(random.nextInt(3)));
            status.put("queryTime", new Date());
            status.put("amount", ThreadLocalRandom.current().nextDouble(100, 10000));
            status.put("orderId", "ORD" + System.currentTimeMillis());
            
            log.info("支付状态查询成功: {}", status);
            return status;
            
        } catch (Exception e) {
            log.error("查询支付状态失败: {}", paymentId, e);
            throw new RuntimeException("查询支付状态失败: " + e.getMessage(), e);
        } finally {
            TraceContext.endSpan();
        }
    }

    /**
     * 退款
     */
    public Map<String, Object> refund(String paymentId, double amount, String reason) {
        TraceContext.startSpan("PaymentService.refund");
        
        try {
            log.info("处理退款: paymentId={}, amount={}, reason={}", paymentId, amount, reason);
            
            // 1. 查询支付状态
            Map<String, Object> paymentStatus = getPaymentStatus(paymentId);
            if (!"SUCCESS".equals(paymentStatus.get("status"))) {
                throw new RuntimeException("支付未成功，无法退款");
            }
            
            // 2. 模拟退款处理时间
            Thread.sleep(200 + random.nextInt(300));
            
            // 3. 模拟错误场景
            if (random.nextDouble() > 0.7) {
                throw new RuntimeException("退款申请失败，请稍后重试");
            }
            
            // 4. 模拟退款成功
            String refundId = "REF" + System.currentTimeMillis() + random.nextInt(1000);
            String refundTransactionId = "RTXN" + System.currentTimeMillis() + random.nextInt(1000000);
            
            // 5. 构造退款结果
            Map<String, Object> refundResult = new HashMap<>();
            refundResult.put("refundId", refundId);
            refundResult.put("paymentId", paymentId);
            refundResult.put("orderId", paymentStatus.get("orderId"));
            refundResult.put("amount", amount);
            refundResult.put("reason", reason);
            refundResult.put("transactionId", refundTransactionId);
            refundResult.put("status", "SUCCESS");
            refundResult.put("refundTime", new Date());
            refundResult.put("expectedArrivalTime", new Date(System.currentTimeMillis() + 86400000 * 3));
            
            log.info("退款处理成功: {}", refundResult);
            return refundResult;
            
        } catch (Exception e) {
            log.error("退款处理失败", e);
            throw new RuntimeException("退款处理失败: " + e.getMessage(), e);
        } finally {
            TraceContext.endSpan();
        }
    }
}