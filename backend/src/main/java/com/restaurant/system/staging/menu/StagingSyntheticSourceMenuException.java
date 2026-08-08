package com.restaurant.system.staging.menu;

public class StagingSyntheticSourceMenuException extends RuntimeException {

    private final String errorCode;

    public StagingSyntheticSourceMenuException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
