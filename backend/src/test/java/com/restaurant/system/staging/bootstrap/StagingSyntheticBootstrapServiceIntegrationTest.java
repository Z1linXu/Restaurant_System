package com.restaurant.system.staging.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.restaurant.system.auth.repository.UserCredentialRepository;
import com.restaurant.system.auth.service.impl.PasswordServiceImpl;
import com.restaurant.system.owner.service.impl.OnboardingStaffProvisioningServiceImpl;
import com.restaurant.system.platform.repository.OrganizationRepository;
import com.restaurant.system.staging.bootstrap.repository.StagingSyntheticBootstrapRequestRepository;
import com.restaurant.system.user.entity.Role;
import com.restaurant.system.user.repository.OrganizationMembershipRepository;
import com.restaurant.system.user.repository.RoleRepository;
import com.restaurant.system.user.repository.StoreMembershipRepository;
import com.restaurant.system.user.repository.StoreRepository;
import com.restaurant.system.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("staging-synthetic-bootstrap")
@ContextConfiguration(classes = StagingSyntheticBootstrapServiceIntegrationTest.JpaSliceConfiguration.class)
@Import({
    StagingSyntheticBootstrapGuard.class,
    StagingSyntheticBootstrapServiceImpl.class,
    OnboardingStaffProvisioningServiceImpl.class,
    PasswordServiceImpl.class
})
class StagingSyntheticBootstrapServiceIntegrationTest {

