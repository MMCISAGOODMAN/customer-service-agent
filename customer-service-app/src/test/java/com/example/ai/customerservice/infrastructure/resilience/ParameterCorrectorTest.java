package com.example.ai.customerservice.infrastructure.resilience;

import com.example.ai.customerservice.infrastructure.order.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParameterCorrectorTest {

    private ParameterCorrector corrector;

    @BeforeEach
    void setUp() {
        corrector = new ParameterCorrector(new OrderService());
    }

    @Test
    void shouldCorrectOrderIdVariants() {
        assertEquals("ORD-10001", corrector.correctOrderId("ord 10001"));
        assertEquals("ORD-10001", corrector.correctOrderId("ORD10001"));
        assertEquals("ORD-10001", corrector.correctOrderId("10001"));
    }

    @Test
    void shouldExtractPhoneFromMessage() {
        var params = corrector.extractFromMessage("我的订单号是 ORD-10002，手机 139-0013-9002");
        assertEquals("ORD-10002", params.orderId());
        assertEquals("13900139002", params.phone());
    }
}
