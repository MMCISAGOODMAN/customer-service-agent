package com.example.ai.customerservice.infrastructure.resilience;

import com.example.ai.customerservice.domain.exception.InvalidParameterException;
import com.example.ai.customerservice.domain.exception.OrderNotFoundException;
import com.example.ai.customerservice.domain.model.Order;
import com.example.ai.customerservice.infrastructure.order.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResilientOrderService {

    private final OrderService orderService;
    private final ParameterCorrector parameterCorrector;

    /**
     * ai: 不静默修正，格式错误返回给 LLM 走 ReAct 重试。
     * infrastructure: 代码层静默修正参数（生产容错）。
     */
    @Value("${app.resilience.param-correction-mode:ai}")
    private String paramCorrectionMode;

    @Retryable(
            retryFor = {RuntimeException.class},
            noRetryFor = {InvalidParameterException.class},
            maxAttemptsExpression = "${app.resilience.order-retry.max-attempts:3}",
            backoff = @Backoff(
                    delayExpression = "${app.resilience.order-retry.delay-ms:500}",
                    multiplierExpression = "${app.resilience.order-retry.multiplier:2.0}"
            )
    )
    public Order queryByOrderIdWithRetry(String orderId) {
        if (isAiCorrectionMode()) {
            log.debug("Query order by id (ai mode, no silent correction): {}", orderId);
            return orderService.queryByOrderId(orderId);
        }
        String correctedId = parameterCorrector.correctOrderId(orderId);
        log.debug("Query order by id: original={}, corrected={}", orderId, correctedId);
        try {
            return orderService.queryByOrderId(correctedId);
        } catch (OrderNotFoundException e) {
            String retryId = parameterCorrector.correctOrderId(orderId);
            if (!retryId.equals(correctedId)) {
                log.info("Retrying order query with corrected id: {} -> {}", orderId, retryId);
                return orderService.queryByOrderId(retryId);
            }
            throw e;
        }
    }

    @Retryable(
            retryFor = {RuntimeException.class},
            noRetryFor = {InvalidParameterException.class, OrderNotFoundException.class},
            maxAttemptsExpression = "${app.resilience.order-retry.max-attempts:3}",
            backoff = @Backoff(
                    delayExpression = "${app.resilience.order-retry.delay-ms:500}",
                    multiplierExpression = "${app.resilience.order-retry.multiplier:2.0}"
            )
    )
    public List<Order> queryByPhoneWithRetry(String phone) {
        if (isAiCorrectionMode()) {
            return orderService.queryByPhone(phone);
        }
        String correctedPhone = parameterCorrector.correctPhone(phone);
        log.debug("Query orders by phone: original={}, corrected={}", phone, correctedPhone);
        return orderService.queryByPhone(correctedPhone);
    }

    @Retryable(
            retryFor = {RuntimeException.class},
            noRetryFor = {InvalidParameterException.class},
            maxAttemptsExpression = "${app.resilience.order-retry.max-attempts:3}",
            backoff = @Backoff(
                    delayExpression = "${app.resilience.order-retry.delay-ms:500}",
                    multiplierExpression = "${app.resilience.order-retry.multiplier:2.0}"
            )
    )
    public Order queryByOrderIdAndPhoneWithRetry(String orderId, String phone) {
        if (isAiCorrectionMode()) {
            return orderService.queryByOrderIdAndPhone(orderId, phone);
        }
        String correctedId = parameterCorrector.correctOrderId(orderId);
        String correctedPhone = parameterCorrector.correctPhone(phone);
        log.debug("Query order: orderId {} -> {}, phone {} -> {}", orderId, correctedId, phone, correctedPhone);
        try {
            return orderService.queryByOrderIdAndPhone(correctedId, correctedPhone);
        } catch (OrderNotFoundException e) {
            String altId = parameterCorrector.correctOrderId(orderId);
            String altPhone = parameterCorrector.correctPhone(phone);
            if (!altId.equals(correctedId) || !altPhone.equals(correctedPhone)) {
                return orderService.queryByOrderIdAndPhone(altId, altPhone);
            }
            throw e;
        }
    }

    private boolean isAiCorrectionMode() {
        return !"infrastructure".equalsIgnoreCase(paramCorrectionMode);
    }

    @Recover
    public Order recoverQueryByOrderId(RuntimeException ex, String orderId) {
        log.error("Order query failed after retries for orderId={}: {}", orderId, ex.getMessage());
        throw ex;
    }

    @Recover
    public List<Order> recoverQueryByPhone(RuntimeException ex, String phone) {
        log.error("Order query failed after retries for phone={}: {}", phone, ex.getMessage());
        throw ex;
    }
}