    private static final String RUNTIME_SHA = "4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c";
    private static final String TOOL_SHA = "1111111111111111111111111111111111111111";
    private final String password = "STG005-" + UUID.randomUUID();

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = MybatisPlusAutoConfiguration.class)
    @EntityScan(basePackages = "com.restaurant.system")
    @EnableJpaRepositories(basePackages = "com.restaurant.system")
    static class JpaSliceConfiguration {
    }

    @Autowired
    private StagingSyntheticBootstrapService bootstrapService;
    @Autowired
    private StagingSyntheticBootstrapRequestRepository bootstrapRequestRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private StoreRepository storeRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserCredentialRepository userCredentialRepository;
    @Autowired
    private OrganizationMembershipRepository organizationMembershipRepository;
    @Autowired
    private StoreMembershipRepository storeMembershipRepository;
    @Autowired
    private RoleRepository roleRepository;

    @BeforeEach
    void setUp() {
        organizationMembershipRepository.deleteAll();
        storeMembershipRepository.deleteAll();
        userCredentialRepository.deleteAll();
        userRepository.deleteAll();
        storeRepository.deleteAll();
        organizationRepository.deleteAll();
        bootstrapRequestRepository.deleteAll();
        roleRepository.deleteAll();

        Role owner = new Role();
        owner.setName("Owner");
        owner.setCode("OWNER");
        owner.setCreated_at(LocalDateTime.now());
        owner.setUpdated_at(owner.getCreated_at());
        roleRepository.saveAndFlush(owner);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void firstExecutionCreatesMinimalTopologyAndExactReplayReturnsSameObjects() {
        StagingSyntheticBootstrapResult first = bootstrapService.bootstrap(spec(), password);
        StagingSyntheticBootstrapResult replay = bootstrapService.bootstrap(spec(), password);

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.organizationId()).isEqualTo(first.organizationId());
        assertThat(replay.sourceStoreId()).isEqualTo(first.sourceStoreId());
        assertThat(replay.ownerUserId()).isEqualTo(first.ownerUserId());
        assertThat(organizationRepository.count()).isOne();
        assertThat(storeRepository.count()).isOne();
        assertThat(userRepository.count()).isOne();
        assertThat(userCredentialRepository.count()).isOne();
        assertThat(organizationMembershipRepository.count()).isOne();
        assertThat(storeMembershipRepository.count()).isOne();
        assertThat(bootstrapRequestRepository.count()).isOne();

        com.restaurant.system.user.entity.Store store = storeRepository.findById(first.sourceStoreId()).orElseThrow();
        assertThat(store.printing_enabled).isFalse();
        assertThat(store.printing_mode).isEqualTo("DISABLED");
        assertThat(store.enable_bar_kitchen_tasks).isFalse();

        com.restaurant.system.auth.entity.UserCredential credential =
            userCredentialRepository.findFirstByLoginIdentifierIgnoreCase("STG005_OWNER_20260730_R01").orElseThrow();
        assertThat(credential.passwordAlgorithm).isEqualTo("BCRYPT");
        assertThat(credential.passwordHash)
            .startsWith("$2")
            .doesNotContain(password);
        assertThat(bootstrapRequestRepository.findAll().get(0).requestFingerprint)
            .hasSize(64)
            .doesNotContain(password);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void sameRunIdWithDifferentPayloadConflictsWithoutCreatingMoreData() {
        bootstrapService.bootstrap(spec(), password);
        StagingSyntheticBootstrapSpec changed = new StagingSyntheticBootstrapSpec(
            spec().runId(),
            "STG005_ORG_CHANGED_20260730_R01",
            spec().organizationCode(),
            spec().sourceStoreName(),
            spec().sourceStoreCode(),
            spec().ownerLoginIdentifier(),
            spec().ownerFullName(),
            spec().runtimeSha(),
            spec().toolSha()
        );

        assertThatThrownBy(() -> bootstrapService.bootstrap(changed, password))
            .isInstanceOfSatisfying(
                StagingSyntheticBootstrapException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo("STG005_BOOTSTRAP_IDEMPOTENCY_CONFLICT")
            )
            .hasMessageNotContaining(password);
        assertThat(organizationRepository.count()).isOne();
        assertThat(storeRepository.count()).isOne();
        assertThat(userRepository.count()).isOne();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void sameRunIdWithDifferentPasswordConflictsWithoutPersistingPassword() {
        bootstrapService.bootstrap(spec(), password);
        String changedPassword = "STG005-" + UUID.randomUUID();

        assertThatThrownBy(() -> bootstrapService.bootstrap(spec(), changedPassword))
            .isInstanceOfSatisfying(
                StagingSyntheticBootstrapException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo("STG005_BOOTSTRAP_IDEMPOTENCY_CONFLICT")
            )
            .hasMessageNotContaining(password)
            .hasMessageNotContaining(changedPassword);
        assertThat(userCredentialRepository.count()).isOne();
        assertThat(bootstrapRequestRepository.findAll().get(0).requestFingerprint)
            .doesNotContain(password)
            .doesNotContain(changedPassword);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void preexistingIdentifierWithDifferentCaseIsRejectedWithoutCreatingTopology() {
        com.restaurant.system.platform.entity.Organization existing =
            new com.restaurant.system.platform.entity.Organization();
        existing.name = "STG005_ORG_EXISTING";
        existing.code = spec().organizationCode().toLowerCase(java.util.Locale.ROOT);
        existing.status = "active";
        existing.created_at = LocalDateTime.now();
        existing.updated_at = existing.created_at;
        organizationRepository.saveAndFlush(existing);

        assertThatThrownBy(() -> bootstrapService.bootstrap(spec(), password))
            .isInstanceOfSatisfying(
                StagingSyntheticBootstrapException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo("STG005_BOOTSTRAP_IDENTIFIER_CONFLICT")
            );
        assertThat(organizationRepository.count()).isOne();
        assertThat(storeRepository.count()).isZero();
        assertThat(userRepository.count()).isZero();
        assertThat(bootstrapRequestRepository.count()).isZero();
    }

    private StagingSyntheticBootstrapSpec spec() {
        return new StagingSyntheticBootstrapSpec(
            "STG005_20260730_R01",
            "STG005_ORG_20260730_R01",
            "STG005_ORG_20260730_R01",
            "STG005_SRC_20260730_R01",
            "STG005_SRC_20260730_R01",
            "STG005_OWNER_20260730_R01",
            "STG005_OWNER_20260730_R01",
            RUNTIME_SHA,
            TOOL_SHA
        );
    }
}
