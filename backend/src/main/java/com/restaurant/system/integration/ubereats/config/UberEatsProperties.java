package com.restaurant.system.integration.ubereats.config;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Credentials are deliberately not a record/Data class: never generate a secret-bearing toString.
 */
@Component
public class UberEatsProperties {
    @Value("${UBER_EATS_ENABLED:false}")
    public boolean enabled;

    @Value("${UBER_EATS_ENVIRONMENT:sandbox}")
    public String environment = "sandbox";

    @Value("${UBER_EATS_CLIENT_ID:}")
    public String clientId = "";

    @Value("${UBER_EATS_CLIENT_SECRET:}")
    private String clientSecret = "";

    @Value("${UBER_EATS_SCOPES:eats.order eats.store.orders.read}")
    public String scopes = "eats.order eats.store.orders.read";

    public String clientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String value) {
        clientSecret = value;
    }

    public String apiBaseUrl() {
        return "production".equals(environment)
                ? "https://api.uber.com"
                : "https://test-api.uber.com";
    }

    public String tokenUrl() {
        return ("production".equals(environment)
                        ? "https://auth.uber.com"
                        : "https://sandbox-login.uber.com")
                + "/oauth/v2/token";
    }

    @PostConstruct
    public void validate() {
        if (!"sandbox".equals(environment) && !"production".equals(environment))
            throw new IllegalStateException("UBER_EATS_ENVIRONMENT must be sandbox or production");
        if (enabled && (clientId.isBlank() || clientSecret.isBlank()))
            throw new IllegalStateException("Uber Eats backend credentials are missing");
        if (enabled && !java.util.Arrays.asList(scopes.split(" +")).contains("eats.order"))
            throw new IllegalStateException("Uber Eats requires eats.order scope");
    }
}
