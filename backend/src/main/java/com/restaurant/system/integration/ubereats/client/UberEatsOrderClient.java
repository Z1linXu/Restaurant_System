package com.restaurant.system.integration.ubereats.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurant.system.integration.ubereats.config.UberEatsProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.UUID;

@Component
public class UberEatsOrderClient {
    private final UberEatsProperties properties;
    private final UberEatsOAuthClient oauth;
    private final RestClient http;

    @Autowired
    public UberEatsOrderClient(UberEatsProperties properties, UberEatsOAuthClient oauth) {
        this(properties, oauth, UberEatsOAuthClient.defaultHttp());
    }

    public UberEatsOrderClient(
            UberEatsProperties properties, UberEatsOAuthClient oauth, RestClient http) {
        this.properties = properties;
        this.oauth = oauth;
        this.http = http;
    }

    public JsonNode getOrder(String id) {
        try {
            return http.get()
                    .uri(properties.apiBaseUrl() + "/v2/eats/order/" + safeId(id))
                    .headers(h -> h.setBearerAuth(oauth.accessToken()))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401) oauth.invalidate();
            throw new UberEatsApiException(ex.getStatusCode().value());
        } catch (UberEatsApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new UberEatsApiException(0);
        }
    }

    public void accept(String id, String reference) {
        post(
                id,
                "accept_pos_order",
                Map.of(
                        "reason",
                        "Accepted by restaurant staff",
                        "external_reference_id",
                        reference,
                        "fields_relayed",
                        Map.of(
                                "order_special_instructions",
                                true,
                                "item_special_instructions",
                                true,
                                "item_special_requests",
                                false,
                                "promotions",
                                false)));
    }

    public void deny(String id, String reason) {
        post(
                id,
                "deny_pos_order",
                Map.of(
                        "reason",
                        Map.of("code", reason, "explanation", "Declined by restaurant staff")));
    }

    private void post(String id, String action, Object body) {
        try {
            var response =
                    http.post()
                            .uri(
                                    properties.apiBaseUrl()
                                            + "/v1/eats/orders/"
                                            + safeId(id)
                                            + "/"
                                            + action)
                            .headers(h -> h.setBearerAuth(oauth.accessToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .toBodilessEntity();
            // Redirects, unexpected 2xx and informational responses do not prove the documented
            // ACK.
            if (response.getStatusCode().value() != 204) throw new UberEatsApiException(0);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401) oauth.invalidate();
            throw new UberEatsApiException(ex.getStatusCode().value());
        } catch (UberEatsApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new UberEatsApiException(0);
        }
    }

    private String safeId(String id) {
        return UUID.fromString(id).toString();
    }
}
