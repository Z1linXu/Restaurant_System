package com.restaurant.system.template.bootstrap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Profile({"template-test", "allo-test"})
public class TemplateTestCorsConfig implements WebMvcConfigurer {
    private final Environment environment;
    public TemplateTestCorsConfig(Environment environment) { this.environment = environment; }
    @Override public void addCorsMappings(CorsRegistry registry) {
        var origins = new ArrayList<>(List.of("http://127.0.0.1:28080", "https://test.lanzhounoodlesmtl.com",
            "https://pos.allonouilles.com", "http://127.0.0.1:28180", "http://localhost:28180"));
        // An explicit synthetic QA profile cannot broaden the deployed production CORS list.
        if (Arrays.asList(environment.getActiveProfiles()).contains("allo-qa")) origins.add("http://127.0.0.1:28182");
        registry.addMapping("/api/**").allowedOrigins(origins.toArray(String[]::new))
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("Content-Type", "Authorization", "Accept", "X-Print-Device-Id", "X-Print-Timestamp", "X-Print-Signature")
            .allowCredentials(false).maxAge(3600);
    }
}
