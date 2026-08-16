package com.restaurant.system.owner.provisioning;

import java.util.List;

public record PhaseBProvisioningValidationResult(String status, List<String> issues) {

    public boolean blocking() {
        return "BLOCKING".equals(status);
    }
}
