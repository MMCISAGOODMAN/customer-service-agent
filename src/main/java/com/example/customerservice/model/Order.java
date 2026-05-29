package com.example.customerservice.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class Order {

    private String orderId;
    private String customerName;
    private String phone;
    private String productName;
    private int quantity;
    private BigDecimal amount;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private String shippingAddress;

    public enum OrderStatus {
        PENDING, PAID, SHIPPED, DELIVERED, CANCELLED
    }
}
