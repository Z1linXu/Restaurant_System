package com.restaurant.system.integration.ubereats;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.restaurant.system.integration.ubereats.client.*;
import com.restaurant.system.integration.ubereats.config.UberEatsProperties;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.*;

class UberEatsOAuthClientTest {
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        public Instant instant() {
            return now;
        }
    }

    @Test
    void cachesAndRefreshesBeforeExpiryAndUsesSandboxOnly() {
        var config = new UberEatsProperties();
        config.enabled = true;
        config.clientId = "fixture-client";
        config.setClientSecret("fixture-secret");
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var clock = new MutableClock();
        server.expect(requestTo("https://sandbox-login.uber.com/oauth/v2/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.containsString(
                                                "grant_type=client_credentials")))
                .andRespond(
                        withSuccess(
                                "{\"access_token\":\"fixture-token-1\",\"expires_in\":100}",
                                MediaType.APPLICATION_JSON));
        server.expect(requestTo(config.tokenUrl()))
                .andRespond(
                        withSuccess(
                                "{\"access_token\":\"fixture-token-2\",\"expires_in\":100}",
                                MediaType.APPLICATION_JSON));
        var client = new UberEatsOAuthClient(config, builder.build(), clock);
        assertThat(client.accessToken()).isEqualTo("fixture-token-1");
        assertThat(client.accessToken()).isEqualTo("fixture-token-1");
        clock.now = clock.now.plusSeconds(91);
        assertThat(client.accessToken()).isEqualTo("fixture-token-2");
        server.verify();
    }

    @Test
    void errorDoesNotLeakResponseOrSecret() {
        var config = new UberEatsProperties();
        config.enabled = true;
        config.clientId = "fixture-client";
        config.setClientSecret("fixture-private-value");
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(config.tokenUrl()))
                .andRespond(
                        withStatus(HttpStatus.UNAUTHORIZED)
                                .body("fixture-private-value fixture-access-token"));
        assertThatThrownBy(
                        () ->
                                new UberEatsOAuthClient(config, builder.build(), Clock.systemUTC())
                                        .accessToken())
                .hasMessage("UBER_API_401")
                .hasNoCause();
        server.verify();
    }

    @Test
    void kitchenMirrorUsesConfiguredReadonlyGrantAndGetsOrder() {
        var config = readonlyConfig();
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        String id = "00000000-0000-4000-8000-000000000001";
        server.expect(requestTo(config.tokenUrl()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.containsString(
                                                "scope=eats.store+eats.store.orders.read")))
                .andRespond(
                        withSuccess(
                                "{\"access_token\":\"fixture-read-token\",\"expires_in\":100,"
                                        + "\"scope\":\"eats.store eats.store.orders.read\"}",
                                MediaType.APPLICATION_JSON));
        server.expect(requestTo(config.apiBaseUrl() + "/v2/eats/order/" + id))
                .andExpect(header("Authorization", "Bearer fixture-read-token"))
                .andRespond(withSuccess("{\"id\":\"" + id + "\"}", MediaType.APPLICATION_JSON));
        var http = builder.build();
        var oauth = new UberEatsOAuthClient(config, http, Clock.systemUTC());
        assertThat(new UberEatsOrderClient(config, oauth, http).getOrder(id).path("id").asText())
                .isEqualTo(id);
        assertThat(oauth.accessToken()).isEqualTo("fixture-read-token");
        server.verify();
    }

    @Test
    void missingCriticalGrantedScopeIsRejectedAndNeverCached() {
        var config = readonlyConfig();
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(config.tokenUrl()))
                .andRespond(
                        withSuccess(
                                "{\"access_token\":\"fixture-unusable\",\"expires_in\":100,"
                                        + "\"scope\":\"eats.store eats.order\"}",
                                MediaType.APPLICATION_JSON));
        server.expect(requestTo(config.tokenUrl()))
                .andRespond(
                        withSuccess(
                                "{\"access_token\":\"fixture-valid\",\"expires_in\":100,"
                                        + "\"scope\":\"eats.store.orders.read\"}",
                                MediaType.APPLICATION_JSON));
        var oauth = new UberEatsOAuthClient(config, builder.build(), Clock.systemUTC());
        assertThatThrownBy(oauth::accessToken).hasMessage("UBER_API_403").hasNoCause();
        assertThat(oauth.accessToken()).isEqualTo("fixture-valid");
        assertThat(oauth.accessToken()).isEqualTo("fixture-valid");
        server.verify();
    }

    @Test
    void managerRejectsReadonlyGrantedToken() {
        var config = readonlyConfig();
        config.processingMode = "ORDER_MANAGER";
        config.scopes = "eats.order eats.store.orders.read";
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(config.tokenUrl()))
                .andRespond(
                        withSuccess(
                                "{\"access_token\":\"fixture-read-token\",\"expires_in\":100,"
                                        + "\"scope\":\"eats.store.orders.read\"}",
                                MediaType.APPLICATION_JSON));
        var oauth = new UberEatsOAuthClient(config, builder.build(), Clock.systemUTC());
        assertThatThrownBy(oauth::accessToken).hasMessage("UBER_API_403");
        server.verify();
    }

    @Test
    void kitchenMirrorBlocksRemoteDecisionsBeforeTokenOrHttp() {
        var config = readonlyConfig();
        var oauth = org.mockito.Mockito.mock(UberEatsOAuthClient.class);
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var orders = new UberEatsOrderClient(config, oauth, builder.build());
        String id = "00000000-0000-4000-8000-000000000001";
        assertThatThrownBy(() -> orders.accept(id, "fixture-reference"))
                .hasMessage("KITCHEN_MIRROR_REMOTE_DECISION_DISABLED");
        assertThatThrownBy(() -> orders.deny(id, "CAPACITY"))
                .hasMessage("KITCHEN_MIRROR_REMOTE_DECISION_DISABLED");
        org.mockito.Mockito.verifyNoInteractions(oauth);
        server.verify();
    }

    @Test
    void receiveOnlyCannotFetchTokenWithoutCredentialsEvenWhenConfiguredScopeIsValid() {
        var config = readonlyConfig();
        config.workerEnabled = false;
        config.clientId = "";
        config.setClientSecret("");
        config.setWebhookSigningKey("fixture-signing-key");
        config.validate();
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var oauth = new UberEatsOAuthClient(config, builder.build(), Clock.systemUTC());
        assertThatThrownBy(oauth::accessToken).hasMessage("UBER_API_503").hasNoCause();
        server.verify();
    }

    @Test
    void token401ClearsCacheAndNextRequestRefreshes() {
        var config = readonlyConfig();
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        String id = "00000000-0000-4000-8000-000000000001";
        server.expect(requestTo(config.tokenUrl()))
                .andRespond(
                        withSuccess(
                                "{\"access_token\":\"fixture-token-1\",\"expires_in\":100}",
                                MediaType.APPLICATION_JSON));
        server.expect(requestTo(config.apiBaseUrl() + "/v2/eats/order/" + id))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(config.tokenUrl()))
                .andRespond(
                        withSuccess(
                                "{\"access_token\":\"fixture-token-2\",\"expires_in\":100}",
                                MediaType.APPLICATION_JSON));
        server.expect(requestTo(config.apiBaseUrl() + "/v2/eats/order/" + id))
                .andExpect(header("Authorization", "Bearer fixture-token-2"))
                .andRespond(withSuccess("{\"id\":\"" + id + "\"}", MediaType.APPLICATION_JSON));
        var http = builder.build();
        var oauth = new UberEatsOAuthClient(config, http, Clock.systemUTC());
        var orders = new UberEatsOrderClient(config, oauth, http);
        assertThatThrownBy(() -> orders.getOrder(id)).hasMessage("UBER_API_401");
        assertThat(orders.getOrder(id).path("id").asText()).isEqualTo(id);
        server.verify();
    }

    private UberEatsProperties readonlyConfig() {
        var config = new UberEatsProperties();
        config.enabled = true;
        config.processingMode = "KITCHEN_MIRROR";
        config.scopes = "eats.store eats.store.orders.read";
        config.clientId = "fixture-client";
        config.setClientSecret("fixture-secret");
        return config;
    }

    @Test
    void verifiedOrderEndpointPathsAndBodies() {
        var config = new UberEatsProperties();
        config.enabled = true;
        var oauth = org.mockito.Mockito.mock(UberEatsOAuthClient.class);
        org.mockito.Mockito.when(oauth.accessToken()).thenReturn("fixture-token");
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        String id = "00000000-0000-4000-8000-000000000001";
        server.expect(requestTo(config.apiBaseUrl() + "/v2/eats/order/" + id))
                .andExpect(header("Authorization", "Bearer fixture-token"))
                .andRespond(withSuccess("{\"id\":\"" + id + "\"}", MediaType.APPLICATION_JSON));
        server.expect(
                        requestTo(
                                config.apiBaseUrl()
                                        + "/v1/eats/orders/"
                                        + id
                                        + "/accept_pos_order"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.external_reference_id").value("rs-uber-1"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        server.expect(requestTo(config.apiBaseUrl() + "/v1/eats/orders/" + id + "/deny_pos_order"))
                .andExpect(jsonPath("$.reason.code").value("CAPACITY"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        var client = new UberEatsOrderClient(config, oauth, builder.build());
        assertThat(client.getOrder(id).path("id").asText()).isEqualTo(id);
        client.accept(id, "rs-uber-1");
        client.deny(id, "CAPACITY");
        server.verify();
    }

    @Test
    void redirectsAndUnexpectedSuccessNeverAcknowledgeDecision() {
        var config = new UberEatsProperties();
        config.enabled = true;
        var oauth = org.mockito.Mockito.mock(UberEatsOAuthClient.class);
        org.mockito.Mockito.when(oauth.accessToken()).thenReturn("fixture-token");
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        String id = "00000000-0000-4000-8000-000000000001";
        server.expect(
                        requestTo(
                                config.apiBaseUrl()
                                        + "/v1/eats/orders/"
                                        + id
                                        + "/accept_pos_order"))
                .andRespond(withStatus(HttpStatus.FOUND));
        server.expect(requestTo(config.apiBaseUrl() + "/v1/eats/orders/" + id + "/deny_pos_order"))
                .andRespond(withStatus(HttpStatus.TEMPORARY_REDIRECT));
        server.expect(
                        requestTo(
                                config.apiBaseUrl()
                                        + "/v1/eats/orders/"
                                        + id
                                        + "/accept_pos_order"))
                .andRespond(withStatus(HttpStatus.OK));
        var client = new UberEatsOrderClient(config, oauth, builder.build());
        assertThatThrownBy(() -> client.accept(id, "rs-uber-1"))
                .isInstanceOf(UberEatsApiException.class)
                .hasMessage("UBER_API_UNAVAILABLE");
        assertThatThrownBy(() -> client.deny(id, "CAPACITY"))
                .isInstanceOf(UberEatsApiException.class);
        assertThatThrownBy(() -> client.accept(id, "rs-uber-1"))
                .isInstanceOf(UberEatsApiException.class);
        server.verify();
    }

    @Test
    void pausedOrderManagerCannotMutateEvenThroughDirectClientCalls() {
        var config = new UberEatsProperties();
        config.enabled = true;
        config.workerEnabled = false;
        var oauth = org.mockito.Mockito.mock(UberEatsOAuthClient.class);
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new UberEatsOrderClient(config, oauth, builder.build());
        String id = "00000000-0000-4000-8000-000000000001";
        assertThatThrownBy(() -> client.accept(id, "fixture-reference"))
                .hasMessage("UBER_PROCESSING_PAUSED");
        assertThatThrownBy(() -> client.deny(id, "CAPACITY"))
                .hasMessage("UBER_PROCESSING_PAUSED");
        org.mockito.Mockito.verifyNoInteractions(oauth);
        server.verify();
    }
}
