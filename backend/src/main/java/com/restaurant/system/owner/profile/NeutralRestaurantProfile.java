package com.restaurant.system.owner.profile;

import java.util.List;

/** Explicit reviewed empty preset. It has no Master Menu, artifact, hardware or customer data. */
public final class NeutralRestaurantProfile {
    public static final String CODE = "NEUTRAL_RESTAURANT";
    public static final String VERSION = "v1";
    public static final String SCHEMA = "STORE_PROFILE_CONTRACT_V1";
    public static final String CONTENT = "{\"menu_template\":{\"categories\":[]},\"module_defaults\":{\"modules\":[{\"enabled\":true,\"module_key\":\"ORDERING_POS\"},{\"enabled\":true,\"module_key\":\"MENU\"},{\"enabled\":true,\"module_key\":\"MENU_MANAGEMENT\"},{\"enabled\":true,\"module_key\":\"TABLE_MANAGEMENT\"},{\"enabled\":true,\"module_key\":\"PRINTING\"},{\"enabled\":true,\"module_key\":\"ORDER_HISTORY\"},{\"enabled\":true,\"module_key\":\"REPORTING_CORE\"},{\"enabled\":true,\"module_key\":\"STAFF_ACCESS\"},{\"enabled\":true,\"module_key\":\"STORE_ADMINISTRATION\"},{\"enabled\":false,\"module_key\":\"KDS\"},{\"enabled\":false,\"module_key\":\"ANALYTICS_ADVANCED\"},{\"enabled\":false,\"module_key\":\"UBER_EATS\"}]},\"profile_code\":\"NEUTRAL_RESTAURANT\",\"profile_version\":\"v1\",\"schema_version\":\"STORE_PROFILE_CONTRACT_V1\"}";
    public static final String FINGERPRINT = "3e696169e5609a9c8b7708bf9e5bb77a995a6539fc15155665f60f17095648ba";
    private NeutralRestaurantProfile() { }
    public static StoreProfileValidationResult validate(String version, String schema, String content,
        String expected, List<StoreProfileArtifactInput> artifacts) {
        boolean valid;
        try {
            valid = VERSION.equals(version) && SCHEMA.equals(schema) && FINGERPRINT.equals(expected)
                && CONTENT.equals(StoreProfileCanonicalJson.canonicalize(content))
                && (artifacts == null || artifacts.isEmpty());
        } catch (RuntimeException invalid) { valid = false; }
        return new StoreProfileValidationResult(valid, FINGERPRINT, valid ? List.of()
            : List.of(new StoreProfileValidationIssue("NEUTRAL_PROFILE_INVALID", "$", "Neutral preset must be the reviewed empty contract")));
    }
}
