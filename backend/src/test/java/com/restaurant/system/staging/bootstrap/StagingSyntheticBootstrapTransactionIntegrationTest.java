package com.restaurant.system.staging.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.restaurant.system.auth.repository.UserCredentialRepository;
import com.restaurant.system.auth.service.impl.PasswordServiceImpl;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.owner.service.impl.OnboardingStaffProvisioningServiceImpl;
import com.restaurant.system.platform.repository.OrganizationRepository;
import com.restaurant.system.staging.bootstrap.repository.StagingSyntheticBootstrapRequestRepository;
import com.restaurant.system.user.entity.OrganizationMembership;
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
import org.springframework.boot.test.mock.mockito.MockBean;
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
@ContextConfiguration(classes = StagingSyntheticBootstrapTransactionIntegrationTest.JpaSliceConfiguration.class)
@Import({
    StagingSyntheticBootstrapGuard.class,
    StagingSyntheticBootstrapServiceImpl.class,
    OnboardingStaffProvisioningServiceImpl.class,
    PasswordServiceImpl.class
})
class StagingSyntheticBootstrapTransactionIntegrationTest {

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
    private StoreMembershipRepository storeMembershipRepository;
    @Autowired
    private RoleRepository roleRepository;
    @MockBean
    private OrganizationMembershipRepository organizationMembershipRepository;

    @BeforeEach
    void setUp() {
        Role owner = new Role();
        owner.setName("Owner");
        owner.setCode("OWNER");
        owner.setCreated_at(LocalDateTime.now());
        owner.setUpdated_at(owner.getCreated_at());
        roleRepository.saveAndFlush(owner);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void organizationMembershipFailureRollsBackEveryBootstrapObject() {
        doThrow(new BusinessException("Synthetic membership persistence failure"))
            .when(organizationMembershipRepository)
            .save(any(OrganizationMembership.class));

        assertThatThrownBy(() -> bootstrapService.bootstrap(
            new StagingSyntheticBootstrapSpec(
                "STG005_20260730_R02",
                "STG005_ORG_20260730_R02",
                "STG005_ORG_20260730_R02",
                "STG005_SRC_20260730_R02",
                "STG005_SRC_20260730_R02",
                "STG005_OWNER_20260730_R02",
                "STG005_OWNER_20260730_R02",
                "4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c",
                "1111111111111111111111111111111111111111"
            ),
            "STG005-" + UUID.randomUUID()
        )).isInstanceOf(BusinessException.class);

        assertThat(bootstrapRequestRepository.count()).isZero();
        assertThat(organizationRepository.count()).isZero();
        assertThat(storeRepository.count()).isZero();
        assertThat(userRepository.count()).isZero();
        assertThat(userCredentialRepository.count()).isZero();
        assertThat(storeMembershipRepository.count()).isZero();
    }
}
