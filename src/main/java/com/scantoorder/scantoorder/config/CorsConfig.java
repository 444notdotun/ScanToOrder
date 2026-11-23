package com.scantoorder.scantoorder.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Which sites may call the API from a browser. ALLOWED_ORIGINS is a comma-separated list (www and the bare domain, say, or the old
 * address next to the new one); when it is empty the one FRONTEND_URL is allowed, as before.
 */
@Configuration
public class CorsConfig {

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    static String[] originsFrom(String list, String fallback) {
        String[] listed = list == null ? new String[0]
                : Arrays.stream(list.split(",")).map(String::trim).map(origin -> origin.replaceAll("/+$", "")).filter(origin -> !origin.isEmpty()).toArray(String[]::new);
        return listed.length > 0 ? listed : new String[]{fallback.trim().replaceAll("/+$", "")};
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins(originsFrom(allowedOrigins, frontendUrl))
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .exposedHeaders("Content-Disposition")
                        .allowCredentials(true);
            }
        };
    }
}
