package com.restaurant.system.modules.dto;

import com.restaurant.system.modules.StoreHardwareCapabilityReadiness;

public class StoreHardwareCapabilityReadinessResponse {
    public String capability_key;
    public String readiness_state;
    public Boolean required_by_current_runtime;
    public Boolean dependency_satisfied;
    public String layer;
    public String source;
    public String note;

    public static StoreHardwareCapabilityReadinessResponse from(StoreHardwareCapabilityReadiness readiness) {
        StoreHardwareCapabilityReadinessResponse response = new StoreHardwareCapabilityReadinessResponse();
        response.capability_key = readiness.capabilityKey();
        response.readiness_state = readiness.readinessState().name();
        response.required_by_current_runtime = readiness.requiredByCurrentRuntime();
        response.dependency_satisfied = readiness.dependencySatisfied();
        response.layer = readiness.layer();
        response.source = readiness.source();
        response.note = readiness.note();
        return response;
    }
}
