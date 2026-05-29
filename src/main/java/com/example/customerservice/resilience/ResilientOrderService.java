package com.example.customerservice.resilience;

import com.example.customerservice.exception.InvalidParameterException;
import com.example.customerservice.exception.OrderNotFoundException;
import com.example.customerservice.model.Order;
import com.example.customerservice.order.OrderService;
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

    @Value("${app.resilience.param-correction-enabled:true}")
    private boolean paramCorrectionEnabled;

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
        String correctedId = paramCorrectionEnabled
                ? parameterCorrector.correctOrderId(orderId)
                : orderService.normalizeOrderId(orderId);
        log.debug("Query order by id: original={}, corrected={}", orderId, correctedId);
        try {
            return orderService.queryByOrderId(correctedId);
        } catch (OrderNotFoundException e) {
            if (paramCorrectionEnabled && !correctedId.equals(orderId)) {
                throw e;
            }
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
        String correctedPhone = paramCorrectionEnabled
                ? parameterCorrector.correctPhone(phone)
                : orderService.normalizePhone(phone);
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
