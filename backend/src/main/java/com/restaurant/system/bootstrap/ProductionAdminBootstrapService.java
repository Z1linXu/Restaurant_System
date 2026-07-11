package com.restaurant.system.bootstrap;

import com.restaurant.system.auth.entity.UserCredential;
import com.restaurant.system.auth.repository.UserCredentialRepository;
import com.restaurant.system.auth.service.PasswordService;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.platform.entity.Organization;
import com.restaurant.system.platform.repository.OrganizationRepository;
import com.restaurant.system.printing.PrintingMode;
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
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductionAdminBootstrapService {

    private static final String ROLE_OWNER = "OWNER";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final Set<String> OWNER_OR_ADMIN_ROLES = Set.of(ROLE_OWNER, ROLE_ADMIN);
    private static final String PASSWORD_ALGORITHM_BCRYPT = "BCRYPT";

    private final OrganizationRepository organizationRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final OrganizationMembershipRepository organizationMembershipRepository;
    private final StoreMembershipRepository storeMembershipRepository;
    private final PasswordService passwordService;

    public ProductionAdminBootstrapService(
        OrganizationRepository organizationRepository,
        StoreRepository storeRepository,
        UserRepository userRepository,
        RoleRepository roleRepository,
        UserCredentialRepository userCredentialRepository,
        OrganizationMembershipRepository organizationMembershipRepository,
        StoreMembershipRepository storeMembershipRepository,
        PasswordService passwordService
    ) {
        this.organizationRepository = organizationRepository;
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userCredentialRepository = userCredentialRepository;
        this.organizationMembershipRepository = organizationMembershipRepository;
        this.storeMembershipRepository = storeMembershipRepository;
        this.passwordService = passwordService;
    }

    @Transactional
    public ProductionAdminBootstrapResult bootstrap(ProductionAdminBootstrapRequest request) {
        ValidatedInput input = validate(request);
        ensureNoProductionOwnerOrAdmin();
        ensureUsernameAndCredentialAvailable(input.ownerUsername());

        if (request.dryRun()) {
            return new ProductionAdminBootstrapResult(input.organizationName(), input.storeName(), input.ownerUsername(), true);
        }

        LocalDateTime now = LocalDateTime.now();
        Role ownerRole = ensureOwnerRole(now);

        Organization organization = new Organization();
        organization.name = input.organizationName();
        organization.code = uniqueOrganizationCode(input.organizationName());
        organization.status = "active";
        organization.created_at = now;
        organization.updated_at = now;
        Organization savedOrganization = organizationRepository.save(organization);

        Store store = new Store();
        store.organization_id = savedOrganization.id;
        store.name = input.storeName();
        store.code = uniqueStoreCode(input.storeName());
        store.status = "active";
        store.enable_bar_kitchen_tasks = false;
        store.printing_enabled = false;
        store.printing_mode = PrintingMode.DISABLED;
        store.created_at = now;
        store.updated_at = now;
        Store savedStore = storeRepository.save(store);

        User user = new User();
        user.setStore_id(savedStore.id);
        user.setRole_id(ownerRole.getId());
        user.setUsername(input.ownerUsername());
        user.setFull_name(input.ownerFullName());
        user.setPhone(input.ownerContact());
        user.setStatus("active");
        user.setCreated_at(now);
        user.setUpdated_at(now);
        User savedUser = userRepository.save(user);

        UserCredential credential = new UserCredential();
        credential.userId = savedUser.getId();
        credential.loginIdentifier = savedUser.getUsername();
        credential.passwordHash = passwordService.hashPassword(input.ownerPassword());
        credential.passwordAlgorithm = PASSWORD_ALGORITHM_BCRYPT;
        credential.passwordUpdatedAt = now;
        credential.isActive = true;
        credential.createdAt = now;
        credential.updatedAt = now;
        userCredentialRepository.save(credential);

        OrganizationMembership organizationMembership = new OrganizationMembership();
        organizationMembership.organizationId = savedOrganization.id;
        organizationMembership.userId = savedUser.getId();
        organizationMembership.roleId = ownerRole.getId();
        organizationMembership.roleCode = ROLE_OWNER;
        organizationMembership.isActive = true;
        organizationMembership.createdAt = now;
        organizationMembership.updatedAt = now;
        organizationMembershipRepository.save(organizationMembership);

        StoreMembership storeMembership = new StoreMembership();
        storeMembership.organizationId = savedOrganization.id;
        storeMembership.storeId = savedStore.id;
        storeMembership.userId = savedUser.getId();
        storeMembership.roleId = ownerRole.getId();
        storeMembership.roleCode = ROLE_OWNER;
        storeMembership.isActive = true;
        storeMembership.createdAt = now;
        storeMembership.updatedAt = now;
        storeMembershipRepository.save(storeMembership);

        return new ProductionAdminBootstrapResult(savedOrganization.name, savedStore.name, savedUser.getUsername(), false);
    }

    private ValidatedInput validate(ProductionAdminBootstrapRequest request) {
        if (request == null) {
            throw new BusinessException("Bootstrap request is required");
        }
        String organizationName = required(request.organizationName(), "Organization name");
        String storeName = required(request.storeName(), "Store name");
        String ownerUsername = required(request.ownerUsername(), "Owner username");
        String ownerFullName = required(request.ownerFullName(), "Owner full name");
        String ownerPassword = required(request.ownerPassword(), "Owner password");
        if (ownerPassword.length() < 8) {
            throw new BusinessException("Owner password must be at least 8 characters");
        }
        if (ownerUsername.contains(" ")) {
            throw new BusinessException("Owner username must not contain spaces");
        }
        return new ValidatedInput(
            organizationName,
            storeName,
            ownerUsername,
            ownerFullName,
            blankToNull(request.ownerContact()),
            ownerPassword
        );
    }

    private void ensureNoProductionOwnerOrAdmin() {
        Set<Long> ownerOrAdminRoleIds = roleRepository.findAll().stream()
            .filter(role -> role.getCode() != null && OWNER_OR_ADMIN_ROLES.contains(role.getCode().trim().toUpperCase(Locale.ROOT)))
            .map(Role::getId)
            .collect(java.util.stream.Collectors.toSet());

        boolean activeOwnerOrAdminUserExists = userRepository.findAll().stream()
            .anyMatch(user -> "active".equalsIgnoreCase(user.getStatus())
                && user.getRole_id() != null
                && ownerOrAdminRoleIds.contains(user.getRole_id()));
        boolean activeOwnerOrAdminOrganizationMembershipExists = organizationMembershipRepository.findAll().stream()
            .anyMatch(membership -> Boolean.TRUE.equals(membership.isActive)
                && hasOwnerOrAdminRole(membership.roleCode, membership.roleId, ownerOrAdminRoleIds));
        boolean activeOwnerOrAdminStoreMembershipExists = storeMembershipRepository.findAll().stream()
            .anyMatch(membership -> Boolean.TRUE.equals(membership.isActive)
                && hasOwnerOrAdminRole(membership.roleCode, membership.roleId, ownerOrAdminRoleIds));

        if (activeOwnerOrAdminUserExists
            || activeOwnerOrAdminOrganizationMembershipExists
            || activeOwnerOrAdminStoreMembershipExists) {
            throw new BusinessException("Production owner/admin already exists; bootstrap refused");
        }
    }

    private boolean hasOwnerOrAdminRole(String roleCode, Long roleId, Set<Long> ownerOrAdminRoleIds) {
        boolean roleCodeMatches = roleCode != null
            && OWNER_OR_ADMIN_ROLES.contains(roleCode.trim().toUpperCase(Locale.ROOT));
        return roleCodeMatches || (roleId != null && ownerOrAdminRoleIds.contains(roleId));
    }

    private void ensureUsernameAndCredentialAvailable(String username) {
        userRepository.findFirstByUsernameIgnoreCase(username).ifPresent(existing -> {
            throw new BusinessException("Username already exists");
        });
        if (userCredentialRepository.existsByLoginIdentifierIgnoreCase(username)) {
            throw new BusinessException("Login identifier already exists");
        }
    }

    private Role ensureOwnerRole(LocalDateTime now) {
        return roleRepository.findFirstByCodeIgnoreCase(ROLE_OWNER)
            .orElseGet(() -> {
                Role role = new Role();
                role.setName("Owner");
                role.setCode(ROLE_OWNER);
                role.setCreated_at(now);
                role.setUpdated_at(now);
                return roleRepository.save(role);
            });
    }

    private String uniqueOrganizationCode(String name) {
        String base = baseCode(name, "ORG");
        String candidate = base;
        int suffix = 2;
        while (organizationRepository.findByCode(candidate) != null) {
            candidate = base + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private String uniqueStoreCode(String name) {
        String base = baseCode(name, "STORE");
        String candidate = base;
        int suffix = 2;
        while (storeCodeExists(candidate)) {
            candidate = base + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private boolean storeCodeExists(String code) {
        return storeRepository.findAll().stream()
            .anyMatch(store -> store.code != null && store.code.equalsIgnoreCase(code));
    }

    private String baseCode(String value, String fallback) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9]+", "_")
            .replaceAll("^_+", "")
            .replaceAll("_+$", "");
        if (normalized.isBlank()) {
            return fallback;
        }
        return normalized.length() > 48 ? normalized.substring(0, 48) : normalized;
    }

    private String required(String value, String label) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new BusinessException(label + " is required");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private record ValidatedInput(
        String organizationName,
        String storeName,
        String ownerUsername,
        String ownerFullName,
        String ownerContact,
        String ownerPassword
    ) {
    }
}
