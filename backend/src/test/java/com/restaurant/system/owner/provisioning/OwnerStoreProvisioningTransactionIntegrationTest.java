package com.restaurant.system.owner.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.combo.StoreComboComponentRepository;
import com.restaurant.system.menu.combo.StoreComboGroupRepository;
import com.restaurant.system.menu.pricing.StorePricingPolicyRepository;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.modules.StoreModuleRepository;
import com.restaurant.system.owner.master.ChainMasterMenuVersionEntity;
import com.restaurant.system.owner.profile.StoreProfileArtifactEntity;
import com.restaurant.system.owner.profile.StoreProfileVersionEntity;
import com.restaurant.system.owner.provisioning.part2.StoreActivationRequestCoordinator;
import com.restaurant.system.owner.provisioning.part2.StoreActivationRequestRepository;
import com.restaurant.system.owner.provisioning.part2.StoreLogicalPrinterRoleRepository;
import com.restaurant.system.owner.provisioning.part2.StoreReadinessEvidenceRepository;
import com.restaurant.system.owner.provisioning.part2.StoreReadinessResponse;
import com.restaurant.system.owner.provisioning.part2.StoreReadinessService;
import com.restaurant.system.platform.entity.Organization;
import com.restaurant.system.platform.repository.OrganizationRepository;
import com.restaurant.system.printing.rules.PrintingDisplayRuleDefaults;
import com.restaurant.system.printing.rules.PrintingDisplayRuleRevision;
import com.restaurant.system.printing.rules.PrintingDisplayRuleRevisionRepository;
import com.restaurant.system.printing.rules.PrintingDisplayRuleSet;
import com.restaurant.system.printing.rules.PrintingDisplayRuleSetRepository;
import com.restaurant.system.station.repository.DiningTableRepository;
import com.restaurant.system.station.repository.StationRepository;
import com.restaurant.system.user.repository.StoreMembershipRepository;
import com.restaurant.system.user.repository.StoreRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
@ContextConfiguration(classes = OwnerStoreProvisioningTransactionIntegrationTest.JpaSliceConfiguration.class)
@Import({
    OwnerStoreProvisioningMaterializer.class,
    OperationalStoreBaselineProvisioner.class,
    OwnerStoreProvisioningRequestCoordinatorImpl.class
})
class OwnerStoreProvisioningTransactionIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = MybatisPlusAutoConfiguration.class)
    @EntityScan(basePackages = "com.restaurant.system")
    @EnableJpaRepositories(basePackages = "com.restaurant.system")
    static class JpaSliceConfiguration {
    }

    private static final String FINGERPRINT = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Autowired private OwnerStoreProvisioningMaterializer materializer;
    @Autowired private OwnerStoreProvisioningRequestCoordinator requestCoordinator;
    @Autowired private OwnerStoreProvisioningRequestRepository requestRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private StoreMembershipRepository membershipRepository;
    @Autowired private DiningTableRepository diningTableRepository;
    @Autowired private StoreLogicalPrinterRoleRepository printerRoleRepository;
    @Autowired private StoreReadinessEvidenceRepository readinessEvidenceRepository;
    @Autowired private StoreActivationRequestRepository activationRequestRepository;

    @MockBean private StoreModuleRepository moduleRepository;
    @MockBean private StationRepository stationRepository;
    @MockBean private MenuCategoryRepository categoryRepository;
    @MockBean private MenuItemRepository itemRepository;
    @MockBean private MenuItemOptionRepository optionRepository;
    @MockBean private StorePricingPolicyRepository pricingPolicyRepository;
    @MockBean private StoreComboGroupRepository comboGroupRepository;
    @MockBean private StoreComboComponentRepository comboComponentRepository;
    @MockBean private PrintingDisplayRuleSetRepository printingRuleSetRepository;
    @MockBean private PrintingDisplayRuleRevisionRepository printingRuleRevisionRepository;
    @MockBean private StoreMenuMasterMappingRepository mappingRepository;
    @MockBean private MenuRevisionService menuRevisionService;
    @MockBean private PhaseBPart1ProvisioningValidator provisioningValidator;
    @MockBean private StoreReadinessService readinessService;
    @MockBean private StoreActivationRequestCoordinator activationRequestCoordinator;

    private Long organizationId;

    @BeforeEach
    void setUp() {
        Organization organization = new Organization();
        organization.name = "Rollback Test Organization";
        organization.code = "ROLLBACK_TEST_ORG";
        organization.status = "active";
        organization.created_at = LocalDateTime.now();
        organization.updated_at = organization.created_at;
        organizationId = organizationRepository.saveAndFlush(organization).id;

        when(pricingPolicyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(comboComponentRepository.findActiveByStoreIdOrdered(anyLong())).thenReturn(List.of());
        when(provisioningValidator.validate(anyLong(), anyLong(), any(Integer.class), any(Integer.class), any()))
            .thenReturn(new PhaseBProvisioningValidationResult("PASS", List.of()));
        when(printingRuleSetRepository.save(any())).thenAnswer(invocation -> {
            PrintingDisplayRuleSet value = invocation.getArgument(0);
            if (value.id == null) value.id = 701L;
            return value;
        });
        when(printingRuleRevisionRepository.save(any())).thenAnswer(invocation -> {
            PrintingDisplayRuleRevision value = invocation.getArgument(0);
            if (value.id == null) value.id = 702L;
            return value;
        });
        when(readinessService.evaluateOperationalBaseline(anyLong(), anyLong(), anyLong()))
            .thenThrow(new BusinessException("INJECTED_LATE_READINESS_FAILURE"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void lateReadinessFailureRollsBackStoreAggregateAndRetainsFailedRequestLedger() {
        long membershipCount = membershipRepository.count();
        long tableCount = diningTableRepository.count();
        long printerRoleCount = printerRoleRepository.count();
        long readinessCount = readinessEvidenceRepository.count();
        long activationCount = activationRequestRepository.count();
        OwnerStoreProvisioningRequestEntity request = requestRepository.saveAndFlush(requestEntity("rollback-request-key"));
        OwnerStoreProvisioningReservation reservation = reservation(request.id);
        ResolvedOwnerStoreProvisioningInput input = input();

        assertThrows(BusinessException.class, () -> materializer.materialize(reservation, input));
        requestCoordinator.fail(new OwnerStoreProvisioningFailureEvidence(
            request.id,
            null,
            "INJECTED_LATE_READINESS_FAILURE"
        ));

        assertThat(storeRepository.findAllByOrganizationIdAndCodeIgnoreCase(organizationId, "ROLLBACK_STORE")).isEmpty();
        assertThat(membershipRepository.count()).isEqualTo(membershipCount);
        assertThat(diningTableRepository.count()).isEqualTo(tableCount);
        assertThat(printerRoleRepository.count()).isEqualTo(printerRoleCount);
        assertThat(readinessEvidenceRepository.count()).isEqualTo(readinessCount);
        assertThat(activationRequestRepository.count()).isEqualTo(activationCount);

        OwnerStoreProvisioningRequestEntity failed = requestRepository.findById(request.id).orElseThrow();
        assertThat(failed.status).isEqualTo("FAILED");
        assertThat(failed.error_code).isEqualTo("INJECTED_LATE_READINESS_FAILURE");
        assertThat(failed.store_id).isNull();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void organizationLockSerializesConcurrentRequestsForTheSameStoreCode() throws Exception {
        StoreReadinessResponse ready = new StoreReadinessResponse();
        ready.ready = true;
        ready.evidence_id = 901L;
        ready.readiness_fingerprint = FINGERPRINT;
        doReturn(ready).when(readinessService).evaluateOperationalBaseline(anyLong(), anyLong(), anyLong());

        OwnerStoreProvisioningRequestEntity firstRequest = requestRepository.saveAndFlush(requestEntity("concurrent-1"));
        OwnerStoreProvisioningRequestEntity secondRequest = requestRepository.saveAndFlush(requestEntity("concurrent-2"));
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(() -> materializeAfter(start, reservation(firstRequest.id)));
            Future<Object> second = executor.submit(() -> materializeAfter(start, reservation(secondRequest.id)));
            start.countDown();

            List<Object> outcomes = List.of(first.get(), second.get());
            assertThat(outcomes.stream().filter(OwnerStoreProvisioningResult.class::isInstance)).hasSize(1);
            assertThat(outcomes.stream().filter(RuntimeException.class::isInstance)).hasSize(1);
        } finally {
            executor.shutdownNow();
        }

        assertThat(storeRepository.findAllByOrganizationIdAndCodeIgnoreCase(organizationId, "ROLLBACK_STORE"))
            .hasSize(1);
    }

    private Object materializeAfter(CountDownLatch start, OwnerStoreProvisioningReservation reservation)
        throws InterruptedException {
        start.await();
        try {
            return materializer.materialize(reservation, input());
        } catch (RuntimeException exception) {
            requestCoordinator.fail(new OwnerStoreProvisioningFailureEvidence(
                reservation.requestId(),
                null,
                "CONCURRENT_STORE_CODE_CONFLICT"
            ));
            return exception;
        }
    }

    private ResolvedOwnerStoreProvisioningInput input() {
        AuthenticatedUser owner = new AuthenticatedUser(501L, null, 601L, "owner", "Owner", "OWNER");
        OwnerStoreProvisioningCommand command = new OwnerStoreProvisioningCommand(
            owner,
            organizationId,
            "rollback-request-key",
            "Rollback Store",
            "ROLLBACK_STORE",
            "TEST_PROFILE",
            "v1",
            FINGERPRINT,
            "TEST_MASTER",
            "v1",
            FINGERPRINT
        );
        StoreProfileVersionEntity profileVersion = new StoreProfileVersionEntity();
        profileVersion.content_json = "{\"module_defaults\":{\"modules\":[]},\"source_store_semantics\":{},\"template_references\":{}}";
        ChainMasterMenuVersionEntity masterVersion = new ChainMasterMenuVersionEntity();
        masterVersion.id = 801L;
        return new ResolvedOwnerStoreProvisioningInput(
            command,
            profileVersion,
            List.of(
                artifact("STATION_TEMPLATE", "{\"stations\":[]}"),
                artifact("PRICING_POLICY", "{\"store_pricing_policy\":{\"size_small_delta\":\"0\",\"size_regular_delta\":\"0\",\"size_large_delta\":\"0\",\"combo_delta\":\"0\"}}"),
                artifact("COMBO_CONFIGURATION", "{\"components\":[]}"),
                artifact("PRINTING_DISPLAY_RULES", PrintingDisplayRuleDefaults.DEFAULT_CONTENT_JSON)
            ),
            masterVersion,
            List.of(),
            List.of(),
            List.of(),
            FINGERPRINT
        );
    }

    private StoreProfileArtifactEntity artifact(String code, String content) {
        StoreProfileArtifactEntity artifact = new StoreProfileArtifactEntity();
        artifact.artifact_code = code;
        artifact.content_json = content;
        return artifact;
    }

    private OwnerStoreProvisioningRequestEntity requestEntity(String idempotencyKey) {
        OwnerStoreProvisioningRequestEntity request = new OwnerStoreProvisioningRequestEntity();
        request.organization_id = organizationId;
        request.idempotency_key = idempotencyKey;
        request.request_fingerprint = FINGERPRINT;
        request.status = "PROCESSING";
        request.store_name = "Rollback Store";
        request.store_code = "ROLLBACK_STORE";
        request.profile_code = "TEST_PROFILE";
        request.profile_version = "v1";
        request.profile_fingerprint_sha256 = FINGERPRINT;
        request.master_menu_key = "TEST_MASTER";
        request.master_menu_version = "v1";
        request.master_menu_fingerprint_sha256 = FINGERPRINT;
        request.validation_status = "PENDING";
        request.actor_user_id = 501L;
        request.created_at = LocalDateTime.now();
        request.updated_at = request.created_at;
        return request;
    }

    private OwnerStoreProvisioningReservation reservation(Long requestId) {
        return new OwnerStoreProvisioningReservation(
            requestId,
            organizationId,
            null,
            "Rollback Store",
            "ROLLBACK_STORE",
            "TEST_PROFILE",
            "v1",
            FINGERPRINT,
            "TEST_MASTER",
            "v1",
            FINGERPRINT,
            "PROCESSING",
            false,
            "PENDING",
            null,
            null,
            new OwnerStoreProvisioningCounts(0, 0, 0, 0, 0, 0, 0)
        );
    }
}
