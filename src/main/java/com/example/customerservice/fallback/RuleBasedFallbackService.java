package com.example.customerservice.fallback;

import com.example.customerservice.exception.OrderNotFoundException;
import com.example.customerservice.model.Order;
import com.example.customerservice.resilience.ResilientOrderService;
import com.example.customerservice.resilience.ParameterCorrector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuleBasedFallbackService {

    private final ResilientOrderService resilientOrderService;
    private final ParameterCorrector parameterCorrector;

    private static final Map<String, String> FAQ = Map.of(
            "退货", "您可在签收后7天内申请退货，请确保商品完好。登录「我的订单」-> 申请售后即可。",
            "换货", "换货政策与退货相同，请在7天内提交换货申请，客服将在24小时内处理。",
            "配送", "标准配送3-5个工作日，加急配送1-2个工作日。发货后可在订单详情查看物流。",
            "发票", "下单时可选择电子发票，付款成功后24小时内发送至您的邮箱。",
            "支付", "支持微信、支付宝、银行卡支付。支付失败请检查余额或更换支付方式。",
            "客服", "人工客服工作时间 9:00-21:00，可拨打 400-888-0000。"
    );

    public String handle(String message) {
        log.warn("Activating rule-based fallback for message: {}", message);
        String lower = message.toLowerCase(Locale.ROOT);

        if (containsOrderIntent(lower)) {
            return handleOrderQuery(message);
        }

        for (Map.Entry<String, String> entry : FAQ.entrySet()) {
            if (message.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return """
                抱歉，AI 服务暂时不可用，已切换至基础客服模式。
                您可以：
                1. 提供订单号（如 ORD-10001）查询订单
                2. 提供手机号查询关联订单
                3. 咨询：退货、换货、配送、发票、支付等问题
                4. 稍后重试或拨打客服热线 400-888-0000
                """;
    }

    private boolean containsOrderIntent(String lower) {
        return lower.contains("订单") || lower.contains("ord")
                || lower.contains("查询") || lower.contains("物流")
                || lower.contains("发货") || lower.contains("快递");
    }

    private String handleOrderQuery(String message) {
        ParameterCorrector.ExtractedParams params = parameterCorrector.extractFromMessage(message);
        if (!params.hasAny()) {
            return "请提供订单号（如 ORD-10001）或注册手机号，我将为您查询订单状态。";
        }

        try {
            if (params.orderId() != null && params.phone() != null) {
                Order order = resilientOrderService.queryByOrderIdAndPhoneWithRetry(
                        params.orderId(), params.phone());
                return formatFallbackOrder(order);
            }
            if (params.orderId() != null) {
                Order order = resilientOrderService.queryByOrderIdWithRetry(params.orderId());
                return formatFallbackOrder(order);
            }
            List<Order> orders = resilientOrderService.queryByPhoneWithRetry(params.phone());
            if (orders.size() == 1) {
                return formatFallbackOrder(orders.get(0));
            }
            return "找到 " + orders.size() + " 个订单：\n"
                    + orders.stream().map(this::formatFallbackOrder).reduce((a, b) -> a + "\n---\n" + b).orElse("");
        } catch (OrderNotFoundException e) {
            return "未找到相关订单，请核对订单号或手机号后重试。错误: " + e.getMessage();
        } catch (Exception e) {
            return "订单查询失败: " + e.getMessage() + "。请稍后重试或联系人工客服 400-888-0000。";
        }
    }

    private String formatFallbackOrder(Order order) {
        return String.format(
                "[降级模式] 订单 %s | 商品: %s | 状态: %s | 金额: %s元 | 地址: %s",
                order.getOrderId(),
                order.getProductName(),
                order.getStatus(),
                order.getAmount(),
                order.getShippingAddress()
        );
    }
}
