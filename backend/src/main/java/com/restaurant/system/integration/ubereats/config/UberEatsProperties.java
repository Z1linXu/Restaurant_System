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

    @Value("${UBER_EATS_WEBHOOK_SIGNING_KEY:}")
    private String webhookSigningKey = "";

    @Value("${UBER_EATS_SCOPES:eats.order eats.store.orders.read}")
    public String scopes = "eats.order eats.store.orders.read";

    @Value("${UBER_EATS_BUSINESS_TIME_ZONE:America/Toronto}")
    public String businessTimeZone = "America/Toronto";

    public String clientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String value) {
        clientSecret = value;
    }

    public String webhookSigningKey() {
        // Preserve existing installations; an explicit webhook key replaces the OAuth secret.
        return webhookSigningKey.isBlank() ? clientSecret : webhookSigningKey;
    }

    public void setWebhookSigningKey(String value) {
        webhookSigningKey = value;
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
        java.time.ZoneId.of(businessTimeZone);
        if (!"sandbox".equals(environment) && !"production".equals(environment))
            throw new IllegalStateException("UBER_EATS_ENVIRONMENT must be sandbox or production");
        if (enabled && (clientId.isBlank() || clientSecret.isBlank()))
            throw new IllegalStateException("Uber Eats backend credentials are missing");
        if (enabled && !java.util.Arrays.asList(scopes.split(" +")).contains("eats.order"))
            throw new IllegalStateException("Uber Eats requires eats.order scope");
    }
}
