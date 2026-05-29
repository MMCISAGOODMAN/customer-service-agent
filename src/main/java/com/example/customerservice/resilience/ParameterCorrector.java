package com.example.customerservice.resilience;

import com.example.customerservice.order.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class ParameterCorrector {

    private static final Pattern ORDER_ID_PATTERN = Pattern.compile(
            "(?:ORD[-\\s]?)?(\\d{4,6})",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");

    private final OrderService orderService;

    public String correctOrderId(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String trimmed = raw.trim();
        String normalized = orderService.normalizeOrderId(trimmed);

        Matcher matcher = ORDER_ID_PATTERN.matcher(trimmed.replaceAll("[^A-Za-z0-9\\-\\s]", ""));
        if (matcher.find()) {
            String digits = matcher.group(1);
            String candidate = "ORD-" + digits;
            if (!candidate.equals(normalized)) {
                log.info("Parameter correction: orderId '{}' -> '{}'", raw, candidate);
                return candidate;
            }
        }

        if (trimmed.matches("(?i)ord\\s*(\\d+)")) {
            String candidate = "ORD-" + trimmed.replaceAll("(?i)[^0-9]", "");
            log.info("Parameter correction: orderId '{}' -> '{}'", raw, candidate);
            return candidate;
        }

        return normalized;
    }

    public String correctPhone(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String digitsOnly = raw.replaceAll("[^0-9]", "");
        Matcher matcher = PHONE_PATTERN.matcher(digitsOnly);
        if (matcher.find()) {
            String phone = matcher.group();
            if (!phone.equals(digitsOnly)) {
                log.info("Parameter correction: phone '{}' -> '{}'", raw, phone);
            }
            return phone;
        }
        return orderService.normalizePhone(raw);
    }

    public ExtractedParams extractFromMessage(String message) {
        if (message == null || message.isBlank()) {
            return new ExtractedParams(null, null);
        }
        String orderId = null;
        String phone = null;

        Matcher orderMatcher = Pattern.compile("(?i)(?:ORD[-\\s]?\\d{4,6}|订单[号]?[:：\\s]*[A-Za-z0-9\\-]+)").matcher(message);
        if (orderMatcher.find()) {
            orderId = correctOrderId(orderMatcher.group().replaceAll("订单[号]?[:：\\s]*", ""));
        }

        Matcher phoneMatcher = PHONE_PATTERN.matcher(message.replaceAll("[^0-9]", ""));
        if (phoneMatcher.find()) {
            phone = phoneMatcher.group();
        }

        return new ExtractedParams(orderId, phone);
    }

    public record ExtractedParams(String orderId, String phone) {
        public boolean hasAny() {
            return orderId != null || phone != null;
        }
    }
}
