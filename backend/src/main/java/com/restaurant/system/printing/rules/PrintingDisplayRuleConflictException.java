package com.restaurant.system.printing.rules;

import org.springframework.http.HttpStatus;

public class PrintingDisplayRuleConflictException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;

    public PrintingDisplayRuleConflictException(String errorCode, String message) {
        this(errorCode, message, null);
    }

    public PrintingDisplayRuleConflictException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.status = HttpStatus.CONFLICT;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
