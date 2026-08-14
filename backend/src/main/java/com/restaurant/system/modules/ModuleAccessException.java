package com.restaurant.system.modules;

public class ModuleAccessException extends RuntimeException {

    private final String errorCode;
    private final String moduleKey;

    public ModuleAccessException(String errorCode, String moduleKey, String message) {
        super(message);
        this.errorCode = errorCode;
        this.moduleKey = moduleKey;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getModuleKey() {
        return moduleKey;
    }
}
