package com.restaurant.system.integration.ubereats.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurant.system.integration.ubereats.config.UberEatsProperties;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class UberEatsOAuthClient {
    private final UberEatsProperties properties;
    private final RestClient http;
    private final Clock clock;
    private String token;
    private Instant refreshAt = Instant.EPOCH;

    @org.springframework.beans.factory.annotation.Autowired
    public UberEatsOAuthClient(UberEatsProperties properties) {
        this(properties, defaultHttp(), Clock.systemUTC());
    }

    public UberEatsOAuthClient(UberEatsProperties properties, RestClient http, Clock clock) {
        this.properties = properties;
        this.http = http;
        this.clock = clock;
    }

    public static RestClient defaultHttp() {
        var factory =
                new SimpleClientHttpRequestFactory() {
                    @Override
                    protected void prepareConnection(
                            java.net.HttpURLConnection connection, String method)
                            throws java.io.IOException {
                        super.prepareConnection(connection, method);
                        connection.setInstanceFollowRedirects(false);
                    }
                };
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().requestFactory(factory).build();
    }

    public synchronized String accessToken() {
        if (!properties.enabled) throw new UberEatsApiException(503);
        if (token != null && clock.instant().isBefore(refreshAt)) return token;
        var form = new LinkedMultiValueMap<String, String>();
        form.add("client_id", properties.clientId);
        form.add("client_secret", properties.clientSecret());
        form.add("grant_type", "client_credentials");
        form.add("scope", properties.scopes);
        Instant requestedAt = clock.instant();
        try {
            JsonNode response =
                    http.post()
                            .uri(properties.tokenUrl())
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(form)
                            .retrieve()
                            .body(JsonNode.class);
            if (response == null
                    || !response.path("access_token").isTextual()
                    || response.path("access_token").asText().isBlank()
                    || response.path("expires_in").asLong() <= 0)
                throw new UberEatsApiException(502);
            long expires = response.path("expires_in").asLong();
            refreshAt =
                    requestedAt.plusSeconds(
                            Math.max(1, expires - Math.min(60, Math.max(1, expires / 10))));
            token = response.path("access_token").asText();
            return token;
        } catch (RestClientResponseException ex) {
            throw new UberEatsApiException(ex.getStatusCode().value());
        } catch (UberEatsApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new UberEatsApiException(0);
        }
    }

    public synchronized void invalidate() {
        token = null;
        refreshAt = Instant.EPOCH;
    }
}
