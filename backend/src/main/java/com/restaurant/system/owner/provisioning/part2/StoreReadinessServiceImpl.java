package com.restaurant.system.owner.provisioning.part2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.auth.entity.UserCredential;
import com.restaurant.system.auth.repository.UserCredentialRepository;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.modules.ModuleKeys;
import com.restaurant.system.modules.StoreModule;
import com.restaurant.system.modules.StoreModuleRepository;
import com.restaurant.system.owner.master.ChainMasterMenuCatalogService;
import com.restaurant.system.owner.master.ChainMasterMenuVersionEntity;
import com.restaurant.system.owner.profile.StoreProfileArtifactEntity;
import com.restaurant.system.owner.profile.StoreProfileArtifactInput;
import com.restaurant.system.owner.profile.StoreProfileArtifactRepository;
import com.restaurant.system.owner.profile.StoreProfileContractValidator;
import com.restaurant.system.owner.profile.StoreProfileEntity;
import com.restaurant.system.owner.profile.StoreProfileRepository;
import com.restaurant.system.owner.profile.StoreProfileValidationResult;
import com.restaurant.system.owner.profile.StoreProfileVersionEntity;
import com.restaurant.system.owner.profile.StoreProfileVersionRepository;
import com.restaurant.system.owner.provisioning.StoreMenuMasterMappingRepository;
import com.restaurant.system.printing.entity.StoreDevice;
import com.restaurant.system.printing.repository.StoreDeviceRepository;
import com.restaurant.system.station.entity.DiningTable;
import com.restaurant.system.station.entity.Station;
import com.restaurant.system.station.repository.DiningTableRepository;
import com.restaurant.system.station.repository.StationRepository;
import com.restaurant.system.user.entity.OrganizationMembership;
import com.restaurant.system.user.entity.Role;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.entity.StoreMembership;
import com.restaurant.system.user.entity.User;
import com.restaurant.system.user.repository.OrganizationMembershipRepository;
import com.restaurant.system.user.repository.RoleRepository;
import com.restaurant.system.user.repository.StoreMembershipRepository;
import com.restaurant.system.user.repository.StoreRepository;
import com.restaurant.system.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreReadinessServiceImpl implements StoreReadinessService {

    private static final int READINESS_TTL_MINUTES = 15;
    private static final String PASS = "PASS";
    private static final String FAIL = "FAIL";

    private final StoreRepository storeRepository;
    private final StoreProfileRepository profileRepository;
    private final StoreProfileVersionRepository profileVersionRepository;
    private final StoreProfileArtifactRepository artifactRepository;
    private final StoreProfileContractValidator profileValidator;
    private final ChainMasterMenuCatalogService masterMenuCatalogService;
    private final StoreMenuMasterMappingRepository mappingRepository;
    private final StoreModuleRepository storeModuleRepository;
    private final MenuCategoryRepository menuCategoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final StationRepository stationRepository;
    private final DiningTableRepository diningTableRepository;
    private final UserRepository userRepository;
    private final UserCredentialRepository credentialRepository;
    private final OrganizationMembershipRepository organizationMembershipRepository;
    private final StoreMembershipRepository storeMembershipRepository;
    private final RoleRepository roleRepository;
    private final StoreLogicalPrinterRoleRepository printerRoleRepository;
    private final StoreDeviceRepository storeDeviceRepository;
    private final StoreDeviceReadinessRepository deviceReadinessRepository;
    private final StoreReadinessEvidenceRepository evidenceRepository;
    private final ObjectMapper objectMapper;

    public StoreReadinessServiceImpl(
        StoreRepository storeRepository,
        StoreProfileRepository profileRepository,
        StoreProfileVersionRepository profileVersionRepository,
        StoreProfileArtifactRepository artifactRepository,
        StoreProfileContractValidator profileValidator,
        ChainMasterMenuCatalogService masterMenuCatalogService,
        StoreMenuMasterMappingRepository mappingRepository,
        StoreModuleRepository storeModuleRepository,
        MenuCategoryRepository menuCategoryRepository,
        MenuItemRepository menuItemRepository,
        StationRepository stationRepository,
        DiningTableRepository diningTableRepository,
        UserRepository userRepository,
        UserCredentialRepository credentialRepository,
        OrganizationMembershipRepository organizationMembershipRepository,
        StoreMembershipRepository storeMembershipRepository,
        RoleRepository roleRepository,
        StoreLogicalPrinterRoleRepository printerRoleRepository,
        StoreDeviceRepository storeDeviceRepository,
        StoreDeviceReadinessRepository deviceReadinessRepository,
        StoreReadinessEvidenceRepository evidenceRepository,
        ObjectMapper objectMapper
    ) {
        this.storeRepository = storeRepository;
        this.profileRepository = profileRepository;
        this.profileVersionRepository = profileVersionRepository;
        this.artifactRepository = artifactRepository;
        this.profileValidator = profileValidator;
        this.masterMenuCatalogService = masterMenuCatalogService;
        this.mappingRepository = mappingRepository;
        this.storeModuleRepository = storeModuleRepository;
        this.menuCategoryRepository = menuCategoryRepository;
        this.menuItemRepository = menuItemRepository;
        this.stationRepository = stationRepository;
        this.diningTableRepository = diningTableRepository;
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.organizationMembershipRepository = organizationMembershipRepository;
        this.storeMembershipRepository = storeMembershipRepository;
        this.roleRepository = roleRepository;
        this.printerRoleRepository = printerRoleRepository;
        this.storeDeviceRepository = storeDeviceRepository;
        this.deviceReadinessRepository = deviceReadinessRepository;
        this.evidenceRepository = evidenceRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public StoreReadinessResponse evaluate(Long organizationId, Long storeId) {
        Store store = storeRepository.findById(storeId)
            .orElseThrow(() -> new BusinessException("PART2_STORE_NOT_FOUND"));
        requireScope(organizationId, store);
        LocalDateTime now = LocalDateTime.now();
        List<StoreReadinessResponse.Check> checks = new ArrayList<>();

        checkStoreBoundary(store, checks);
        ChainMasterMenuVersionEntity masterVersion = checkProfileAndMaster(store, checks);
        checkMenu(store, masterVersion, checks);
        checkModules(store, checks);
        checkStationsAndTables(store, checks);
        checkStaff(store, checks);
        checkPrinting(store, checks);
        checkDevices(store, now, checks);
        checkStructuralSmoke(store, checks);

        boolean ready = checks.stream().allMatch(check -> PASS.equals(check.status));
        String status = ready ? "READY" : "NOT_READY";
        String evidenceJson = evidenceJson(status, checks);
        String fingerprint = com.restaurant.system.owner.profile.StoreProfileCanonicalJson.sha256Canonical(evidenceJson);
        StoreReadinessEvidenceEntity evidence = evidenceRepository.findByStoreIdForUpdate(store.id)
            .orElseGet(StoreReadinessEvidenceEntity::new);
        evidence.organization_id = store.organization_id;
        evidence.store_id = store.id;
        evidence.status = status;
        evidence.readiness_fingerprint = fingerprint;
        evidence.evidence_json = evidenceJson;
        evidence.checked_at = now;
        evidence.expires_at = now.plusMinutes(READINESS_TTL_MINUTES);
        evidence.created_at = evidence.created_at == null ? now : evidence.created_at;
        evidence.updated_at = now;
        StoreReadinessEvidenceEntity saved = evidenceRepository.save(evidence);
        return toResponse(store, saved, checks, ready);
    }

    private void checkStoreBoundary(Store store, List<StoreReadinessResponse.Check> checks) {
        boolean part2 = "PHASE_B_OWNER_PROVISIONING".equalsIgnoreCase(store.provisioning_source)
            && "VALIDATION_FIXTURE".equalsIgnoreCase(store.store_kind);
        if (!part2) {
            checks.add(check("STORE_BOUNDARY", FAIL, "Only a Phase B validation fixture may be evaluated"));
            return;
        }
        boolean safeState = "inactive".equalsIgnoreCase(store.status)
            && ("READY_FOR_REVIEW".equalsIgnoreCase(store.lifecycle_status)
                || "CONFIGURING".equalsIgnoreCase(store.lifecycle_status));
        boolean alreadyLive = "active".equalsIgnoreCase(store.status)
            && "ACTIVE".equalsIgnoreCase(store.lifecycle_status);
        checks.add(check("STORE_BOUNDARY", safeState || alreadyLive ? PASS : FAIL,
            safeState || alreadyLive ? "Store is inside the Part 2 lifecycle boundary" : "Store must remain inactive until activation"));
    }

    private ChainMasterMenuVersionEntity checkProfileAndMaster(
        Store store,
        List<StoreReadinessResponse.Check> checks
    ) {
        try {
            if (blank(store.provisioned_profile_code)
                || blank(store.provisioned_profile_version)
                || blank(store.provisioned_profile_fingerprint_sha256)
                || blank(store.provisioned_master_menu_key)
                || blank(store.provisioned_master_menu_version)
                || blank(store.provisioned_master_menu_fingerprint_sha256)) {
                checks.add(check("PROFILE_FINGERPRINT", FAIL, "Store Profile and Master Menu provenance is incomplete"));
                return null;
            }
            StoreProfileEntity profile = profileRepository.findByProfileCode(store.provisioned_profile_code)
                .orElseThrow(() -> new BusinessException("STORE_PROFILE_NOT_FOUND"));
            StoreProfileVersionEntity version = profileVersionRepository
                .findByProfileIdAndProfileVersion(profile.id, store.provisioned_profile_version)
                .orElseThrow(() -> new BusinessException("STORE_PROFILE_VERSION_NOT_FOUND"));
            List<StoreProfileArtifactInput> artifacts = artifactRepository
                .findAllByProfileVersionIdOrderByArtifactTypeAscArtifactCodeAsc(version.id)
                .stream()
                .map(this::artifactInput)
                .toList();
            StoreProfileValidationResult profileValidation = profileValidator.validate(
                profile.profile_code,
                version.profile_version,
                version.schema_version,
                version.content_json,
                version.fingerprint_sha256,
                artifacts
            );
            ChainMasterMenuVersionEntity master = masterMenuCatalogService.requirePublishedVersion(
                store.organization_id,
                store.provisioned_master_menu_key,
                store.provisioned_master_menu_version,
                store.provisioned_master_menu_fingerprint_sha256
            );
            boolean valid = List.of("READY", "REVIEWED", "PUBLISHED").contains(version.status)
                && version.fingerprint_sha256.equals(store.provisioned_profile_fingerprint_sha256)
                && profileValidation.valid();
            checks.add(check("PROFILE_FINGERPRINT", valid ? PASS : FAIL,
                valid ? "Profile and Master fingerprints revalidated" : "Profile or Master fingerprint validation failed"));
            return master;
        } catch (RuntimeException exception) {
            checks.add(check("PROFILE_FINGERPRINT", FAIL, "Profile or Master provenance could not be revalidated"));
            return null;
        }
    }

    private void checkMenu(Store store, ChainMasterMenuVersionEntity master, List<StoreReadinessResponse.Check> checks) {
        long categories = menuCategoryRepository.countAllByStoreId(store.id);
        long items = menuItemRepository.countAllByStoreId(store.id);
        long mappings = master == null ? 0 : mappingRepository.findAllByStoreAndMasterVersion(store.id, master.id).size();
        boolean valid = categories > 0 && items > 0 && mappings > 0;
        checks.add(check("MENU_STORE_LOCAL", valid ? PASS : FAIL,
            valid ? "Store-owned menu and Master mappings are present" : "Store-local menu or Master mappings are incomplete"));
    }

    private void checkModules(Store store, List<StoreReadinessResponse.Check> checks) {
        Map<String, StoreModule> modules = new LinkedHashMap<>();
        for (StoreModule module : storeModuleRepository.findAllByStoreIdOrderByIdAsc(store.id)) {
            modules.put(module.module_key, module);
        }
        boolean valid = Set.of(
            ModuleKeys.ORDERING_POS,
            ModuleKeys.MENU,
            ModuleKeys.TABLE_MANAGEMENT,
            ModuleKeys.STAFF_ACCESS,
            ModuleKeys.PRINTING
        ).stream().allMatch(key -> {
            StoreModule module = modules.get(key);
            return module != null && Boolean.TRUE.equals(module.enabled);
        });
        checks.add(check("MODULES", valid ? PASS : FAIL,
            valid ? "Required Part 2 modules are enabled" : "A required Part 2 module is missing or disabled"));
    }

    private void checkStationsAndTables(Store store, List<StoreReadinessResponse.Check> checks) {
        List<Station> stations = stationRepository.findActiveStationsByStoreId(store.id);
        List<DiningTable> tables = diningTableRepository.findAllByStoreIdOrderBySortOrderAscIdAsc(store.id)
            .stream().filter(table -> Boolean.TRUE.equals(table.is_active)).toList();
        checks.add(check("STATIONS_TABLES", !stations.isEmpty() && !tables.isEmpty() ? PASS : FAIL,
            !stations.isEmpty() && !tables.isEmpty()
                ? "Store-local stations and tables are ready"
                : "At least one active station and table are required"));
    }

    private void checkStaff(Store store, List<StoreReadinessResponse.Check> checks) {
        Map<String, Boolean> roleReady = new LinkedHashMap<>();
        roleReady.put("MANAGER", false);
        roleReady.put("FRONTDESK", false);
        for (User user : userRepository.findAllByStore_id(store.id)) {
            String role = roleCode(user.getRole_id());
            if (!roleReady.containsKey(role) || !"active".equalsIgnoreCase(user.getStatus())) {
                continue;
            }
            UserCredential credential = user.getId() == null
                ? null
                : credentialRepository.findFirstByUserIdAndIsActiveTrue(user.getId()).orElse(null);
            StoreMembership storeMembership = user.getId() == null
                ? null
                : storeMembershipRepository.findFirstByUserIdAndStoreId(user.getId(), store.id).orElse(null);
            OrganizationMembership organizationMembership = user.getId() == null
                ? null
                : organizationMembershipRepository.findFirstByUserIdAndOrganizationId(user.getId(), store.organization_id).orElse(null);
            boolean valid = credential != null
                && "BCRYPT".equalsIgnoreCase(credential.passwordAlgorithm)
                && user.getUsername() != null
                && user.getUsername().equalsIgnoreCase(credential.loginIdentifier)
                && storeMembership != null
                && Boolean.TRUE.equals(storeMembership.isActive)
                && store.organization_id.equals(storeMembership.organizationId)
                && Objects.equals(user.getRole_id(), storeMembership.roleId)
                && role.equalsIgnoreCase(storeMembership.roleCode)
                && organizationMembership != null
                && Boolean.TRUE.equals(organizationMembership.isActive)
                && store.organization_id.equals(organizationMembership.organizationId)
                && Objects.equals(user.getRole_id(), organizationMembership.roleId)
                && role.equalsIgnoreCase(organizationMembership.roleCode);
            roleReady.put(role, roleReady.get(role) || valid);
        }
        boolean valid = roleReady.values().stream().allMatch(Boolean.TRUE::equals);
        checks.add(check("STAFF_ACCESS", valid ? PASS : FAIL,
            valid ? "Synthetic Manager and Frontdesk access are Store/Organization scoped" : "Required Store-local staff access is incomplete"));
    }

    private void checkPrinting(Store store, List<StoreReadinessResponse.Check> checks) {
        List<StoreLogicalPrinterRoleEntity> roles = printerRoleRepository.findAllByStoreIdOrderByRoleCodeAsc(store.id);
        boolean scopedRoles = roles.stream().allMatch(role -> store.organization_id.equals(role.organization_id));
        boolean requiredRoles = roles.stream().anyMatch(role -> "GRAB".equals(role.module_code)
                && Boolean.TRUE.equals(role.required)
                && Boolean.TRUE.equals(role.enabled))
            && roles.stream().anyMatch(role -> "FRONTDESK_RECEIPT".equals(role.module_code)
                && Boolean.TRUE.equals(role.required)
                && Boolean.TRUE.equals(role.enabled));
        boolean safeRoles = !roles.isEmpty() && roles.stream().allMatch(role ->
            ("DISABLED".equalsIgnoreCase(role.mode) || "MOCK".equalsIgnoreCase(role.mode))
                && "UNBOUND".equalsIgnoreCase(role.physical_binding_status)
                && role.assigned_printer_id == null
        );
        boolean safeStoreMode = "DISABLED".equalsIgnoreCase(store.printing_mode)
            || "MOCK".equalsIgnoreCase(store.printing_mode);
        checks.add(check("PRINTING_TOPOLOGY", scopedRoles && requiredRoles && safeRoles && safeStoreMode ? PASS : FAIL,
            scopedRoles && requiredRoles && safeRoles && safeStoreMode
                ? "Logical printing roles are Store-scoped and endpoint-free"
                : "Printing topology is incomplete or has an unsafe physical binding"));
        boolean hotKitchenExcluded = roles.stream().noneMatch(role -> "HOT_KITCHEN".equals(role.module_code) && Boolean.TRUE.equals(role.enabled));
        checks.add(check("PRINTING_MODULE_EXCLUSION", hotKitchenExcluded ? PASS : FAIL,
            hotKitchenExcluded ? "HOT_KITCHEN remains excluded from synthetic topology" : "Excluded printing module must remain disabled"));
    }

    private void checkDevices(Store store, LocalDateTime now, List<StoreReadinessResponse.Check> checks) {
        boolean valid = deviceReadinessRepository.findAllByStoreIdOrderByDeviceIdAsc(store.id).stream().anyMatch(readiness -> {
            StoreDevice device = storeDeviceRepository.findByIdAndStoreId(readiness.device_id, store.id).orElse(null);
            return device != null
                && store.organization_id.equals(device.organizationId)
                && store.organization_id.equals(readiness.organization_id)
                && Boolean.TRUE.equals(device.isActive)
                && "ACTIVE".equalsIgnoreCase(device.status)
                && Boolean.TRUE.equals(readiness.trusted_build)
                && "HEALTHY".equalsIgnoreCase(readiness.worker_status)
                && "PASS".equalsIgnoreCase(readiness.proof_status)
                && readiness.expires_at != null
                && readiness.expires_at.isAfter(now)
                && readiness.last_heartbeat_at != null
                && readiness.last_heartbeat_at.isAfter(now.minusMinutes(READINESS_TTL_MINUTES));
        });
        checks.add(check("DEVICE_READINESS", valid ? PASS : FAIL,
            valid ? "Synthetic device proof, heartbeat and Worker status are fresh" : "A fresh trusted synthetic device proof is required"));
    }

    private void checkStructuralSmoke(Store store, List<StoreReadinessResponse.Check> checks) {
        boolean valid = menuItemRepository.findActiveByStoreId(store.id).stream().findFirst().isPresent()
            && !stationRepository.findActiveStationsByStoreId(store.id).isEmpty()
            && !diningTableRepository.findAllByStoreIdOrderBySortOrderAscIdAsc(store.id).isEmpty();
        checks.add(check("STRUCTURAL_SMOKE", valid ? PASS : FAIL,
            valid ? "Ordering/menu/station/table structural smoke passed" : "Ordering structural smoke prerequisites are missing"));
    }

    private String roleCode(Long roleId) {
        if (roleId == null) {
            return "";
        }
        return roleRepository.findById(roleId).map(Role::getCode).orElse("");
    }

    private StoreProfileArtifactInput artifactInput(StoreProfileArtifactEntity artifact) {
        return new StoreProfileArtifactInput(
            artifact.artifact_type,
            artifact.artifact_code,
            artifact.artifact_version,
            artifact.content_json,
            artifact.fingerprint_sha256
        );
    }

    private StoreReadinessResponse.Check check(String code, String status, String message) {
        return new StoreReadinessResponse.Check(code, status, message);
    }

    private String evidenceJson(String status, List<StoreReadinessResponse.Check> checks) {
        try {
            return objectMapper.writeValueAsString(Map.of("status", status, "checks", checks));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Readiness evidence cannot be serialized", exception);
        }
    }

    private StoreReadinessResponse toResponse(
        Store store,
        StoreReadinessEvidenceEntity evidence,
        List<StoreReadinessResponse.Check> checks,
        boolean ready
    ) {
        StoreReadinessResponse response = new StoreReadinessResponse();
        response.evidence_id = evidence.id;
        response.organization_id = store.organization_id;
        response.store_id = store.id;
        response.readiness_status = evidence.status;
        response.ready = ready;
        response.store_status = store.status;
        response.lifecycle_status = store.lifecycle_status;
        response.readiness_fingerprint = evidence.readiness_fingerprint;
        response.checked_at = evidence.checked_at;
        response.expires_at = evidence.expires_at;
        response.checks = List.copyOf(checks);
        response.counts = counts(store);
        return response;
    }

    private StoreReadinessResponse.Counts counts(Store store) {
        StoreReadinessResponse.Counts counts = new StoreReadinessResponse.Counts();
        counts.station_count = (int) stationRepository.countAllByStoreId(store.id);
        counts.table_count = diningTableRepository.findAllByStoreIdOrderBySortOrderAscIdAsc(store.id).size();
        counts.staff_count = userRepository.findAllByStore_id(store.id).size();
        counts.printer_role_count = printerRoleRepository.findAllByStoreIdOrderByRoleCodeAsc(store.id).size();
        counts.device_count = storeDeviceRepository.findAllByStoreIdOrderByIdAsc(store.id).size();
        return counts;
    }

    private void requireScope(Long organizationId, Store store) {
        if (organizationId == null || !organizationId.equals(store.organization_id)) {
            throw new BusinessException("PART2_STORE_ORGANIZATION_MISMATCH");
        }
        if (!"PHASE_B_OWNER_PROVISIONING".equalsIgnoreCase(store.provisioning_source)
            || !"VALIDATION_FIXTURE".equalsIgnoreCase(store.store_kind)) {
            throw new BusinessException("PART2_ONLY_VALIDATION_FIXTURE_ALLOWED");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
