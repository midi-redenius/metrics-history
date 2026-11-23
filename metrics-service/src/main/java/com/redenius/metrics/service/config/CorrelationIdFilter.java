package com.redenius.metrics.service.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(1)
public class CorrelationIdFilter implements Filter {

    private static final String CORRELATION_ID = "correlationId";
    private static final String HEADER_NAME = "X-Correlation-Id";
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            String correlationId = null;
            if (request instanceof HttpServletRequest httpRequest) {
                correlationId = httpRequest.getHeader(HEADER_NAME);
            }
            if (!isValid(correlationId)) {
                correlationId = UUID.randomUUID().toString();
            }
            MDC.put(CORRELATION_ID, correlationId);
            if (response instanceof HttpServletResponse httpResponse) {
                httpResponse.setHeader(HEADER_NAME, correlationId);
            }
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID);
        }
    }

    public static boolean isValid(String correlationId) {
        return correlationId != null && VALID_ID.matcher(correlationId).matches();
    }
}
