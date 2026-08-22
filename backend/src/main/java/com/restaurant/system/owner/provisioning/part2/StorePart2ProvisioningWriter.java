package com.restaurant.system.owner.provisioning.part2;

import com.restaurant.system.auth.entity.UserCredential;
import com.restaurant.system.auth.repository.UserCredentialRepository;
import com.restaurant.system.auth.service.PasswordService;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.owner.service.ProvisionedStoreStaff;
import com.restaurant.system.printing.dto.DeviceRegisterRequest;
import com.restaurant.system.printing.dto.DeviceRegisterResponse;
import com.restaurant.system.printing.dto.DeviceHeartbeatRequest;
import com.restaurant.system.printing.entity.StoreDevice;
import com.restaurant.system.printing.repository.StoreDeviceRepository;
import com.restaurant.system.printing.service.StoreDeviceService;
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
import com.restaurant.system.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StorePart2ProvisioningWriter {

    public static final String RESOURCE_STATION = "STATION";
    public static final String RESOURCE_TABLE = "TABLE";
    public static final String RESOURCE_DEVICE = "DEVICE";

    private final StationRepository stationRepository;
    private final DiningTableRepository diningTableRepository;
    private final StoreProvisioningResourceRepository resourceRepository;
    private final StoreLogicalPrinterRoleRepository printerRoleRepository;
    private final UserRepository userRepository;
    private final UserCredentialRepository credentialRepository;
    private final OrganizationMembershipRepository organizationMembershipRepository;
    private final StoreMembershipRepository storeMembershipRepository;
    private final RoleRepository roleRepository;
    private final PasswordService passwordService;
    private final StoreDeviceService storeDeviceService;
    private final StoreDeviceRepository storeDeviceRepository;
    private final StoreDeviceReadinessProofService readinessProofService;

    public StorePart2ProvisioningWriter(
        StationRepository stationRepository,
        DiningTableRepository diningTableRepository,
        StoreProvisioningResourceRepository resourceRepository,
        StoreLogicalPrinterRoleRepository printerRoleRepository,
        UserRepository userRepository,
        UserCredentialRepository credentialRepository,
        OrganizationMembershipRepository organizationMembershipRepository,
        StoreMembershipRepository storeMembershipRepository,
        RoleRepository roleRepository,
        PasswordService passwordService,
        StoreDeviceService storeDeviceService,
        StoreDeviceRepository storeDeviceRepository,
        StoreDeviceReadinessProofService readinessProofService
    ) {
        this.stationRepository = stationRepository;
        this.diningTableRepository = diningTableRepository;
        this.resourceRepository = resourceRepository;
        this.printerRoleRepository = printerRoleRepository;
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.organizationMembershipRepository = organizationMembershipRepository;
        this.storeMembershipRepository = storeMembershipRepository;
        this.roleRepository = roleRepository;
        this.passwordService = passwordService;
        this.storeDeviceService = storeDeviceService;
        this.storeDeviceRepository = storeDeviceRepository;
        this.readinessProofService = readinessProofService;
    }

    @Transactional
    public WriteResult write(Store store, Long requestId, Part2ProvisioningPlan plan) {
        requireInactivePart2Store(store);
        LocalDateTime now = LocalDateTime.now();

        List<Part2ProvisioningPlan.StationSpec> stations = effectiveStations(store, plan.stations());
        List<Part2ProvisioningPlan.TableSpec> tables = plan.tables().isEmpty()
            ? defaultTables()
            : plan.tables();
        List<Part2ProvisioningPlan.StaffSpec> staff = plan.staff().isEmpty()
            ? defaultStaff()
            : plan.staff();
        List<Part2ProvisioningPlan.PrinterRoleSpec> printerRoles = plan.printerRoles().isEmpty()
            ? defaultPrinterRoles()
            : plan.printerRoles();
        List<Part2ProvisioningPlan.DeviceSpec> devices = plan.devices().isEmpty()
            ? defaultDevices()
            : plan.devices();

        for (Part2ProvisioningPlan.StationSpec station : stations) {
            upsertStation(store, requestId, station, now);
        }
        for (Part2ProvisioningPlan.TableSpec table : tables) {
            upsertTable(store, requestId, table, now);
        }

        List<ProvisionedStoreStaff> provisionedStaff = new ArrayList<>();
        List<TemporaryStaffCredential> temporaryStaffCredentials = new ArrayList<>();
        int staffIndex = 0;
        for (Part2ProvisioningPlan.StaffSpec person : staff) {
            StaffWriteResult result = provisionStaff(store, requestId, person, ++staffIndex, now);
            provisionedStaff.add(result.staff());
            temporaryStaffCredentials.add(result.temporaryCredential());
        }

        for (Part2ProvisioningPlan.PrinterRoleSpec printerRole : printerRoles) {
            upsertPrinterRole(store, printerRole, now);
        }

        List<TemporaryDeviceCredential> temporaryDeviceCredentials = new ArrayList<>();
        int deviceIndex = 0;
        for (Part2ProvisioningPlan.DeviceSpec device : devices) {
            TemporaryDeviceCredential credential = upsertDevice(
                store,
                requestId,
                device,
                ++deviceIndex,
                now
            );
            if (credential != null) {
                temporaryDeviceCredentials.add(credential);
            }
        }

        // Provisioning is never the activation path. Keep the compatibility
        // mirror and runtime mode safely disabled until the coordinator runs.
        store.printing_mode = "DISABLED";
        store.printing_enabled = false;
        store.status = "inactive";
        if (store.lifecycle_status == null || "CONFIGURING".equalsIgnoreCase(store.lifecycle_status)) {
            store.lifecycle_status = "READY_FOR_REVIEW";
        }
        store.updated_at = now;

        return new WriteResult(
            provisionedStaff,
            temporaryStaffCredentials,
            temporaryDeviceCredentials,
            stations.size(),
            tables.size(),
            provisionedStaff.size(),
            printerRoles.size(),
            devices.size()
        );
    }

    private Station upsertStation(
        Store store,
        Long requestId,
        Part2ProvisioningPlan.StationSpec spec,
        LocalDateTime now
    ) {
        List<Station> existing = stationRepository.findAllByStoreIdAndCode(store.id, spec.code());
        if (existing.size() > 1) {
            throw new BusinessException("PART2_STATION_DUPLICATE");
        }
        Station target = existing.isEmpty() ? new Station() : existing.get(0);
        target.store_id = store.id;
        target.code = spec.code();
        target.name = spec.name();
        target.name_zh = spec.nameZh() == null ? spec.name() : spec.nameZh();
        target.name_en = spec.nameEn() == null ? spec.name() : spec.nameEn();
        target.station_type = spec.stationType();
        target.sort_order = spec.sortOrder();
        target.is_active = spec.active();
        target.created_at = target.created_at == null ? now : target.created_at;
        target.updated_at = now;
        Station saved = stationRepository.save(target);
        ensureResource(store, requestId, RESOURCE_STATION, spec.code(), saved.id, now);
        return saved;
    }

    private DiningTable upsertTable(
        Store store,
        Long requestId,
        Part2ProvisioningPlan.TableSpec spec,
        LocalDateTime now
    ) {
        List<DiningTable> existing = diningTableRepository.findAllByStoreIdAndTableCode(store.id, spec.code());
        if (existing.size() > 1) {
            throw new BusinessException("PART2_TABLE_DUPLICATE");
        }
        DiningTable target = existing.isEmpty() ? new DiningTable() : existing.get(0);
        target.store_id = store.id;
        target.table_code = spec.code();
        target.table_name = spec.name();
        target.area_name = spec.areaName();
        target.table_config = spec.tableConfig();
        target.capacity = spec.capacity();
        target.supports_split = spec.supportsSplit();
        target.sort_order = spec.sortOrder();
        target.is_active = spec.active();
        target.created_at = target.created_at == null ? now : target.created_at;
        target.updated_at = now;
        DiningTable saved = diningTableRepository.save(target);
        ensureResource(store, requestId, RESOURCE_TABLE, spec.code(), saved.id, now);
        return saved;
    }

    private StaffWriteResult provisionStaff(
        Store store,
        Long requestId,
        Part2ProvisioningPlan.StaffSpec spec,
        int index,
        LocalDateTime now
    ) {
        String roleCode = spec.roleCode().toUpperCase(Locale.ROOT);
        Role role = roleRepository.findFirstByCodeIgnoreCase(roleCode)
            .orElseThrow(() -> new BusinessException("PART2_STAFF_ROLE_NOT_FOUND"));
        String login = spec.loginIdentifier() == null
            ? generatedLogin(store, roleCode, index)
            : normalizeLogin(spec.loginIdentifier());
        if (userRepository.findFirstByUsernameIgnoreCase(login).isPresent()
            || credentialRepository.existsByLoginIdentifierIgnoreCase(login)) {
            throw new BusinessException("PART2_STAFF_LOGIN_ALREADY_EXISTS");
        }
        String rawPassword = spec.rawPassword() == null
            ? generatedPassword(store, requestId, roleCode)
            : spec.rawPassword();

        User user = new User();
        user.setStore_id(store.id);
        user.setRole_id(role.getId());
        user.setUsername(login);
        user.setFull_name(spec.fullName() == null ? roleCode : spec.fullName());
        user.setStatus("active");
        user.setCreated_at(now);
        user.setUpdated_at(now);
        User savedUser = userRepository.save(user);

        UserCredential credential = new UserCredential();
        credential.userId = savedUser.getId();
        credential.loginIdentifier = login;
        credential.passwordHash = passwordService.hashPassword(rawPassword);
        credential.passwordAlgorithm = "BCRYPT";
        credential.passwordUpdatedAt = now;
        credential.isActive = true;
        credential.createdAt = now;
        credential.updatedAt = now;
        credentialRepository.save(credential);

        OrganizationMembership organizationMembership = new OrganizationMembership();
        organizationMembership.organizationId = store.organization_id;
        organizationMembership.userId = savedUser.getId();
        organizationMembership.roleId = role.getId();
        organizationMembership.roleCode = role.getCode();
        organizationMembership.isActive = true;
        organizationMembership.createdAt = now;
        organizationMembership.updatedAt = now;
        organizationMembershipRepository.save(organizationMembership);

        StoreMembership storeMembership = new StoreMembership();
        storeMembership.organizationId = store.organization_id;
        storeMembership.storeId = store.id;
        storeMembership.userId = savedUser.getId();
        storeMembership.roleId = role.getId();
        storeMembership.roleCode = role.getCode();
        storeMembership.isActive = true;
        storeMembership.createdAt = now;
        storeMembership.updatedAt = now;
        storeMembershipRepository.save(storeMembership);

        return new StaffWriteResult(
            new ProvisionedStoreStaff(savedUser.getId(), store.id, login, role.getCode()),
            new TemporaryStaffCredential(login, rawPassword, role.getCode())
        );
    }

    private StoreLogicalPrinterRoleEntity upsertPrinterRole(
        Store store,
        Part2ProvisioningPlan.PrinterRoleSpec spec,
        LocalDateTime now
    ) {
        StoreLogicalPrinterRoleEntity target = printerRoleRepository
            .findByStoreIdAndRoleCode(store.id, spec.roleCode())
            .orElseGet(StoreLogicalPrinterRoleEntity::new);
        if (target.id != null && !Objects.equals(target.module_code, spec.moduleCode())) {
            throw new BusinessException("PART2_PRINTER_ROLE_MODULE_CONFLICT");
        }
        StoreLogicalPrinterRoleEntity moduleRole = printerRoleRepository
            .findByStoreIdAndModuleCode(store.id, spec.moduleCode())
            .orElse(null);
        if (moduleRole != null && !Objects.equals(moduleRole.id, target.id)) {
            throw new BusinessException("PART2_PRINTER_MODULE_DUPLICATE");
        }
        target.organization_id = store.organization_id;
        target.store_id = store.id;
        target.role_code = spec.roleCode();
        target.module_code = spec.moduleCode();
        target.display_name = spec.displayName();
        target.mode = spec.mode();
        target.enabled = spec.enabled();
        target.required = spec.required();
        target.physical_binding_status = "UNBOUND";
        target.assigned_printer_id = null;
        target.created_at = target.created_at == null ? now : target.created_at;
        target.updated_at = now;
        return printerRoleRepository.save(target);
    }

    private TemporaryDeviceCredential upsertDevice(
        Store store,
        Long requestId,
        Part2ProvisioningPlan.DeviceSpec spec,
        int index,
        LocalDateTime now
    ) {
        String resourceCode = "DEVICE_" + index + "_" + normalizeResourceCode(spec.deviceName());
        StoreProvisioningResourceEntity resource = resourceRepository
            .findByStoreIdAndResourceTypeAndResourceCode(store.id, RESOURCE_DEVICE, resourceCode)
            .orElse(null);
        StoreDevice device;
        String rawToken = null;
        if (resource != null) {
            device = storeDeviceRepository.findByIdAndStoreId(resource.target_id, store.id)
                .orElseThrow(() -> new BusinessException("PART2_DEVICE_RESOURCE_MISSING"));
        } else {
            DeviceRegisterRequest request = new DeviceRegisterRequest();
            request.store_id = store.id;
            request.device_name = spec.deviceName();
            request.device_type = spec.deviceType();
            request.app_version = spec.appVersion();
            request.platform = spec.platform();
            DeviceRegisterResponse response = storeDeviceService.registerDevice(request);
            rawToken = response.device_token;
            device = storeDeviceRepository.findByIdAndStoreId(response.device_id, store.id)
                .orElseThrow(() -> new BusinessException("PART2_DEVICE_REGISTRATION_MISSING"));
            ensureResource(store, requestId, RESOURCE_DEVICE, resourceCode, device.id, now);
        }

        if (rawToken != null) {
            DeviceHeartbeatRequest heartbeat = new DeviceHeartbeatRequest();
            heartbeat.app_version = spec.appVersion();
            heartbeat.platform = spec.platform();
            storeDeviceService.heartbeat(device.id, rawToken, heartbeat);

            DeviceReadinessProofRequest proof = new DeviceReadinessProofRequest();
            proof.trusted_build = spec.trustedBuild();
            proof.worker_status = spec.workerStatus();
            readinessProofService.record(device.id, rawToken, proof);
        }
        return rawToken == null ? null : new TemporaryDeviceCredential(device.id, device.deviceName, rawToken);
    }

    private void ensureResource(
        Store store,
        Long requestId,
        String type,
        String code,
        Long targetId,
        LocalDateTime now
    ) {
        StoreProvisioningResourceEntity existing = resourceRepository
            .findByStoreIdAndResourceTypeAndResourceCode(store.id, type, code)
            .orElse(null);
        if (existing != null) {
            if (!Objects.equals(existing.target_id, targetId)) {
                throw new BusinessException("PART2_RESOURCE_IDENTITY_CONFLICT");
            }
            return;
        }
        StoreProvisioningResourceEntity resource = new StoreProvisioningResourceEntity();
        resource.organization_id = store.organization_id;
        resource.store_id = store.id;
        resource.resource_type = type;
        resource.resource_code = code;
        resource.target_id = targetId;
        resource.request_id = requestId;
        resource.created_at = now;
        resourceRepository.save(resource);
    }

    private List<Part2ProvisioningPlan.StationSpec> effectiveStations(
        Store store,
        List<Part2ProvisioningPlan.StationSpec> requested
    ) {
        if (!requested.isEmpty() || stationRepository.countAllByStoreId(store.id) > 0) {
            return requested;
        }
        return List.of(
            new Part2ProvisioningPlan.StationSpec("FRONTDESK", "Synthetic Frontdesk", "前台", "Frontdesk", "FRONTDESK", 1, true),
            new Part2ProvisioningPlan.StationSpec("KITCHEN", "Synthetic Kitchen", "厨房", "Kitchen", "KITCHEN", 2, true)
        );
    }

    private List<Part2ProvisioningPlan.TableSpec> defaultTables() {
        return List.of(
            new Part2ProvisioningPlan.TableSpec("T01", "Table 1", "Synthetic", "split_supported", 2, true, 1, true),
            new Part2ProvisioningPlan.TableSpec("T02", "Table 2", "Synthetic", "split_supported", 2, true, 2, true)
        );
    }

    private List<Part2ProvisioningPlan.StaffSpec> defaultStaff() {
        return List.of(
            new Part2ProvisioningPlan.StaffSpec("MANAGER", null, "Synthetic Manager", null),
            new Part2ProvisioningPlan.StaffSpec("FRONTDESK", null, "Synthetic Frontdesk", null)
        );
    }

    private List<Part2ProvisioningPlan.PrinterRoleSpec> defaultPrinterRoles() {
        return List.of(
            new Part2ProvisioningPlan.PrinterRoleSpec("GRAB", "GRAB", "Synthetic GRAB", "MOCK", true, true),
            new Part2ProvisioningPlan.PrinterRoleSpec("FRONTDESK_RECEIPT", "FRONTDESK_RECEIPT", "Synthetic Receipt", "MOCK", true, true)
        );
    }

    private List<Part2ProvisioningPlan.DeviceSpec> defaultDevices() {
        return List.of(new Part2ProvisioningPlan.DeviceSpec(
            "Synthetic Pad 1",
            "ANDROID_PAD",
            "synthetic-build",
            "STAGING",
            true,
            "HEALTHY"
        ));
    }

    private String generatedLogin(Store store, String roleCode, int index) {
        String base = "P2_" + normalizeResourceCode(store.code) + "_" + roleCode;
        return base + "_" + String.format("%02d", index);
    }

    private String generatedPassword(Store store, Long requestId, String roleCode) {
        String seed = "P2|" + store.organization_id + "|" + store.id + "|" + requestId + "|" + roleCode;
        return "P2!" + Integer.toHexString(seed.hashCode()) + "A9x";
    }

    private String normalizeLogin(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[A-Za-z0-9][A-Za-z0-9._@-]{2,127}")) {
            throw new BusinessException("PART2_STAFF_LOGIN_INVALID");
        }
        return normalized;
    }

    private String normalizeResourceCode(String value) {
        return (value == null ? "DEVICE" : value.trim().toUpperCase(Locale.ROOT))
            .replaceAll("[^A-Z0-9]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "");
    }

    private void requireInactivePart2Store(Store store) {
        if (store == null || store.id == null || store.organization_id == null) {
            throw new BusinessException("PART2_STORE_NOT_FOUND");
        }
        if (!"PHASE_B_OWNER_PROVISIONING".equalsIgnoreCase(store.provisioning_source)
            || !"VALIDATION_FIXTURE".equalsIgnoreCase(store.store_kind)) {
            throw new BusinessException("PART2_ONLY_VALIDATION_FIXTURE_ALLOWED");
        }
        if ("active".equalsIgnoreCase(store.status) || "ACTIVE".equalsIgnoreCase(store.lifecycle_status)) {
            throw new BusinessException("PART2_STORE_ALREADY_LIVE");
        }
    }

    public record TemporaryStaffCredential(String loginIdentifier, String temporaryPassword, String roleCode) {}

    public record TemporaryDeviceCredential(Long deviceId, String deviceName, String deviceToken) {}

    private record StaffWriteResult(ProvisionedStoreStaff staff, TemporaryStaffCredential temporaryCredential) {}

    public record WriteResult(
        List<ProvisionedStoreStaff> staff,
        List<TemporaryStaffCredential> temporaryStaffCredentials,
        List<TemporaryDeviceCredential> temporaryDeviceCredentials,
        int stationCount,
        int tableCount,
        int staffCount,
        int printerRoleCount,
        int deviceCount
    ) {}
}
