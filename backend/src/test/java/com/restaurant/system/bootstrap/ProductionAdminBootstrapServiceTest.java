package com.restaurant.system.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurant.system.auth.entity.UserCredential;
import com.restaurant.system.auth.repository.UserCredentialRepository;
import com.restaurant.system.auth.service.PasswordService;
import com.restaurant.system.auth.service.impl.PasswordServiceImpl;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.platform.entity.Organization;
import com.restaurant.system.platform.repository.OrganizationRepository;
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
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class ProductionAdminBootstrapServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserCredentialRepository userCredentialRepository;
    @Mock
    private OrganizationMembershipRepository organizationMembershipRepository;
    @Mock
    private StoreMembershipRepository storeMembershipRepository;

    private PasswordService passwordService;
    private ProductionAdminBootstrapService bootstrapService;

    @BeforeEach
    void setUp() {
        passwordService = new PasswordServiceImpl();
        bootstrapService = new ProductionAdminBootstrapService(
            organizationRepository,
            storeRepository,
            userRepository,
            roleRepository,
            userCredentialRepository,
            organizationMembershipRepository,
            storeMembershipRepository,
            passwordService
        );
    }

    @Test
    void bootstrapsFirstOwnerWithCredentialAndMemberships() {
        Role ownerRole = role(1L, "Owner", "OWNER");
        stubCleanOwnerAdminGuard(List.of(ownerRole));
        stubAvailableUsername();
        when(roleRepository.findFirstByCodeIgnoreCase("OWNER")).thenReturn(Optional.of(ownerRole));
        stubUniqueCodes();
        stubEntitySaves();

        ProductionAdminBootstrapResult result = bootstrapService.bootstrap(request(false));

        assertEquals("Acme Restaurant Group", result.organizationName());
        assertEquals("Acme Main Store", result.storeName());
        assertEquals("owner", result.username());

        ArgumentCaptor<Organization> organizationCaptor = ArgumentCaptor.forClass(Organization.class);
        ArgumentCaptor<Store> storeCaptor = ArgumentCaptor.forClass(Store.class);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<UserCredential> credentialCaptor = ArgumentCaptor.forClass(UserCredential.class);
        ArgumentCaptor<OrganizationMembership> organizationMembershipCaptor =
            ArgumentCaptor.forClass(OrganizationMembership.class);
        ArgumentCaptor<StoreMembership> storeMembershipCaptor = ArgumentCaptor.forClass(StoreMembership.class);

        verify(organizationRepository).save(organizationCaptor.capture());
        verify(storeRepository).save(storeCaptor.capture());
        verify(userRepository).save(userCaptor.capture());
        verify(userCredentialRepository).save(credentialCaptor.capture());
        verify(organizationMembershipRepository).save(organizationMembershipCaptor.capture());
        verify(storeMembershipRepository).save(storeMembershipCaptor.capture());

        Organization organization = organizationCaptor.getValue();
        Store store = storeCaptor.getValue();
        User user = userCaptor.getValue();
        UserCredential credential = credentialCaptor.getValue();
        OrganizationMembership organizationMembership = organizationMembershipCaptor.getValue();
        StoreMembership storeMembership = storeMembershipCaptor.getValue();

        assertEquals("Acme Restaurant Group", organization.name);
        assertEquals("ACME_RESTAURANT_GROUP", organization.code);
        assertEquals("active", organization.status);
        assertEquals(10L, store.organization_id);
        assertEquals("ACME_MAIN_STORE", store.code);
        assertEquals("active", store.status);
        assertEquals(20L, user.getStore_id());
        assertEquals(1L, user.getRole_id());
        assertEquals("owner", user.getUsername());
        assertEquals("Owner User", user.getFull_name());
        assertEquals("owner@example.com", user.getPhone());
        assertEquals("active", user.getStatus());

        assertEquals(30L, credential.userId);
        assertEquals("owner", credential.loginIdentifier);
        assertEquals("BCRYPT", credential.passwordAlgorithm);
        assertTrue(Boolean.TRUE.equals(credential.isActive));
        assertNotEquals("StrongPass123!", credential.passwordHash);
        assertTrue(passwordService.matches("StrongPass123!", credential.passwordHash));

        assertEquals(10L, organizationMembership.organizationId);
        assertEquals(30L, organizationMembership.userId);
        assertEquals(1L, organizationMembership.roleId);
        assertEquals("OWNER", organizationMembership.roleCode);
        assertTrue(Boolean.TRUE.equals(organizationMembership.isActive));

        assertEquals(10L, storeMembership.organizationId);
        assertEquals(20L, storeMembership.storeId);
        assertEquals(30L, storeMembership.userId);
        assertEquals(1L, storeMembership.roleId);
        assertEquals("OWNER", storeMembership.roleCode);
        assertTrue(Boolean.TRUE.equals(storeMembership.isActive));
    }

    @Test
    void dryRunValidatesButDoesNotPersist() {
        stubCleanOwnerAdminGuard(List.of(role(1L, "Owner", "OWNER")));
        stubAvailableUsername();

        ProductionAdminBootstrapResult result = bootstrapService.bootstrap(request(true));

        assertTrue(result.dryRun());
        verify(organizationRepository, never()).save(any(Organization.class));
        verify(storeRepository, never()).save(any(Store.class));
        verify(userRepository, never()).save(any(User.class));
        verify(userCredentialRepository, never()).save(any(UserCredential.class));
        verify(organizationMembershipRepository, never()).save(any(OrganizationMembership.class));
        verify(storeMembershipRepository, never()).save(any(StoreMembership.class));
    }

    @Test
    void refusesWhenActiveOwnerAlreadyExists() {
        Role ownerRole = role(1L, "Owner", "OWNER");
        User owner = new User();
        owner.setRole_id(ownerRole.getId());
        owner.setUsername("existing_owner");
        owner.setStatus("active");
        when(roleRepository.findAll()).thenReturn(List.of(ownerRole));
        when(userRepository.findAll()).thenReturn(List.of(owner));
        when(organizationMembershipRepository.findAll()).thenReturn(List.of());
        when(storeMembershipRepository.findAll()).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class, () -> bootstrapService.bootstrap(request(false)));

        assertTrue(exception.getMessage().contains("already exists"));
        verify(organizationRepository, never()).save(any(Organization.class));
        verify(userCredentialRepository, never()).save(any(UserCredential.class));
    }

    @Test
    void refusesWhenActiveOwnerMembershipUsesRoleIdWithoutRoleCode() {
        Role ownerRole = role(1L, "Owner", "OWNER");
        OrganizationMembership membership = new OrganizationMembership();
        membership.roleId = ownerRole.getId();
        membership.roleCode = null;
        membership.isActive = true;
        when(roleRepository.findAll()).thenReturn(List.of(ownerRole));
        when(userRepository.findAll()).thenReturn(List.of());
        when(organizationMembershipRepository.findAll()).thenReturn(List.of(membership));
        when(storeMembershipRepository.findAll()).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class, () -> bootstrapService.bootstrap(request(false)));

        assertTrue(exception.getMessage().contains("already exists"));
        verify(organizationRepository, never()).save(any(Organization.class));
        verify(userCredentialRepository, never()).save(any(UserCredential.class));
    }

    @Test
    void createsOwnerRoleWhenSafeMetadataRoleIsMissing() {
        stubCleanOwnerAdminGuard(List.of());
        stubAvailableUsername();
        when(roleRepository.findFirstByCodeIgnoreCase("OWNER")).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
            Role role = invocation.getArgument(0);
            role.setId(99L);
            return role;
        });
        stubUniqueCodes();
        stubEntitySaves();

        bootstrapService.bootstrap(request(false));

        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(roleCaptor.capture());
        Role createdRole = roleCaptor.getValue();
        assertEquals("Owner", createdRole.getName());
        assertEquals("OWNER", createdRole.getCode());
    }

    @Test
    void bootstrapMethodIsTransactional() throws NoSuchMethodException {
        Method method = ProductionAdminBootstrapService.class.getMethod("bootstrap", ProductionAdminBootstrapRequest.class);

        assertNotNull(method.getAnnotation(Transactional.class));
    }

    private void stubCleanOwnerAdminGuard(List<Role> roles) {
        when(roleRepository.findAll()).thenReturn(roles);
        when(userRepository.findAll()).thenReturn(List.of());
        when(organizationMembershipRepository.findAll()).thenReturn(List.of());
        when(storeMembershipRepository.findAll()).thenReturn(List.of());
    }

    private void stubAvailableUsername() {
        when(userRepository.findFirstByUsernameIgnoreCase("owner")).thenReturn(Optional.empty());
        when(userCredentialRepository.existsByLoginIdentifierIgnoreCase("owner")).thenReturn(false);
    }

    private void stubUniqueCodes() {
        when(organizationRepository.findByCode("ACME_RESTAURANT_GROUP")).thenReturn(null);
        when(storeRepository.findAll()).thenReturn(List.of());
    }

    private void stubEntitySaves() {
        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> {
            Organization organization = invocation.getArgument(0);
            organization.id = 10L;
            return organization;
        });
        when(storeRepository.save(any(Store.class))).thenAnswer(invocation -> {
            Store store = invocation.getArgument(0);
            store.id = 20L;
            return store;
        });
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(30L);
            return user;
        });
        when(userCredentialRepository.save(any(UserCredential.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(organizationMembershipRepository.save(any(OrganizationMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storeMembershipRepository.save(any(StoreMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ProductionAdminBootstrapRequest request(boolean dryRun) {
        return new ProductionAdminBootstrapRequest(
            "Acme Restaurant Group",
            "Acme Main Store",
            "owner",
            "Owner User",
            "owner@example.com",
            "StrongPass123!",
            dryRun
        );
    }

    private Role role(Long id, String name, String code) {
        Role role = new Role();
        role.setId(id);
        role.setName(name);
        role.setCode(code);
        role.setCreated_at(LocalDateTime.now());
        role.setUpdated_at(LocalDateTime.now());
        return role;
    }
}
