package com.restaurant.system.integration.ubereats.config;

import com.restaurant.system.integration.ubereats.service.UberEatsException;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Credentials are deliberately not a record/Data class: never generate a secret-bearing toString.
 */
@Component
public class UberEatsProperties {
    @Value("${UBER_EATS_ENABLED:false}")
    public boolean enabled;

    @Value("${UBER_EATS_WORKER_ENABLED:true}")
    public boolean workerEnabled = true;

    // A process-wide permission ceiling, evaluated before Store bindings are loaded.
    // ORDER_MANAGER keeps existing installations compatible; read-only deployments opt in.
    @Value("${UBER_EATS_PROCESSING_MODE:ORDER_MANAGER}")
    public String processingMode = "ORDER_MANAGER";

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

    @Value("${APP_ENVIRONMENT:local}")
    public String runtimeEnvironment = "local";

    @Value("${UBER_EATS_TEST_WEBHOOK_CLIENT_ID:}")
    public String testWebhookClientId = "";

    @Value("${UBER_EATS_TEST_WEBHOOK_STORE_ID:}")
    public String testWebhookStoreId = "";

    public boolean allowsTestWebhook(String header, String storeId) {
        return "staging".equals(runtimeEnvironment)
                && "sandbox".equals(environment)
                && "production".equalsIgnoreCase(header)
                && !testWebhookClientId.isBlank()
                && testWebhookClientId.equals(clientId)
                && !testWebhookStoreId.isBlank()
                && testWebhookStoreId.equals(storeId);
    }

    public String clientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String value) {
        clientSecret = value;
    }

    public String webhookSigningKey() {
        if ("production".equals(environment)) return webhookSigningKey;
        // Preserve existing installations; an explicit webhook key replaces the OAuth secret.
        return webhookSigningKey.isBlank() ? clientSecret : webhookSigningKey;
    }

    public void setWebhookSigningKey(String value) {
        webhookSigningKey = value;
    }

    public boolean kitchenMirror() {
        return "KITCHEN_MIRROR".equals(processingMode);
    }

    public void requireProcessingEnabled() {
        if (!enabled || !workerEnabled)
            throw UberEatsException.conflict("UBER_PROCESSING_PAUSED");
    }

    public void requireOrderManagerMode() {
        if (!"ORDER_MANAGER".equals(processingMode))
            throw UberEatsException.conflict("KITCHEN_MIRROR_REMOTE_DECISION_DISABLED");
    }

    public boolean hasOAuthCredentials() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    public boolean hasRequiredScopes(String grantedScopes) {
        Set<String> values =
                Arrays.stream(grantedScopes.trim().split("\\s+"))
                        .collect(Collectors.toSet());
        return values.contains(kitchenMirror() ? "eats.store.orders.read" : "eats.order");
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
        if (!Set.of("ORDER_MANAGER", "KITCHEN_MIRROR").contains(processingMode))
            throw new IllegalStateException(
                    "UBER_EATS_PROCESSING_MODE must be ORDER_MANAGER or KITCHEN_MIRROR");
        if (!testWebhookClientId.isBlank() || !testWebhookStoreId.isBlank()) {
            if (!"staging".equals(runtimeEnvironment)
                    || !"sandbox".equals(environment)
                    || testWebhookClientId.isBlank()
                    || !testWebhookClientId.equals(clientId)
                    || testWebhookStoreId.isBlank())
                throw new IllegalStateException(
                        "TEST webhook compatibility requires an exact Staging Sandbox app/store"
                            + " pair");
            java.util.UUID.fromString(testWebhookStoreId);
        }
        if (!"sandbox".equals(environment) && !"production".equals(environment))
            throw new IllegalStateException("UBER_EATS_ENVIRONMENT must be sandbox or production");
        if (!enabled) return;
        if ("production".equals(runtimeEnvironment) && !"production".equals(environment))
            throw new IllegalStateException(
                    "Production Uber Eats requires UBER_EATS_ENVIRONMENT=production");
        if ("production".equals(environment) && webhookSigningKey.isBlank())
            throw new IllegalStateException(
                    "Production Uber Eats requires an explicit webhook signing key");
        if (webhookSigningKey().isBlank())
            throw new IllegalStateException("Uber Eats webhook signing key is missing");
        // Receive-only startup needs HMAC authentication, not an OAuth session.
        if (workerEnabled && !hasOAuthCredentials())
            throw new IllegalStateException("Uber Eats backend credentials are missing");
        if (!hasRequiredScopes(scopes))
            throw new IllegalStateException(
                    kitchenMirror()
                            ? "Uber Eats Kitchen Mirror requires eats.store.orders.read scope"
                            : "Uber Eats Order Manager requires eats.order scope");
    }
}
