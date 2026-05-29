package com.example.customerservice.order;

import com.example.customerservice.exception.InvalidParameterException;
import com.example.customerservice.exception.OrderNotFoundException;
import com.example.customerservice.model.Order;
import com.example.customerservice.model.Order.OrderStatus;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class OrderService {

    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final AtomicInteger failureCounter = new AtomicInteger(0);

    @Value("${app.order.simulate-transient-failure:false}")
    private boolean simulateTransientFailure;

    @Value("${app.order.transient-failure-rate:3}")
    private int transientFailureRate;

    @PostConstruct
    void initSampleData() {
        save(Order.builder()
                .orderId("ORD-10001")
                .customerName("张三")
                .phone("13800138001")
                .productName("无线蓝牙耳机")
                .quantity(1)
                .amount(new BigDecimal("299.00"))
                .status(OrderStatus.SHIPPED)
                .createdAt(LocalDateTime.of(2026, 5, 20, 10, 30))
                .shippingAddress("北京市朝阳区建国路88号")
                .build());

        save(Order.builder()
                .orderId("ORD-10002")
                .customerName("李四")
                .phone("13900139002")
                .productName("智能手表")
                .quantity(1)
                .amount(new BigDecimal("1299.00"))
                .status(OrderStatus.DELIVERED)
                .createdAt(LocalDateTime.of(2026, 5, 15, 14, 20))
                .shippingAddress("上海市浦东新区陆家嘴环路1000号")
                .build());

        save(Order.builder()
                .orderId("ORD-10003")
                .customerName("王五")
                .phone("13700137003")
                .productName("机械键盘")
                .quantity(2)
                .amount(new BigDecimal("598.00"))
                .status(OrderStatus.PAID)
                .createdAt(LocalDateTime.of(2026, 5, 28, 9, 0))
                .shippingAddress("广州市天河区体育西路123号")
                .build());

        log.info("Loaded {} sample orders", orders.size());
    }

    public Order queryByOrderId(String orderId) {
        validateOrderId(orderId);
        maybeSimulateTransientFailure();
        Order order = orders.get(normalizeOrderId(orderId));
        if (order == null) {
            throw new OrderNotFoundException("订单不存在: " + orderId);
        }
        return order;
    }

    public List<Order> queryByPhone(String phone) {
        validatePhone(phone);
        maybeSimulateTransientFailure();
        String normalizedPhone = normalizePhone(phone);
        List<Order> result = orders.values().stream()
                .filter(o -> o.getPhone().equals(normalizedPhone))
                .toList();
        if (result.isEmpty()) {
            throw new OrderNotFoundException("未找到手机号 " + phone + " 关联的订单");
        }
        return result;
    }

    public Order queryByOrderIdAndPhone(String orderId, String phone) {
        Order order = queryByOrderId(orderId);
        String normalizedPhone = normalizePhone(phone);
        if (!order.getPhone().equals(normalizedPhone)) {
            throw new OrderNotFoundException("订单 " + orderId + " 与手机号 " + phone + " 不匹配");
        }
        return order;
    }

    public String normalizeOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return orderId;
        }
        String cleaned = orderId.trim().toUpperCase().replaceAll("\\s+", "");
        if (!cleaned.startsWith("ORD-") && cleaned.matches("ORD?\\d+")) {
            cleaned = cleaned.replaceFirst("^ORD", "ORD-");
        }
        if (cleaned.matches("\\d{5}")) {
            cleaned = "ORD-" + cleaned;
        }
        return cleaned;
    }

    public String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        return phone.replaceAll("[^0-9]", "");
    }

    private void validateOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new InvalidParameterException("订单号不能为空");
        }
    }

    private void validatePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new InvalidParameterException("手机号不能为空");
        }
        String normalized = normalizePhone(phone);
        if (normalized.length() != 11) {
            throw new InvalidParameterException("手机号格式不正确: " + phone);
        }
    }

    private void maybeSimulateTransientFailure() {
        if (!simulateTransientFailure) {
            return;
        }
        if (failureCounter.incrementAndGet() % transientFailureRate != 0) {
            throw new RuntimeException("订单服务暂时不可用，请稍后重试");
        }
    }

    private void save(Order order) {
        orders.put(order.getOrderId(), order);
    }
}
