package com.restaurant.system.merchant;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.auth.ForbiddenException;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.merchant.dto.MerchantConfigurationResponse;
import com.restaurant.system.merchant.dto.MerchantConfigurationResponse.ResourceReference;
import com.restaurant.system.merchant.dto.MerchantConfigurationSummary;
import com.restaurant.system.owner.exception.OwnerStoreProvisioningException;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantConfigurationService {
    public static final String NEUTRAL_PROFILE = "NEUTRAL_RESTAURANT";
    public static final String LANZHOU_PROFILE = "ST_DENIS_CANONICAL_PROFILE";
    public static final String TAX_POLICY = MenuRevisionService.TAX_POLICY_VERSION;
    private static final Set<String> WRITE_FIELDS = Set.of("schema_version", "expected_config_revision", "brand_name", "locale");
    private final StoreMerchantConfigurationRepository configurations;
    private final StoreRepository stores;
    private final AuthorizationService authorization;
    private final MenuRevisionService menuRevisions;
    private final AuditLogService audit;
    private final String defaultTimezone;
    private final jakarta.persistence.EntityManager entityManager;

    public MerchantConfigurationService(StoreMerchantConfigurationRepository configurations, StoreRepository stores,
        AuthorizationService authorization, MenuRevisionService menuRevisions, AuditLogService audit,
        @Value("${app.daily-close.default-timezone:America/Toronto}") String defaultTimezone,
        jakarta.persistence.EntityManager entityManager) {
        this.configurations = configurations; this.stores = stores; this.authorization = authorization;
        this.menuRevisions = menuRevisions; this.audit = audit; this.defaultTimezone = ZoneId.of(defaultTimezone).getId();
        this.entityManager = entityManager;
    }

    /** Internal compatibility decision. Missing, corrupt and unsupported configurations fail closed. */
    @Transactional(readOnly = true)
    public boolean isLanzhouCompatible(Long storeId) {
        if (storeId == null) return false;
        return configurations.findById(storeId).filter(this::valid)
            .filter(c -> LANZHOU_PROFILE.equals(c.profile_code) && "v2".equals(c.profile_version))
            .filter(c -> "PROVISIONAL_COMPATIBILITY".equals(c.compatibility_mode)).isPresent();
    }

    /** Caller must authorize its Store before exposing this safe projection. No Profile artifact is loaded. */
    @Transactional(readOnly = true)
    public MerchantConfigurationSummary safeSummary(Long storeId) {
        Store store = requireStore(storeId);
        StoreMerchantConfiguration c = configurations.findById(storeId).filter(this::valid).orElseGet(() -> neutral(store));
        return new MerchantConfigurationSummary(1, c.config_revision, c.brand_name, c.locale, "CAD", timezone(store),
            TAX_POLICY, c.profile_code, c.compatibility_mode);
    }

    @Transactional(readOnly = true)
    public MerchantConfigurationResponse get(Long storeId) {
        requireOwner(storeId);
        return response(requireStore(storeId));
    }

    @Transactional
    public MerchantConfigurationResponse update(Long storeId, JsonNode body, HttpServletRequest request) {
        AuthenticatedUser owner = requireOwner(storeId);
        validateWrite(body);
        Store store = stores.findByIdForUpdate(storeId).orElseThrow(() -> new ForbiddenException("Store not available"));
        // A previously authorized OSIV entity may predate a Store move while the lock waited.
        entityManager.refresh(store, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        owner = requireOwner(storeId); // Recheck the refreshed scope after waiting on the Store lock.
        StoreMerchantConfiguration c = configurations.findById(storeId).orElseGet(() -> neutral(store));
        if (!valid(c)) throw conflict("MERCHANT_CONFIGURATION_INVALID", "Stored configuration requires repair");
        if (c.config_revision != body.get("expected_config_revision").longValue())
            throw conflict("MERCHANT_CONFIGURATION_REVISION_CONFLICT", "Configuration changed; reload before saving");
        long oldRevision = c.config_revision;
        c.brand_name = body.get("brand_name").textValue().trim(); c.locale = body.get("locale").textValue();
        c.config_revision += 1; c.updated_by = owner.userId(); c.updated_at = LocalDateTime.now();
        if (oldRevision == 0) c.configuration_source = "OWNER_CONFIGURED_NEUTRAL";
        configurations.saveAndFlush(c);
        audit.record(storeId, owner, "MERCHANT_CONFIGURATION_UPDATED", "MERCHANT_CONFIGURATION", storeId,
            "Merchant configuration updated", Map.of("schema_version", 1, "previous_revision", oldRevision,
                "config_revision", c.config_revision, "profile_code", c.profile_code), request);
        menuRevisions.incrementRevision(storeId);
        return response(store);
    }

    /** Called only in the atomic provisioning transaction, after Profile authorization/validation. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void initializeForProvisionedStore(Store store, Long actorId, String profileCode, String profileVersion) {
        if (configurations.existsById(store.id)) throw conflict("MERCHANT_CONFIGURATION_ALREADY_EXISTS", "Configuration exists");
        StoreMerchantConfiguration c = neutral(store);
        c.config_revision = 1L; c.updated_by = actorId; c.configuration_source = "OWNER_PROVISIONED";
        if (LANZHOU_PROFILE.equals(profileCode) && "v2".equals(profileVersion)) {
            c.profile_code = profileCode; c.profile_version = profileVersion;
            c.compatibility_mode = "PROVISIONAL_COMPATIBILITY";
        } else if (!NEUTRAL_PROFILE.equals(profileCode) || !"v1".equals(profileVersion)) {
            throw invalid("Unsupported runtime Profile");
        }
        configurations.save(c);
    }

    private MerchantConfigurationResponse response(Store store) {
        String source = configurations.findById(store.id).filter(this::valid).map(c -> c.configuration_source).orElse("MISSING_NEUTRAL");
        return new MerchantConfigurationResponse(store.id, store.organization_id, safeSummary(store.id), source,
            List.of(new ResourceReference(1, "MODULES", "/api/v1/stores/" + store.id + "/modules"),
                new ResourceReference(1, "MENU", "/api/v1/menu/catalog?store_id=" + store.id),
                new ResourceReference(1, "STATIONS", "/api/v1/admin/platform/stations?store_id=" + store.id),
                new ResourceReference(1, "TABLES", "/api/v1/admin/platform/dining-tables?store_id=" + store.id),
                new ResourceReference(1, "PRINTING", "/api/v1/admin/printing?store_id=" + store.id),
                new ResourceReference(1, "MENU_OPTIONS", "/api/v1/admin/platform/menu/item-options?store_id=" + store.id),
                new ResourceReference(1, "COMBOS", "/api/v1/admin/menu/combo-configuration?store_id=" + store.id)));
    }

    private AuthenticatedUser requireOwner(Long storeId) {
        AuthenticatedUser actor = authorization.requireForStore(storeId, Capability.ADMIN_STORE_CONFIG);
        if (!authorization.isOwner(actor)) throw new ForbiddenException("Store Owner is required");
        return actor;
    }
    private Store requireStore(Long id) { return stores.findById(id).orElseThrow(() -> new ForbiddenException("Store not available")); }
    private String timezone(Store store) { return store.timezone == null || store.timezone.isBlank() ? defaultTimezone : ZoneId.of(store.timezone).getId(); }
    private StoreMerchantConfiguration neutral(Store store) {
        StoreMerchantConfiguration c = new StoreMerchantConfiguration();
        c.store_id = store.id; c.schema_version = 1; c.config_revision = 0L;
        c.brand_name = store.name == null || store.name.isBlank() ? store.code : store.name;
        c.locale = "en-CA"; c.currency = "CAD"; c.tax_policy_reference = TAX_POLICY;
        c.profile_code = NEUTRAL_PROFILE; c.profile_version = "v1"; c.compatibility_mode = "NEUTRAL";
        c.configuration_source = "MISSING_NEUTRAL"; c.created_at = LocalDateTime.now(); c.updated_at = c.created_at;
        return c;
    }
    private boolean valid(StoreMerchantConfiguration c) {
        if (c == null || !Integer.valueOf(1).equals(c.schema_version) || c.config_revision == null || c.config_revision < 0
            || c.brand_name == null || c.brand_name.isBlank() || c.brand_name.length() > 255
            || !validLocale(c.locale) || !"CAD".equals(c.currency) || !TAX_POLICY.equals(c.tax_policy_reference)) return false;
        return NEUTRAL_PROFILE.equals(c.profile_code) && "v1".equals(c.profile_version) && "NEUTRAL".equals(c.compatibility_mode)
            || LANZHOU_PROFILE.equals(c.profile_code) && "v2".equals(c.profile_version) && "PROVISIONAL_COMPATIBILITY".equals(c.compatibility_mode);
    }
    private void validateWrite(JsonNode body) {
        if (body == null || !body.isObject() || body.size() != WRITE_FIELDS.size()) throw invalid("Complete configuration fields are required");
        body.fieldNames().forEachRemaining(name -> { if (!WRITE_FIELDS.contains(name)) throw invalid("Unknown or read-only configuration field: " + name); });
        if (!body.path("schema_version").isIntegralNumber() || !body.path("schema_version").canConvertToInt() || body.path("schema_version").intValue() != 1
            || !body.path("expected_config_revision").isIntegralNumber() || !body.path("expected_config_revision").canConvertToLong() || body.path("expected_config_revision").longValue() < 0
            || !body.path("brand_name").isTextual() || body.path("brand_name").textValue().trim().isEmpty() || body.path("brand_name").textValue().length() > 255
            || !body.path("locale").isTextual() || !validLocale(body.path("locale").textValue())) throw invalid("Invalid configuration schema or field type");
    }
    private boolean validLocale(String value) {
        try { return value != null && value.length() <= 64 && !new Locale.Builder().setLanguageTag(value).build().getLanguage().isBlank(); }
        catch (RuntimeException invalid) { return false; }
    }
    private OwnerStoreProvisioningException invalid(String message) { return new OwnerStoreProvisioningException("MERCHANT_CONFIGURATION_INVALID", HttpStatus.BAD_REQUEST, message); }
    private OwnerStoreProvisioningException conflict(String code, String message) { return new OwnerStoreProvisioningException(code, HttpStatus.CONFLICT, message); }
}
