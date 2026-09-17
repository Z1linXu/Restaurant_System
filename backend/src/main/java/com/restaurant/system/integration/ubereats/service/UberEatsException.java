package com.restaurant.system.integration.ubereats.service;

import org.springframework.http.HttpStatus;

public class UberEatsException extends RuntimeException {
    public final HttpStatus status;

    public UberEatsException(HttpStatus status, String code) {
        super(code);
        this.status = status;
    }

    public static UberEatsException conflict(String code) {
        return new UberEatsException(HttpStatus.CONFLICT, code);
    }
}
