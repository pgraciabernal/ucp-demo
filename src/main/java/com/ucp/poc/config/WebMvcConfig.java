package com.ucp.poc.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private PoppySecurityInterceptor securityInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(securityInterceptor)
                // Apply protection for specific endpoints
                .addPathPatterns("/ucp/checkout", "/ucp/orders/**", "/ucp/payments/**")
                // Endpoints excluded
                .excludePathPatterns("/ucp/products/**");
    }
}
