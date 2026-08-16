package com.restaurant.system.owner.exception;

import org.springframework.http.HttpStatus;

public class OwnerStoreProvisioningException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;

    public OwnerStoreProvisioningException(String errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
