package com.restaurant.system.integration.ubereats;

import static org.assertj.core.api.Assertions.assertThat;

import com.restaurant.system.integration.ubereats.config.UberEatsProperties;
import com.restaurant.system.integration.ubereats.service.UberEatsWebhookService;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

class UberEatsWebhookSignatureTest {
    private final UberEatsProperties config = new UberEatsProperties();
    private final UberEatsWebhookService service =
            new UberEatsWebhookService(config, null, null, null, null, null, null, null);
    private final byte[] body =
            "{\"event_type\":\"internal.signature_test\"}\n".getBytes(StandardCharsets.UTF_8);

    @Test
    void independentKeyReplacesOAuthSecretAndPreservesRawBytes() throws Exception {
        config.setClientSecret("fixture-oauth-only");
        config.setWebhookSigningKey("fixture-independent-webhook");
        String signature = sign(body, "fixture-independent-webhook");
        assertThat(service.validSignature(body, signature)).isTrue();
        assertThat(service.validSignature(body, sign(body, "fixture-oauth-only"))).isFalse();
        assertThat(service.validSignature(body, sign(body, "fixture-wrong-key"))).isFalse();
        assertThat(service.validSignature("{}".getBytes(StandardCharsets.UTF_8), signature))
                .isFalse();
        assertThat(
                        service.validSignature(
                                new String(body, StandardCharsets.UTF_8)
                                        .strip()
                                        .getBytes(StandardCharsets.UTF_8),
                                signature))
                .isFalse();
        assertThat(config.clientSecret()).isEqualTo("fixture-oauth-only");
    }

    @Test
    void missingExplicitKeyRetainsLegacyCompatibility() throws Exception {
        config.setClientSecret("fixture-legacy-webhook");
        assertThat(service.validSignature(body, sign(body, "fixture-legacy-webhook"))).isTrue();
    }

    @Test
    void missingKeyAndMalformedSignaturesFailClosed() throws Exception {
        String signature = sign(body, "fixture-webhook");
        assertThat(service.validSignature(body, signature)).isFalse();
        config.setWebhookSigningKey("fixture-webhook");
        assertThat(service.validSignature(body, null)).isFalse();
        assertThat(service.validSignature(body, "")).isFalse();
        assertThat(service.validSignature(body, signature.substring(1))).isFalse();
        assertThat(service.validSignature(body, "z".repeat(64))).isFalse();
        assertThat(service.validSignature(body, signature.toUpperCase())).isFalse();
    }

    private static String sign(byte[] raw, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(raw));
    }
}
