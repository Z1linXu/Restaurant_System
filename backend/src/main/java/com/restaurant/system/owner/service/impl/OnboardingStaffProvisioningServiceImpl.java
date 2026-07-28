package com.restaurant.system.owner.service.impl;

import com.restaurant.system.auth.entity.UserCredential;
import com.restaurant.system.auth.repository.UserCredentialRepository;
import com.restaurant.system.auth.service.PasswordService;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.owner.service.OnboardingStaffProvisioningCommand;
import com.restaurant.system.owner.service.OnboardingStaffProvisioningService;
import com.restaurant.system.owner.service.ProvisionedStoreStaff;
import com.restaurant.system.user.entity.Role;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.entity.StoreMembership;
import com.restaurant.system.user.entity.User;
import com.restaurant.system.user.repository.RoleRepository;
import com.restaurant.system.user.repository.StoreMembershipRepository;
import com.restaurant.system.user.repository.StoreRepository;
import com.restaurant.system.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Store-scoped human identity provisioning used by owner onboarding only.
 */
@Service
public class OnboardingStaffProvisioningServiceImpl implements OnboardingStaffProvisioningService {

    private final UserRepository userRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final StoreMembershipRepository storeMembershipRepository;
    private final StoreRepository storeRepository;
    private final RoleRepository roleRepository;
    private final PasswordService passwordService;

    public OnboardingStaffProvisioningServiceImpl(
        UserRepository userRepository,
        UserCredentialRepository userCredentialRepository,
        StoreMembershipRepository storeMembershipRepository,
        StoreRepository storeRepository,
        RoleRepository roleRepository,
        PasswordService passwordService
    ) {
        this.userRepository = userRepository;
        this.userCredentialRepository = userCredentialRepository;
        this.storeMembershipRepository = storeMembershipRepository;
        this.storeRepository = storeRepository;
        this.roleRepository = roleRepository;
        this.passwordService = passwordService;
    }

    @Override
    @Transactional
    public ProvisionedStoreStaff provision(OnboardingStaffProvisioningCommand command) {
        if (command == null) {
            throw new BusinessException("Staff provisioning request is required");
        }

        Long organizationId = requireId(command.organizationId(), "Organization");
        Long storeId = requireId(command.storeId(), "Store");
        String loginIdentifier = requireText(command.loginIdentifier(), "Login identifier");
        String roleCode = requireText(command.roleCode(), "Role code");
        String rawPassword = requireText(command.rawPassword(), "Password");

        Store store = storeRepository.findById(storeId)
            .orElseThrow(() -> new BusinessException("Target store not found"));
        if (!organizationId.equals(store.organization_id)) {
            throw new BusinessException("Target store does not belong to the organization");
        }

        if (userRepository.findFirstByUsernameIgnoreCase(loginIdentifier).isPresent()) {
            throw new BusinessException("Username already exists");
        }
        if (userCredentialRepository.existsByLoginIdentifierIgnoreCase(loginIdentifier)) {
            throw new BusinessException("Login identifier already exists");
        }

        Role role = roleRepository.findFirstByCodeIgnoreCase(roleCode)
            .orElseThrow(() -> new BusinessException("Role not found"));
        LocalDateTime now = LocalDateTime.now();

        User user = new User();
        // Existing login routing still reads users.store_id before workspace selection.
        user.setStore_id(storeId);
        user.setRole_id(role.getId());
        user.setUsername(loginIdentifier);
        user.setFull_name(blankToNull(command.fullName()));
        user.setStatus("active");
        user.setCreated_at(now);
        user.setUpdated_at(now);
        User savedUser = userRepository.save(user);

        UserCredential credential = new UserCredential();
        credential.userId = savedUser.getId();
        credential.loginIdentifier = loginIdentifier;
        credential.passwordHash = passwordService.hashPassword(rawPassword);
        credential.passwordAlgorithm = "BCRYPT";
        credential.passwordUpdatedAt = now;
        credential.isActive = true;
        credential.createdAt = now;
        credential.updatedAt = now;
        userCredentialRepository.save(credential);

        StoreMembership membership = new StoreMembership();
        membership.organizationId = organizationId;
        membership.storeId = storeId;
        membership.userId = savedUser.getId();
        membership.roleId = role.getId();
        membership.roleCode = role.getCode();
        membership.isActive = true;
        membership.createdAt = now;
        membership.updatedAt = now;
        storeMembershipRepository.save(membership);

        return new ProvisionedStoreStaff(savedUser.getId(), storeId, loginIdentifier, role.getCode());
    }

    private Long requireId(Long value, String field) {
        if (value == null) {
            throw new BusinessException(field + " is required");
        }
        return value;
    }

    private String requireText(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new BusinessException(field + " is required");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
