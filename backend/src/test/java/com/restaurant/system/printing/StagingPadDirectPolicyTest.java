package com.restaurant.system.printing;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.common.exception.BusinessException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class StagingPadDirectPolicyTest {
    @Test
    void reviewedStagingOverlayBindsPadDirectButNeverReal() throws Exception {
        Map<String, Object> overlay = new ObjectMapper().readValue(
            Path.of("../deployment/cloud/staging-pad-direct/backend-environment.json").toFile(),
            new TypeReference<Map<String, Object>>() {});
        assertEquals(1, overlay.size(), "This batch changes only the mode ceiling");
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("staging-overlay", overlay));
        environment.getPropertySources().addLast(new SystemEnvironmentPropertySource("existing-staging", Map.of(
            "APP_PRINTING_ALLOWED_MODES", "DISABLED,MOCK",
            "APP_PRINTING_ENDPOINT_CONFIGURATION_ENABLED", "false")));
        var policy = Binder.get(environment).bind("app.printing", PrintingRuntimePolicyProperties.class).get();
        policy.validate();
        assertEquals(List.of("DISABLED", "MOCK", "PAD_DIRECT"), policy.getAllowedModes());
        for (String mode : List.of("DISABLED", "MOCK", "PAD_DIRECT")) {
            assertEquals(mode, policy.requireAllowedMode(mode));
        }
        assertThrows(BusinessException.class, () -> policy.requireAllowedMode("REAL"));
        assertEquals("DISABLED", policy.safePersistedModeOrDisabled("REAL"));
        assertFalse(policy.isEndpointConfigurationEnabled());
        assertThrows(BusinessException.class, () -> policy.requireEndpointConfigurationAllowed("192.168.1.100"));
    }
}
