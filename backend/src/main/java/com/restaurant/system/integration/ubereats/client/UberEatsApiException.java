package com.restaurant.system.integration.ubereats.client;

/** Never attach HTTP response bodies, request headers, or underlying exceptions. */
public class UberEatsApiException extends RuntimeException {
    public final int status;

    public UberEatsApiException(int status) {
        super("UBER_API_" + (status == 0 ? "UNAVAILABLE" : status));
        this.status = status;
    }
}
