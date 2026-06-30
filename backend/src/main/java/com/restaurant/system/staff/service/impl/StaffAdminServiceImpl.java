package com.restaurant.system.staff.service.impl;

import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.auth.entity.UserCredential;
import com.restaurant.system.auth.repository.UserCredentialRepository;
import com.restaurant.system.auth.service.PasswordService;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.ForbiddenException;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.staff.dto.ResetPasswordRequest;
import com.restaurant.system.staff.dto.StaffStoreResponse;
import com.restaurant.system.staff.dto.StaffUserRequest;
import com.restaurant.system.staff.dto.StaffUserResponse;
import com.restaurant.system.staff.service.StaffAdminService;
import com.restaurant.system.user.entity.Role;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.entity.User;
import com.restaurant.system.user.repository.RoleRepository;
import com.restaurant.system.user.repository.StoreRepository;
import com.restaurant.system.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffAdminServiceImpl implements StaffAdminService {

    private static final String ROLE_OWNER = "OWNER";
    private static final String ROLE_MANAGER = "MANAGER";
    private static final String ROLE_FRONTDESK = "FRONTDESK";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final StoreRepository storeRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final PasswordService passwordService;
    private final AuthorizationService authorizationService;
    private final AuditLogService auditLogService;

    public StaffAdminServiceImpl(
        UserRepository userRepository,
        RoleRepository roleRepository,
        StoreRepository storeRepository,
        UserCredentialRepository userCredentialRepository,
        PasswordService passwordService,
        AuthorizationService authorizationService,
        AuditLogService auditLogService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.storeRepository = storeRepository;
        this.userCredentialRepository = userCredentialRepository;
        this.passwordService = passwordService;
        this.authorizationService = authorizationService;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffStoreResponse> getAccessibleStores(AuthenticatedUser actor) {
        if (authorizationService.isOwner(actor)) {
            return storeRepository.findAll().stream()
                .sorted(Comparator.comparing(store -> store.id))
                .map(StaffStoreResponse::from)
                .toList();
        }
        Store store = storeRepository.findById(actor.storeId()).orElseThrow(() -> new BusinessException("Store not found"));
        return List.of(StaffStoreResponse.from(store));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffUserResponse> getStaff(Long storeId, AuthenticatedUser actor) {
        authorizationService.requireStaffManageForStore(storeId);
        return userRepository.findAllByStore_id(storeId).stream()
            .map(user -> StaffUserResponse.from(user, roleCode(user.getRole_id())))
            .filter(response -> authorizationService.isOwner(actor) || ROLE_FRONTDESK.equalsIgnoreCase(response.roleCode))
            .sorted(Comparator.comparing((StaffUserResponse response) -> response.roleCode == null ? "" : response.roleCode)
                .thenComparing(response -> response.username == null ? "" : response.username))
            .toList();
    }

    @Override
    @Transactional
    public StaffUserResponse createStaff(StaffUserRequest request, AuthenticatedUser actor, HttpServletRequest servletRequest) {
        authorizationService.requireStaffManageForStore(request.storeId);
        String targetRoleCode = normalizeRole(request.roleCode);
        ensureCanManageRole(actor, targetRoleCode);
        if (request.password == null || request.password.isBlank()) {
            throw new BusinessException("Password is required when creating staff");
        }
        userRepository.findFirstByUsernameIgnoreCase(request.username).ifPresent(existing -> {
            throw new BusinessException("Username already exists");
        });
        if (userCredentialRepository.existsByLoginIdentifierIgnoreCase(request.username)) {
            throw new BusinessException("Login identifier already exists");
        }

        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setStore_id(request.storeId);
        user.setRole_id(findRole(targetRoleCode).getId());
        user.setUsername(request.username.trim());
        user.setFull_name(blankToNull(request.fullName));
        user.setPhone(blankToNull(request.phone));
        user.setStatus("active");
        user.setCreated_at(now);
        user.setUpdated_at(now);
        User saved = userRepository.save(user);

        UserCredential credential = new UserCredential();
        credential.userId = saved.getId();
        credential.loginIdentifier = saved.getUsername();
        credential.passwordHash = passwordService.hashPassword(request.password);
        credential.passwordAlgorithm = "BCRYPT";
        credential.passwordUpdatedAt = now;
        credential.isActive = true;
        credential.createdAt = now;
        credential.updatedAt = now;
        userCredentialRepository.save(credential);

        auditLogService.record(saved.getStore_id(), actor, "STAFF_CREATED", "USER", saved.getId(), "Created staff " + saved.getUsername(), Map.of("role", targetRoleCode), servletRequest);
        return StaffUserResponse.from(saved, targetRoleCode);
    }

    @Override
    @Transactional
    public StaffUserResponse updateStaff(Long userId, StaffUserRequest request, AuthenticatedUser actor, HttpServletRequest servletRequest) {
        User user = requireUser(userId);
        authorizationService.requireStaffManageForStore(user.getStore_id());
        String currentRole = roleCode(user.getRole_id());
        ensureCanManageExistingUser(actor, user, currentRole);
        String targetRoleCode = normalizeRole(request.roleCode);
        ensureCanManageRole(actor, targetRoleCode);
        if (!authorizationService.isOwner(actor) && !user.getStore_id().equals(request.storeId)) {
            throw new ForbiddenException("Manager cannot move staff to another store");
        }
        String previousUsername = user.getUsername();
        String nextUsername = request.username == null ? "" : request.username.trim();
        if (nextUsername.isBlank()) {
            throw new BusinessException("Username is required");
        }
        if (!nextUsername.equalsIgnoreCase(previousUsername)) {
            userRepository.findFirstByUsernameIgnoreCase(nextUsername)
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> {
                    throw new BusinessException("Username already exists");
                });
            userCredentialRepository.findFirstByLoginIdentifierIgnoreCase(nextUsername)
                .filter(credential -> !credential.userId.equals(userId))
                .ifPresent(credential -> {
                    throw new BusinessException("Login identifier already exists");
                });
        }

        user.setStore_id(request.storeId);
        user.setRole_id(findRole(targetRoleCode).getId());
        user.setUsername(nextUsername);
        user.setFull_name(blankToNull(request.fullName));
        user.setPhone(blankToNull(request.phone));
        user.setUpdated_at(LocalDateTime.now());
        User saved = userRepository.save(user);
        if (!saved.getUsername().equalsIgnoreCase(previousUsername)) {
            userCredentialRepository.findFirstByLoginIdentifierIgnoreCase(previousUsername).ifPresent(credential -> {
                credential.loginIdentifier = saved.getUsername();
                credential.updatedAt = LocalDateTime.now();
                userCredentialRepository.save(credential);
            });
        }

        auditLogService.record(saved.getStore_id(), actor, roleChanged(currentRole, targetRoleCode) ? "STAFF_ROLE_CHANGED" : "STAFF_UPDATED", "USER", saved.getId(), "Updated staff " + saved.getUsername(), Map.of("role", targetRoleCode), servletRequest);
        return StaffUserResponse.from(saved, targetRoleCode);
    }

    @Override
    @Transactional
    public StaffUserResponse deactivateStaff(Long userId, AuthenticatedUser actor, HttpServletRequest servletRequest) {
        return setStaffStatus(userId, actor, "inactive", "STAFF_DEACTIVATED", servletRequest);
    }

    @Override
    @Transactional
    public StaffUserResponse reactivateStaff(Long userId, AuthenticatedUser actor, HttpServletRequest servletRequest) {
        return setStaffStatus(userId, actor, "active", "STAFF_REACTIVATED", servletRequest);
    }

    @Override
    @Transactional
    public StaffUserResponse resetPassword(Long userId, ResetPasswordRequest request, AuthenticatedUser actor, HttpServletRequest servletRequest) {
        User user = requireUser(userId);
        authorizationService.requireStaffManageForStore(user.getStore_id());
        String targetRole = roleCode(user.getRole_id());
        ensureCanManageExistingUser(actor, user, targetRole);
        UserCredential credential = userCredentialRepository.findFirstByLoginIdentifierIgnoreCase(user.getUsername())
            .orElseThrow(() -> new BusinessException("Credential not found for user"));
        LocalDateTime now = LocalDateTime.now();
        credential.passwordHash = passwordService.hashPassword(request.newPassword);
        credential.passwordAlgorithm = "BCRYPT";
        credential.passwordUpdatedAt = now;
        credential.isActive = true;
        credential.updatedAt = now;
        userCredentialRepository.save(credential);
        auditLogService.record(user.getStore_id(), actor, "STAFF_PASSWORD_RESET", "USER", user.getId(), "Reset password for " + user.getUsername(), Map.of("credential", "redacted"), servletRequest);
        return StaffUserResponse.from(user, targetRole);
    }

    private StaffUserResponse setStaffStatus(Long userId, AuthenticatedUser actor, String status, String action, HttpServletRequest servletRequest) {
        User user = requireUser(userId);
        authorizationService.requireStaffManageForStore(user.getStore_id());
        String targetRole = roleCode(user.getRole_id());
        ensureCanManageExistingUser(actor, user, targetRole);
        if (ROLE_OWNER.equalsIgnoreCase(targetRole) && !authorizationService.isOwner(actor)) {
            throw new ForbiddenException("Only owner can change owner status");
        }
        user.setStatus(status);
        user.setUpdated_at(LocalDateTime.now());
        User saved = userRepository.save(user);
        userCredentialRepository.findFirstByLoginIdentifierIgnoreCase(saved.getUsername()).ifPresent(credential -> {
            credential.isActive = "active".equalsIgnoreCase(status);
            credential.updatedAt = LocalDateTime.now();
            userCredentialRepository.save(credential);
        });
        auditLogService.record(saved.getStore_id(), actor, action, "USER", saved.getId(), action + " " + saved.getUsername(), Map.of("status", status), servletRequest);
        return StaffUserResponse.from(saved, targetRole);
    }

    private void ensureCanManageExistingUser(AuthenticatedUser actor, User target, String targetRole) {
        if (!authorizationService.canAccessStore(actor, target.getStore_id())) {
            throw new ForbiddenException("Access denied for store " + target.getStore_id());
        }
        if (authorizationService.isManager(actor) && !ROLE_FRONTDESK.equalsIgnoreCase(targetRole)) {
            throw new ForbiddenException("Manager can only manage frontdesk staff");
        }
    }

    private void ensureCanManageRole(AuthenticatedUser actor, String targetRoleCode) {
        if (authorizationService.isOwner(actor)) {
            return;
        }
        if (authorizationService.isManager(actor) && ROLE_FRONTDESK.equalsIgnoreCase(targetRoleCode)) {
            return;
        }
        throw new ForbiddenException("Access denied for staff role " + targetRoleCode);
    }

    private String normalizeRole(String roleCode) {
        String normalized = roleCode == null ? "" : roleCode.trim().toUpperCase();
        if (!ROLE_OWNER.equals(normalized) && !ROLE_MANAGER.equals(normalized) && !ROLE_FRONTDESK.equals(normalized)) {
            throw new BusinessException("Unsupported staff role: " + roleCode);
        }
        return normalized;
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new BusinessException("User not found"));
    }

    private Role findRole(String roleCode) {
        return roleRepository.findFirstByCodeIgnoreCase(roleCode)
            .orElseThrow(() -> new BusinessException("Role not found: " + roleCode));
    }

    private String roleCode(Long roleId) {
        if (roleId == null) {
            return null;
        }
        return roleRepository.findById(roleId).map(Role::getCode).orElse(null);
    }

    private boolean roleChanged(String left, String right) {
        if (left == null) {
            return right != null;
        }
        return !left.equalsIgnoreCase(right);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
