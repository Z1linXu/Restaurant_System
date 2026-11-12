package com.restaurant.system.printing;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** A closed opt-in for the existing independent Allô deployment; never enables backend TCP. */
@Component
public class AlloProductionPrintingPolicy {
    public static final String ENABLED_PROPERTY = "app.delivery.allo-production-printing-enabled";
    public static final String STORE_CODE = "ALLO_NOUILLES_TEST";
    private final Environment environment;
    private final PrintingRuntimePolicyProperties runtime;
    private final StoreRepository stores;

    public AlloProductionPrintingPolicy(Environment environment, PrintingRuntimePolicyProperties runtime, StoreRepository stores) {
        this.environment = environment;
        this.runtime = runtime;
        this.stores = stores;
    }

    public static boolean productionPrintingRequested(Environment environment) {
        return environment.getProperty(ENABLED_PROPERTY, Boolean.class, false);
    }

    /** Called before Flyway, retaining the original deployment's exact database identity. */
    public static void validateProductionRuntime(Environment environment, PrintingRuntimePolicyProperties runtime) {
        Set<String> profiles = Set.copyOf(Arrays.asList(environment.getActiveProfiles()));
        String url = environment.getProperty("spring.datasource.url", "");
        boolean originalIdentity = Set.of("allo-test").equals(profiles)
            && Set.of("jdbc:postgresql://db:5432/restaurant_allo_nouilles_test",
                "jdbc:postgresql://127.0.0.1:25442/restaurant_allo_nouilles_test").contains(url);
        boolean isolatedQaIdentity = Set.of("allo-test", "allo-qa").equals(profiles)
            && "jdbc:postgresql://127.0.0.1:26512/restaurant_allo_nouilles_test".equals(url);
        if ((!originalIdentity && !isolatedQaIdentity)
                || !"production".equals(environment.getProperty("app.environment"))
                || !"allo_test".equals(environment.getProperty("spring.datasource.username"))) {
            throw new IllegalStateException("Allo printing requires its exact independent deployment or isolated local QA identity");
        }
        if (!Set.of(PrintingMode.DISABLED, PrintingMode.MOCK, PrintingMode.PAD_DIRECT).equals(Set.copyOf(runtime.getAllowedModes()))
                || !runtime.isEndpointConfigurationEnabled()) {
            throw new IllegalStateException("Allo Production printing permits DISABLED/MOCK/PAD_DIRECT only; backend REAL is forbidden");
        }
        for (String flag : List.of("runtime-enabled", "force-overwrite", "safe-metadata-enabled", "default-users-enabled",
                "demo-data-enabled", "membership-supplement-enabled", "production-bootstrap-enabled")) {
            if (environment.getProperty("app.seed." + flag, Boolean.class, true)) {
                throw new IllegalStateException("Allo Production printing requires all ordinary seeders disabled");
            }
        }
        if (environment.getProperty("app.delivery.allo-bootstrap-enabled", Boolean.class, true)
                || environment.getProperty("app.template.test-bootstrap.enabled", Boolean.class, true)) {
            throw new IllegalStateException("Allo Production printing requires both Test bootstraps disabled");
        }
    }

    public boolean allowsPadDirect(Store store) {
        if (store == null || !STORE_CODE.equals(store.code) || !productionPrintingRequested(environment)) return false;
        try {
            validateProductionRuntime(environment, runtime);
            return true;
        } catch (IllegalStateException invalidRuntime) {
            return false;
        }
    }

    public boolean allowsPadDirect(Long storeId) {
        return stores.findById(storeId).map(this::allowsPadDirect).orElse(false);
    }

    public String effectiveMode(Store store) {
        String mode = runtime.safePersistedModeOrDisabled(store.printing_mode);
        if (templateRuntime() && (PrintingMode.REAL.equals(mode)
                || PrintingMode.PAD_DIRECT.equals(mode) && !allowsPadDirect(store))) return PrintingMode.DISABLED;
        return mode;
    }

    public void requireStoreMode(Store store, String mode) {
        if (templateRuntime() && (PrintingMode.REAL.equals(mode)
                || PrintingMode.PAD_DIRECT.equals(mode) && !allowsPadDirect(store))) {
            throw new BusinessException("ALLO_PAD_DIRECT_RUNTIME_NOT_ALLOWED");
        }
    }

    public void requireEndpointStore(Long storeId, String endpoint) {
        if (templateRuntime() && endpoint != null && !endpoint.isBlank() && !allowsPadDirect(storeId)) {
            throw new BusinessException("ALLO_PAD_DIRECT_RUNTIME_NOT_ALLOWED");
        }
    }

    public boolean padDirectActive(Long storeId) {
        return stores.findById(storeId).map(store -> PrintingMode.PAD_DIRECT.equals(effectiveMode(store))).orElse(false);
    }

    private boolean templateRuntime() {
        List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        return profiles.contains("allo-test") || profiles.contains("template-test");
    }
}
