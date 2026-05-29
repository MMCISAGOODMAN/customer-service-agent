package com.example.ai.customerservice.infrastructure.fallback;

import com.example.ai.customerservice.infrastructure.order.OrderService;
import com.example.ai.customerservice.infrastructure.resilience.ParameterCorrector;
import com.example.ai.customerservice.infrastructure.resilience.ResilientOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedFallbackServiceTest {

    private RuleBasedFallbackService fallbackService;

    @BeforeEach
    void setUp() {
        OrderService orderService = new OrderService();
        ReflectionTestUtils.invokeMethod(orderService, "initSampleData");
        ParameterCorrector corrector = new ParameterCorrector(orderService);
        ResilientOrderService resilientOrderService = new ResilientOrderService(orderService, corrector);
        ReflectionTestUtils.setField(resilientOrderService, "paramCorrectionMode", "infrastructure");
        fallbackService = new RuleBasedFallbackService(resilientOrderService, corrector);
    }

    @Test
    void shouldQueryOrderInFallbackMode() {
        String reply = fallbackService.handle("查询订单 ORD-10001");
        assertTrue(reply.contains("ORD-10001"));
        assertTrue(reply.contains("降级模式"));
    }

    @Test
    void shouldAnswerFaqInFallbackMode() {
        String reply = fallbackService.handle("怎么退货？");
        assertTrue(reply.contains("7天"));
    }
}
