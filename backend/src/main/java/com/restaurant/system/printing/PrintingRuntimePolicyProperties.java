package com.restaurant.system.printing;

import com.restaurant.system.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.printing")
public class PrintingRuntimePolicyProperties {

    private static final Set<String> SUPPORTED_MODES = Set.of(
        PrintingMode.REAL,
        PrintingMode.MOCK,
        PrintingMode.DISABLED,
        PrintingMode.PAD_DIRECT
    );

    private List<String> allowedModes = new ArrayList<>(List.of(
        PrintingMode.REAL,
        PrintingMode.MOCK,
        PrintingMode.DISABLED,
        PrintingMode.PAD_DIRECT
    ));
    private boolean endpointConfigurationEnabled = true;

    @PostConstruct
    public void validate() {
        if (allowedModes == null || allowedModes.isEmpty()) {
            throw new IllegalStateException("app.printing.allowed-modes must not be empty");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String mode : allowedModes) {
            String candidate = mode == null ? "" : mode.trim().toUpperCase();
            if (!SUPPORTED_MODES.contains(candidate)) {
                throw new IllegalStateException("Unsupported app.printing.allowed-modes value: " + candidate);
            }
            normalized.add(candidate);
        }
        if (!normalized.contains(PrintingMode.DISABLED)) {
            throw new IllegalStateException("app.printing.allowed-modes must include DISABLED for fail-closed printing behavior");
        }
        allowedModes = new ArrayList<>(normalized);
    }

    public String requireAllowedMode(String mode) {
        String normalized;
        try {
            normalized = PrintingMode.normalizeRequired(mode);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(exception.getMessage());
        }
        if (!allowedModes.contains(normalized)) {
            throw new BusinessException("Printing mode " + normalized + " is not allowed by the runtime policy");
        }
        return normalized;
    }

    public String safePersistedModeOrDisabled(String mode) {
        String normalized = PrintingMode.normalizeOrNull(mode);
        if (normalized == null || !allowedModes.contains(normalized)) {
            return requireAllowedMode(PrintingMode.DISABLED);
        }
        return normalized;
    }

    public void requireEndpointConfigurationAllowed(String endpoint) {
        if (!endpointConfigurationEnabled && endpoint != null && !endpoint.isBlank()) {
            throw new BusinessException("Printer endpoint configuration is disabled by the runtime policy");
        }
    }

    public List<String> getAllowedModes() {
        return allowedModes;
    }

    public void setAllowedModes(List<String> allowedModes) {
        this.allowedModes = allowedModes;
    }

    public boolean isEndpointConfigurationEnabled() {
        return endpointConfigurationEnabled;
    }

    public void setEndpointConfigurationEnabled(boolean endpointConfigurationEnabled) {
        this.endpointConfigurationEnabled = endpointConfigurationEnabled;
    }
}
