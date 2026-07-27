package com.restaurant.system.owner.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.restaurant.system.auth.repository.UserCredentialRepository;
import com.restaurant.system.auth.service.impl.PasswordServiceImpl;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.OwnerOrganizationAuthorizationService;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.owner.dto.OwnerStoreOnboardingRequest;
import com.restaurant.system.owner.dto.OwnerStoreOnboardingStaffRequest;
import com.restaurant.system.platform.repository.OwnerStoreOnboardingRequestRepository;
import com.restaurant.system.user.entity.Role;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.entity.StoreMembership;
import com.restaurant.system.user.repository.RoleRepository;
import com.restaurant.system.user.repository.StoreMembershipRepository;
import com.restaurant.system.user.repository.StoreRepository;
import com.restaurant.system.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ContextConfiguration(classes = OwnerStoreOnboardingTransactionIntegrationTest.JpaSliceConfiguration.class)
@Import({
    OwnerStoreOnboardingServiceImpl.class,
    OnboardingStaffProvisioningServiceImpl.class,
    PasswordServiceImpl.class
})
class OwnerStoreOnboardingTransactionIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = MybatisPlusAutoConfiguration.class)
    @EntityScan(basePackages = "com.restaurant.system")
    @EnableJpaRepositories(basePackages = "com.restaurant.system")
    static class JpaSliceConfiguration {
    }

    private static final Long ORGANIZATION_ID = 100L;
    private static final Long SOURCE_STORE_ID = 101L;

    @Autowired
    private OwnerStoreOnboardingServiceImpl onboardingService;
    @Autowired
    private StoreRepository storeRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserCredentialRepository userCredentialRepository;
    @MockBean
    private OwnerOrganizationAuthorizationService ownerOrganizationAuthorizationService;
    @MockBean
    private StoreMembershipRepository storeMembershipRepository;
    @MockBean
    private OwnerStoreOnboardingRequestRepository onboardingRequestRepository;

    private Store sourceStore;
    private AuthenticatedUser owner;
    private com.restaurant.system.platform.entity.OwnerStoreOnboardingRequest onboardingRequest;

    @BeforeEach
    void setUp() {
        sourceStore = new Store();
        sourceStore.id = SOURCE_STORE_ID;
        sourceStore.organization_id = ORGANIZATION_ID;
        sourceStore.name = "Source Store";
        sourceStore.code = "SOURCE";
        sourceStore.status = "active";
        sourceStore.printing_enabled = false;
        sourceStore.printing_mode = "DISABLED";
        sourceStore.created_at = LocalDateTime.now();
        sourceStore.updated_at = sourceStore.created_at;
        sourceStore = storeRepository.saveAndFlush(sourceStore);

        Role frontdesk = new Role();
        frontdesk.setCode("FRONTDESK");
        frontdesk.setName("Frontdesk");
        frontdesk.setCreated_at(LocalDateTime.now());
        frontdesk.setUpdated_at(frontdesk.getCreated_at());
        roleRepository.saveAndFlush(frontdesk);

        owner = new AuthenticatedUser(1L, SOURCE_STORE_ID, 1L, "owner-test", "Owner Test", "OWNER");
        when(ownerOrganizationAuthorizationService.requireSourceStoreInOrganization(
            owner,
            ORGANIZATION_ID,
            SOURCE_STORE_ID
        )).thenReturn(sourceStore);

        onboardingRequest = new com.restaurant.system.platform.entity.OwnerStoreOnboardingRequest();
        onboardingRequest.id = 901L;
        onboardingRequest.organizationId = ORGANIZATION_ID;
        onboardingRequest.idempotencyKey = "transaction-rollback-test";
        onboardingRequest.status = "PROCESSING";
        when(onboardingRequestRepository.insertIfAbsent(
            org.mockito.ArgumentMatchers.eq(ORGANIZATION_ID),
            org.mockito.ArgumentMatchers.eq("transaction-rollback-test"),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        )).thenAnswer(invocation -> {
            onboardingRequest.requestFingerprint = invocation.getArgument(2);
            return 1;
        });
        when(onboardingRequestRepository.findForUpdate(ORGANIZATION_ID, "transaction-rollback-test"))
            .thenReturn(Optional.of(onboardingRequest));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void membershipFailureRollsBackStoreCredentialAndOnboardingRequest() {
        doThrow(new BusinessException("Synthetic membership persistence failure"))
            .when(storeMembershipRepository)
            .save(any(StoreMembership.class));

        assertThrows(
            BusinessException.class,
            () -> onboardingService.onboard(
                ORGANIZATION_ID,
                "transaction-rollback-test",
                request(),
                owner
            )
        );

        assertThat(storeRepository.findAllByOrganizationIdOrderByIdAsc(ORGANIZATION_ID))
            .extracting(store -> store.id)
            .containsExactly(sourceStore.id);
        assertThat(userRepository.count()).isZero();
        assertThat(userCredentialRepository.count()).isZero();
    }

    private OwnerStoreOnboardingRequest request() {
        OwnerStoreOnboardingStaffRequest staff = new OwnerStoreOnboardingStaffRequest();
        staff.login_identifier = "transaction-test-user";
        staff.full_name = "Synthetic Staff";
        staff.role_code = "FRONTDESK";
        staff.initial_password = "SyntheticOnly!Pass9";

        OwnerStoreOnboardingRequest request = new OwnerStoreOnboardingRequest();
        request.source_store_id = SOURCE_STORE_ID;
        request.store_name = "Transaction Test Store";
        request.store_code = "TRANSACTION-TEST";
        request.staff = List.of(staff);
        return request;
    }
}
