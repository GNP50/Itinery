package com.travel.monitoring.config;

import com.travel.monitoring.service.MonitoringService;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Filter for recording HTTP request metrics.
 * Records request duration, status code, and endpoint information.
 */
@Slf4j
@WebFilter(filterName = "metricsFilter", urlPatterns = "/*")
@RequiredArgsConstructor
public class MetricsFilter implements Filter {

    private final MonitoringService monitoringService;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        long startTime = System.currentTimeMillis();
        int statusCode = 200;

        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException e) {
            statusCode = 500;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;

            // Record the request metrics
            if (request instanceof HttpServletRequest) {
                HttpServletRequest httpRequest = (HttpServletRequest) request;
                String method = httpRequest.getMethod();
                String path = httpRequest.getRequestURI();
                String queryString = httpRequest.getQueryString();

                String endpoint = path;
                if (queryString != null && !queryString.isEmpty()) {
                    endpoint = path + "?" + queryString;
                }

                monitoringService.recordHttpRequest(method, endpoint, duration, statusCode);
            }
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("MetricsFilter initialized");
    }

    @Override
    public void destroy() {
        log.info("MetricsFilter destroyed");
    }
}
