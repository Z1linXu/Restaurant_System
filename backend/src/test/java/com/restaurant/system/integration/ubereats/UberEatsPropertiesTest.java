package com.restaurant.system.integration.ubereats;

import static org.assertj.core.api.Assertions.*;

import com.restaurant.system.integration.ubereats.config.UberEatsProperties;

import org.junit.jupiter.api.Test;

class UberEatsPropertiesTest {
    @Test
    void disabledOptionalIntegrationNeedsNoCredentials() {
        var config = new UberEatsProperties();
        config.runtimeEnvironment = "production";
        assertThatCode(config::validate).doesNotThrowAnyException();
    }

    @Test
    void kitchenMirrorAcceptsReadonlyScopesButRequiresOrderRead() {
        var config = enabledConfig();
        config.processingMode = "KITCHEN_MIRROR";
        config.scopes = "eats.store\t eats.store.orders.read";
        assertThatCode(config::validate).doesNotThrowAnyException();
        config.scopes = "eats.store eats.order";
        assertThatThrownBy(config::validate).hasMessageContaining("requires eats.store.orders.read");
    }

    @Test
    void orderManagerRetainsWriteScopeRequirement() {
        var config = enabledConfig();
        assertThatCode(config::validate).doesNotThrowAnyException();
        config.scopes = "eats.store eats.store.orders.read";
        assertThatThrownBy(config::validate).hasMessageContaining("requires eats.order");
    }

    @Test
    void invalidGlobalModeFailsClosedBeforeBindings() {
        var config = enabledConfig();
        config.processingMode = "mirror";
        assertThatThrownBy(config::validate).hasMessageContaining("UBER_EATS_PROCESSING_MODE");
        assertThatThrownBy(config::requireOrderManagerMode)
                .hasMessage("KITCHEN_MIRROR_REMOTE_DECISION_DISABLED");
    }

    @Test
    void productionRuntimeCannotAccidentallyUseSandboxDefault() {
        var config = enabledConfig();
        config.runtimeEnvironment = "production";
        config.setWebhookSigningKey("fixture-production-signing-key");
        assertThatThrownBy(config::validate)
                .hasMessage("Production Uber Eats requires UBER_EATS_ENVIRONMENT=production");
        config.environment = "production";
        assertThatCode(config::validate).doesNotThrowAnyException();
        assertThat(config.apiBaseUrl()).isEqualTo("https://api.uber.com");
        assertThat(config.tokenUrl()).isEqualTo("https://auth.uber.com/oauth/v2/token");
    }

    @Test
    void productionRequiresExplicitSigningKeyAndNeverReliesOnOAuthSecret() {
        var config = enabledConfig();
        config.environment = "production";
        assertThat(config.webhookSigningKey()).isEmpty();
        assertThatThrownBy(config::validate).hasMessageContaining("explicit webhook signing key");
        config.setWebhookSigningKey("fixture-production-signing-key");
        assertThatCode(config::validate).doesNotThrowAnyException();
        assertThat(config.webhookSigningKey()).isEqualTo("fixture-production-signing-key");
    }

    @Test
    void receiveOnlyProductionDoesNotNeedOAuthCredentials() {
        var config = new UberEatsProperties();
        config.enabled = true;
        config.workerEnabled = false;
        config.runtimeEnvironment = "production";
        config.environment = "production";
        config.processingMode = "KITCHEN_MIRROR";
        config.scopes = "eats.store eats.store.orders.read";
        config.setWebhookSigningKey("fixture-production-signing-key");
        assertThatCode(config::validate).doesNotThrowAnyException();
        assertThatThrownBy(config::requireProcessingEnabled).hasMessage("UBER_PROCESSING_PAUSED");
        config.workerEnabled = true;
        assertThatThrownBy(config::validate).hasMessage("Uber Eats backend credentials are missing");
    }

    @Test
    void receiveOnlyStillNeedsSigningKey() {
        var config = new UberEatsProperties();
        config.enabled = true;
        config.workerEnabled = false;
        assertThatThrownBy(config::validate).hasMessage("Uber Eats webhook signing key is missing");
    }

    @Test
    void sandboxKeepsLegacyClientSecretSignatureFallback() {
        var config = enabledConfig();
        assertThatCode(config::validate).doesNotThrowAnyException();
        assertThat(config.webhookSigningKey()).isEqualTo(config.clientSecret());
        assertThat(config.apiBaseUrl()).isEqualTo("https://test-api.uber.com");
        assertThat(config.tokenUrl()).isEqualTo("https://sandbox-login.uber.com/oauth/v2/token");
    }

    @Test
    void testWebhookCompatibilityCannotLeakIntoProduction() {
        var config = enabledConfig();
        config.environment = "production";
        config.runtimeEnvironment = "production";
        config.testWebhookClientId = config.clientId;
        config.testWebhookStoreId = "00000000-0000-4000-8000-000000000001";
        config.setWebhookSigningKey("fixture-production-signing-key");
        assertThatThrownBy(config::validate).hasMessageContaining("exact Staging Sandbox");
    }

    private UberEatsProperties enabledConfig() {
        var config = new UberEatsProperties();
        config.enabled = true;
        config.clientId = "fixture-client";
        config.setClientSecret("fixture-secret");
        return config;
    }
}
