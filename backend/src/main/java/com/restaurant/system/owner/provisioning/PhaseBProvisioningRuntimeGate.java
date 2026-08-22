package com.restaurant.system.owner.provisioning;

import com.restaurant.system.owner.exception.OwnerStoreProvisioningException;
import java.util.Arrays;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PhaseBProvisioningRuntimeGate {

    private final Environment environment;

    public PhaseBProvisioningRuntimeGate(Environment environment) {
        this.environment = environment;
    }

    public void requireEnabled() {
        if (isProductionProfile()) {
            throw forbidden("PHASE_B_PROVISIONING_FORBIDDEN_IN_PRODUCTION");
        }
        if (!isExplicitStagingRuntime()) {
            throw forbidden("PHASE_B_PROVISIONING_STAGING_RUNTIME_REQUIRED");
        }
        if (!environment.getProperty("app.phase-b.provisioning.enabled", Boolean.class, false)) {
            throw forbidden("PHASE_B_PROVISIONING_DISABLED");
        }
    }

    public boolean enabled() {
        return !isProductionProfile()
            && isExplicitStagingRuntime()
            && environment.getProperty("app.phase-b.provisioning.enabled", Boolean.class, false);
    }

    private boolean isProductionProfile() {
        return Arrays.stream(environment.getActiveProfiles())
            .anyMatch(profile -> "prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile));
    }

    private boolean isExplicitStagingRuntime() {
        return "staging".equalsIgnoreCase(environment.getProperty("app.phase-b.runtime", ""))
            && "staging".equalsIgnoreCase(environment.getProperty("app.environment", ""));
    }

    private OwnerStoreProvisioningException forbidden(String code) {
        return new OwnerStoreProvisioningException(
            code,
            HttpStatus.FORBIDDEN,
            "Phase B Store provisioning is not enabled in this runtime"
        );
    }
}
