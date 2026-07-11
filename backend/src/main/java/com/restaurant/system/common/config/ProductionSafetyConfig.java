package com.restaurant.system.common.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ProductionSafetyConfig implements BeanFactoryPostProcessor, EnvironmentAware, PriorityOrdered {

    private static final int MIN_JWT_SECRET_LENGTH = 32;
    private static final List<String> STRICT_PROFILES = List.of("cloud", "prod", "production");
    private static final List<String> PILOT_PROFILES = List.of("pilot");
    private static final List<String> UNSAFE_DDL_AUTO_VALUES = List.of("update", "create", "create-drop", "create_drop");

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public int getOrder() {
        return PriorityOrdered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        validateEnvironment(environment);
    }

    static void validateEnvironment(Environment environment) {
        if (environment == null) {
            return;
        }

        List<String> activeProfiles = normalizedActiveProfiles(environment);
        boolean strictProductionProfile = activeProfiles.stream().anyMatch(STRICT_PROFILES::contains);
        boolean pilotProfile = activeProfiles.stream().anyMatch(PILOT_PROFILES::contains);

        if (!strictProductionProfile && !pilotProfile) {
            return;
        }

        List<String> violations = new ArrayList<>();
        validateJwtSecret(environment, violations, strictProductionProfile, pilotProfile);

        if (strictProductionProfile) {
            validateStrictProductionSettings(environment, violations);
        } else if (pilotProfile) {
            validatePilotSettings(environment, violations);
        }

        if (!violations.isEmpty()) {
            throw new IllegalStateException("Production safety check failed:\n - " + String.join("\n - ", violations));
        }
    }

    private static void validateJwtSecret(
        Environment environment,
        List<String> violations,
        boolean strictProductionProfile,
        boolean pilotProfile
    ) {
        String jwtSecret = environment.getProperty("app.auth.jwt-secret");
        String normalizedSecret = jwtSecret == null ? "" : jwtSecret.trim();
        String lowerSecret = normalizedSecret.toLowerCase(Locale.ROOT);

        if (!StringUtils.hasText(normalizedSecret)) {
            violations.add("app.auth.jwt-secret must not be empty for cloud/prod/pilot profiles.");
            return;
        }
        if (normalizedSecret.length() < MIN_JWT_SECRET_LENGTH) {
            violations.add("app.auth.jwt-secret must be at least " + MIN_JWT_SECRET_LENGTH
                + " characters for cloud/prod/pilot profiles.");
        }
        if (lowerSecret.contains("replace-this")) {
            violations.add("app.auth.jwt-secret still contains placeholder text 'replace-this'.");
        }
        if ((strictProductionProfile || pilotProfile) && lowerSecret.contains("dev-local")) {
            violations.add("app.auth.jwt-secret must not use the dev-local secret outside local/dev profiles.");
        }
    }

    private static void validateStrictProductionSettings(Environment environment, List<String> violations) {
        if (environment.getProperty("app.auth.x-user-id-fallback-enabled", Boolean.class, false)) {
            violations.add("app.auth.x-user-id-fallback-enabled must be false for cloud/prod profiles.");
        }
        if (environment.getProperty("app.dev-tools.role-switcher-enabled", Boolean.class, false)) {
            violations.add("app.dev-tools.role-switcher-enabled must be false for cloud/prod profiles.");
        }
        if (environment.getProperty("app.seed.force-overwrite", Boolean.class, false)) {
            violations.add("app.seed.force-overwrite must be false for cloud/prod profiles.");
        }
        if (environment.getProperty("app.seed.default-users-enabled", Boolean.class, false)) {
            violations.add("app.seed.default-users-enabled must be false for cloud/prod profiles.");
        }
        if (environment.getProperty("app.seed.demo-data-enabled", Boolean.class, false)) {
            violations.add("app.seed.demo-data-enabled must be false for cloud/prod profiles.");
        }
        if (environment.getProperty("app.seed.membership-supplement-enabled", Boolean.class, false)) {
            violations.add("app.seed.membership-supplement-enabled must be false for cloud/prod profiles.");
        }
        if (environment.getProperty("app.seed.production-bootstrap-enabled", Boolean.class, false)) {
            violations.add("app.seed.production-bootstrap-enabled is reserved for a future explicit bootstrap flow and must be false for cloud/prod profiles.");
        }

        String ddlAuto = normalize(environment.getProperty("spring.jpa.hibernate.ddl-auto"));
        if (UNSAFE_DDL_AUTO_VALUES.contains(ddlAuto)) {
            violations.add("spring.jpa.hibernate.ddl-auto must not be '" + ddlAuto
                + "' for cloud/prod profiles; use validate or none.");
        }

        if (!environment.getProperty("spring.flyway.enabled", Boolean.class, false)) {
            violations.add("spring.flyway.enabled must be true for cloud/prod profiles.");
        }
    }

    private static void validatePilotSettings(Environment environment, List<String> violations) {
        if (environment.getProperty("app.seed.default-users-enabled", Boolean.class, false)) {
            violations.add("app.seed.default-users-enabled must be false for pilot profile.");
        }
        if (environment.getProperty("app.seed.demo-data-enabled", Boolean.class, false)) {
            violations.add("app.seed.demo-data-enabled must be false for pilot profile.");
        }
    }

    private static List<String> normalizedActiveProfiles(Environment environment) {
        return Arrays.stream(environment.getActiveProfiles())
            .map(ProductionSafetyConfig::normalize)
            .filter(StringUtils::hasText)
            .toList();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
