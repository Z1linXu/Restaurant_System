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
import com.restaurant.system.printing.repository.PrinterAssignmentRepository;
import com.restaurant.system.printing.repository.PrinterConfigRepository;
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
    private final StoreReadinessEvidenceHistoryRepository evidenceHistoryRepository;
    private final PrinterConfigRepository printerConfigRepository;
    private final PrinterAssignmentRepository printerAssignmentRepository;
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
        StoreReadinessEvidenceHistoryRepository evidenceHistoryRepository,
        PrinterConfigRepository printerConfigRepository,
        PrinterAssignmentRepository printerAssignmentRepository,
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
        this.evidenceHistoryRepository = evidenceHistoryRepository;
        this.printerConfigRepository = printerConfigRepository;
        this.printerAssignmentRepository = printerAssignmentRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public StoreReadinessResponse evaluate(Long organizationId, Long storeId) {
        return evaluate(organizationId, storeId, null, ReadinessPolicy.SYNTHETIC_ACCEPTANCE);
    }

    @Override
    @Transactional
    public StoreReadinessResponse evaluateOperationalBaseline(
        Long organizationId,
        Long storeId,
        Long ownerUserId
    ) {
        if (ownerUserId == null) {
            throw new BusinessException("STORE_BASELINE_OWNER_REQUIRED");
        }
        return evaluate(organizationId, storeId, ownerUserId, ReadinessPolicy.OPERATIONAL_BASELINE);
    }

    private StoreReadinessResponse evaluate(
        Long organizationId,
        Long storeId,
        Long ownerUserId,
        ReadinessPolicy policy
    ) {
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
        if (policy == ReadinessPolicy.OPERATIONAL_BASELINE) {
            checkOwnerAccess(store, ownerUserId, checks);
        } else {
            checkStaff(store, checks);
        }
        checkPrinting(store, checks);
        if (policy == ReadinessPolicy.OPERATIONAL_BASELINE) {
            checks.add(check("DEVICE_READINESS", PASS, "No device is required for Store activation"));
        } else {
            checkDevices(store, now, checks);
        }
        checkStructuralSmoke(store, checks);

        boolean ready = checks.stream().allMatch(check -> PASS.equals(check.status));
        String status = ready ? "READY" : "NOT_READY";
        String evidenceJson = evidenceJson(status, checks, resourceSnapshot(store, masterVersion));
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
        StoreReadinessEvidenceHistoryEntity history = new StoreReadinessEvidenceHistoryEntity();
        history.organization_id = store.organization_id;
        history.store_id = store.id;
        history.status = status;
        history.readiness_fingerprint = fingerprint;
        history.evidence_json = evidenceJson;
        history.checked_at = now;
        history.expires_at = now.plusMinutes(READINESS_TTL_MINUTES);
        history.created_at = now;
        StoreReadinessEvidenceHistoryEntity savedHistory = evidenceHistoryRepository.save(history);
        return toResponse(store, saved, savedHistory.id, checks, ready);
    }

    private void checkOwnerAccess(Store store, Long ownerUserId, List<StoreReadinessResponse.Check> checks) {
        StoreMembership storeMembership = storeMembershipRepository
            .findFirstByUserIdAndStoreId(ownerUserId, store.id)
            .orElse(null);
        OrganizationMembership organizationMembership = organizationMembershipRepository
            .findFirstByUserIdAndOrganizationId(ownerUserId, store.organization_id)
            .orElse(null);
        boolean valid = storeMembership != null
            && Boolean.TRUE.equals(storeMembership.isActive)
            && store.organization_id.equals(storeMembership.organizationId)
            && "OWNER".equalsIgnoreCase(storeMembership.roleCode)
            && organizationMembership != null
            && Boolean.TRUE.equals(organizationMembership.isActive)
            && store.organization_id.equals(organizationMembership.organizationId)
            && "OWNER".equalsIgnoreCase(organizationMembership.roleCode);
        checks.add(check("STAFF_ACCESS", valid ? PASS : FAIL,
            valid ? "Owner access is Organization/Store scoped" : "Owner Store access is incomplete"));
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
        boolean noPhysicalBindings = printerConfigRepository.findAllByStoreIdOrderByIdAsc(store.id).isEmpty()
            && printerAssignmentRepository.findAllByStoreIdOrderByIdAsc(store.id).isEmpty();
        boolean printingReady = scopedRoles && requiredRoles && safeRoles && safeStoreMode && noPhysicalBindings;
        checks.add(check("PRINTING_TOPOLOGY", printingReady ? PASS : FAIL,
            printingReady
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
                && device.lastHeartbeatAt != null
                && readiness.last_heartbeat_at.equals(device.lastHeartbeatAt)
                && device.lastHeartbeatAt.isAfter(now.minusMinutes(READINESS_TTL_MINUTES));
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

    private String evidenceJson(String status, List<StoreReadinessResponse.Check> checks, Object snapshot) {
        try {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("status", status);
            evidence.put("checks", checks);
            evidence.put("resource_snapshot", snapshot);
            return objectMapper.writeValueAsString(evidence);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Readiness evidence cannot be serialized", exception);
        }
    }

    private Map<String, Object> resourceSnapshot(Store store, ChainMasterMenuVersionEntity masterVersion) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("store", Map.of(
            "organization_id", store.organization_id,
            "store_id", store.id,
            "status", value(store.status),
            "lifecycle_status", value(store.lifecycle_status),
            "printing_mode", value(store.printing_mode),
            "printing_enabled", Boolean.TRUE.equals(store.printing_enabled),
            "profile_fingerprint", value(store.provisioned_profile_fingerprint_sha256),
            "master_fingerprint", value(store.provisioned_master_menu_fingerprint_sha256)
        ));
        snapshot.put("modules", storeModuleRepository.findAllByStoreIdOrderByIdAsc(store.id).stream()
            .map(module -> Map.of(
                "id", module.id,
                "module_key", value(module.module_key),
                "enabled", Boolean.TRUE.equals(module.enabled),
                "configuration_status", value(module.configuration_status),
                "profile_version", value(module.profile_version)
            )).toList());
        snapshot.put("categories", menuCategoryRepository.findAllByStoreIdOrderByIdAsc(store.id).stream()
            .map(category -> Map.of(
                "id", category.id,
                "code", value(category.code),
                "sort_order", category.sort_order == null ? 0 : category.sort_order,
                "is_active", Boolean.TRUE.equals(category.is_active),
                "updated_at", value(category.updated_at)
            )).toList());
        snapshot.put("items", menuItemRepository.findAllByStoreIdOrderByIdAsc(store.id).stream()
            .map(item -> Map.of(
                "id", item.id,
                "category_id", item.category_id == null ? 0L : item.category_id,
                "station_id", item.station_id == null ? 0L : item.station_id,
                "sku", value(item.sku),
                "base_price", value(item.base_price),
                "is_active", Boolean.TRUE.equals(item.is_active),
                "is_sold_out", Boolean.TRUE.equals(item.is_sold_out),
                "sort_order", item.sort_order == null ? 0 : item.sort_order,
                "updated_at", value(item.updated_at)
            )).toList());
        snapshot.put("mappings", masterVersion == null ? List.of() : mappingRepository
            .findAllByStoreAndMasterVersion(store.id, masterVersion.id).stream()
            .map(mapping -> Map.of(
                "id", mapping.id,
                "master_menu_version_id", mapping.master_menu_version_id,
                "entity_type", value(mapping.entity_type),
                "local_entity_id", mapping.local_entity_id,
                "mapping_status", value(mapping.mapping_status)
            )).toList());
        snapshot.put("stations", stationRepository.findAllByStoreIdOrderByIdAsc(store.id).stream()
            .map(station -> Map.of(
                "id", station.id,
                "code", value(station.code),
                "station_type", value(station.station_type),
                "sort_order", station.sort_order == null ? 0 : station.sort_order,
                "is_active", Boolean.TRUE.equals(station.is_active),
                "updated_at", value(station.updated_at)
            )).toList());
        snapshot.put("tables", diningTableRepository.findAllByStoreIdOrderBySortOrderAscIdAsc(store.id).stream()
            .map(table -> Map.of(
                "id", table.id,
                "table_code", value(table.table_code),
                "capacity", table.capacity == null ? 0 : table.capacity,
                "supports_split", Boolean.TRUE.equals(table.supports_split),
                "is_active", Boolean.TRUE.equals(table.is_active),
                "updated_at", value(table.updated_at)
            )).toList());
        snapshot.put("staff", userRepository.findAllByStore_id(store.id).stream()
            .map(user -> staffSnapshot(user, store)).toList());
        snapshot.put("printer_roles", printerRoleRepository.findAllByStoreIdOrderByRoleCodeAsc(store.id).stream()
            .map(role -> Map.of(
                "id", role.id,
                "role_code", value(role.role_code),
                "module_code", value(role.module_code),
                "mode", value(role.mode),
                "enabled", Boolean.TRUE.equals(role.enabled),
                "required", Boolean.TRUE.equals(role.required),
                "physical_binding_status", value(role.physical_binding_status),
                "assigned_printer_id", role.assigned_printer_id == null ? 0L : role.assigned_printer_id
            )).toList());
        snapshot.put("physical_printing", Map.of(
            "printer_config_count", printerConfigRepository.findAllByStoreIdOrderByIdAsc(store.id).size(),
            "printer_assignment_count", printerAssignmentRepository.findAllByStoreIdOrderByIdAsc(store.id).size()
        ));
        snapshot.put("devices", deviceReadinessRepository.findAllByStoreIdOrderByDeviceIdAsc(store.id).stream()
            .map(readiness -> {
                StoreDevice device = storeDeviceRepository.findByIdAndStoreId(readiness.device_id, store.id).orElse(null);
                return Map.of(
                    "readiness_id", readiness.id,
                    "device_id", readiness.device_id,
                    "device_status", device == null ? "MISSING" : value(device.status),
                    "device_active", device != null && Boolean.TRUE.equals(device.isActive),
                    "last_heartbeat_at", value(readiness.last_heartbeat_at),
                    "trusted_build", Boolean.TRUE.equals(readiness.trusted_build),
                    "worker_status", value(readiness.worker_status),
                    "proof_status", value(readiness.proof_status),
                    "expires_at", value(readiness.expires_at)
                );
            }).toList());
        return snapshot;
    }

    private Map<String, Object> staffSnapshot(User user, Store store) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("user_id", user.getId());
        snapshot.put("username", value(user.getUsername()));
        snapshot.put("status", value(user.getStatus()));
        snapshot.put("role_code", roleCode(user.getRole_id()));
        UserCredential credential = user.getId() == null
            ? null
            : credentialRepository.findFirstByUserIdAndIsActiveTrue(user.getId()).orElse(null);
        snapshot.put("credential", credential == null ? Map.of() : Map.of(
            "id", credential.id,
            "login_identifier", value(credential.loginIdentifier),
            "password_algorithm", value(credential.passwordAlgorithm),
            "is_active", Boolean.TRUE.equals(credential.isActive)
        ));
        StoreMembership storeMembership = user.getId() == null
            ? null
            : storeMembershipRepository.findFirstByUserIdAndStoreId(user.getId(), store.id).orElse(null);
        snapshot.put("store_membership", storeMembership == null ? Map.of() : Map.of(
            "id", storeMembership.id,
            "organization_id", storeMembership.organizationId,
            "role_code", value(storeMembership.roleCode),
            "is_active", Boolean.TRUE.equals(storeMembership.isActive)
        ));
        OrganizationMembership organizationMembership = user.getId() == null
            ? null
            : organizationMembershipRepository.findFirstByUserIdAndOrganizationId(user.getId(), store.organization_id).orElse(null);
        snapshot.put("organization_membership", organizationMembership == null ? Map.of() : Map.of(
            "id", organizationMembership.id,
            "organization_id", organizationMembership.organizationId,
            "role_code", value(organizationMembership.roleCode),
            "is_active", Boolean.TRUE.equals(organizationMembership.isActive)
        ));
        return snapshot;
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private StoreReadinessResponse toResponse(
        Store store,
        StoreReadinessEvidenceEntity evidence,
        Long historyEvidenceId,
        List<StoreReadinessResponse.Check> checks,
        boolean ready
    ) {
        StoreReadinessResponse response = new StoreReadinessResponse();
        response.evidence_id = historyEvidenceId;
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

    private enum ReadinessPolicy {
        SYNTHETIC_ACCEPTANCE,
        OPERATIONAL_BASELINE
    }
}
