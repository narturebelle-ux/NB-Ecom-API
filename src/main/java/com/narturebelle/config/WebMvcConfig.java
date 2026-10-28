package com.narturebelle.config;

import com.narturebelle.security.VerifiedCheckInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final VerifiedCheckInterceptor verifiedCheckInterceptor;

    public WebMvcConfig(VerifiedCheckInterceptor verifiedCheckInterceptor) {
        this.verifiedCheckInterceptor = verifiedCheckInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(verifiedCheckInterceptor);
    }
}
