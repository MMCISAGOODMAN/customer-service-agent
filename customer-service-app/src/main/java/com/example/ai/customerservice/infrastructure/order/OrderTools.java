package com.example.ai.customerservice.infrastructure.order;

import com.example.ai.customerservice.domain.model.Order;
import com.example.ai.customerservice.infrastructure.resilience.ResilientOrderService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OrderTools {

    private final ResilientOrderService resilientOrderService;

    @Tool("根据订单号查询订单详情，订单号格式如 ORD-10001")
    public String queryOrderById(String orderId) {
        Order order = resilientOrderService.queryByOrderIdWithRetry(orderId);
        return formatOrder(order);
    }

    @Tool("根据手机号查询该用户所有订单")
    public String queryOrdersByPhone(String phone) {
        List<Order> orders = resilientOrderService.queryByPhoneWithRetry(phone);
        return orders.stream()
                .map(this::formatOrder)
                .collect(Collectors.joining("\n---\n"));
    }

    @Tool("根据订单号和手机号联合验证并查询订单")
    public String queryOrderByIdAndPhone(String orderId, String phone) {
        Order order = resilientOrderService.queryByOrderIdAndPhoneWithRetry(orderId, phone);
        return formatOrder(order);
    }

    private String formatOrder(Order order) {
        return String.format(
                "订单号: %s, 客户: %s, 手机: %s, 商品: %s, 数量: %d, 金额: %s元, 状态: %s, 下单时间: %s, 地址: %s",
                order.getOrderId(),
                order.getCustomerName(),
                order.getPhone(),
                order.getProductName(),
                order.getQuantity(),
                order.getAmount(),
                translateStatus(order.getStatus()),
                order.getCreatedAt(),
                order.getShippingAddress()
        );
    }

    private String translateStatus(Order.OrderStatus status) {
        return switch (status) {
            case PENDING -> "待付款";
            case PAID -> "已付款";
            case SHIPPED -> "已发货";
            case DELIVERED -> "已签收";
            case CANCELLED -> "已取消";
        };
    }
}
