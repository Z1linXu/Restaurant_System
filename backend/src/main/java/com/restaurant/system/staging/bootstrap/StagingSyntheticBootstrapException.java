package com.restaurant.system.staging.bootstrap;

public class StagingSyntheticBootstrapException extends RuntimeException {

    private final String errorCode;

    public StagingSyntheticBootstrapException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
