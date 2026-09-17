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
    void verifiedOrderEndpointPathsAndBodies() {
        var config = new UberEatsProperties();
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
}
