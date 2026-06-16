package com.ucp.poc.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.ContentCachingRequestWrapper;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * UCP Web Configuration
 *
 * Configures:
 * - CORS for browser access
 * - UCP validation interceptor (only if validation enabled)
 * - Request caching filter (only if validation enabled)
 */
@Configuration
@RequiredArgsConstructor
public class UcpWebConfig implements WebMvcConfigurer {

    private final UcpValidationInterceptor validationInterceptor;

    @Value("${ucp.validation.enabled:false}")
    private boolean validationEnabled;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Only add validation interceptor if validation is enabled
        if (validationEnabled) {
            registry.addInterceptor(validationInterceptor)
                    .addPathPatterns("/checkout-sessions/**", "/ucp/**")
                    .excludePathPatterns("/.well-known/**");
        }
    }

    /**
     * Configure CORS for UCP endpoints
     * Allows browser-based clients to access the API
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("UCP-Agent")
                .maxAge(3600);
    }

    /**
     * Request caching filter to allow reading request body multiple times
     * Required for validation interceptor to read body before controller
     * Only registered if validation is enabled
     */
    @Bean
    public FilterRegistrationBean<ContentCachingFilter> contentCachingFilter() {
        FilterRegistrationBean<ContentCachingFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new ContentCachingFilter());
        registrationBean.addUrlPatterns("/checkout-sessions/*", "/ucp/*");
        registrationBean.setOrder(1);
        registrationBean.setEnabled(validationEnabled);  // Only enable if validation is enabled
        return registrationBean;
    }

    /**
     * Filter to wrap request with ContentCachingRequestWrapper
     * This is only used when validation is enabled
     */
    public static class ContentCachingFilter implements Filter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            if (request instanceof HttpServletRequest) {
                HttpServletRequest httpRequest = (HttpServletRequest) request;
                ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(httpRequest);
                chain.doFilter(wrappedRequest, response);
            } else {
                chain.doFilter(request, response);
            }
        }
    }
}
