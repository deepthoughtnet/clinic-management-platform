package com.deepthoughtnet.clinic.api.config;

import com.deepthoughtnet.clinic.platform.spring.graphql.RequestContextGraphQlInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.server.WebGraphQlInterceptor;

@Configuration
public class GraphQlConfig {
    @Bean
    public WebGraphQlInterceptor requestContextGraphQlInterceptor() {
        return new RequestContextGraphQlInterceptor();
    }
}
