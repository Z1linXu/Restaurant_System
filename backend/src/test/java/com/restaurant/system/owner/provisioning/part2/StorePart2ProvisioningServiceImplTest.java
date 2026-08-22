package com.restaurant.system.owner.provisioning.part2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.printing.PrintingRuntimePolicyProperties;
import com.restaurant.system.user.entity.Store;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

class StorePart2ProvisioningServiceImplTest {

    @Test
    void returnsPersistedCompletedRequestStatusAfterProvisioning() {
        StoreRepositoryFixture fixture = new StoreRepositoryFixture();
        StorePart2ProvisioningRequest request = new StorePart2ProvisioningRequest();
        Part2ProvisioningPlan plan = new Part2ProvisioningPlan(
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            "{}",
            "a".repeat(64)
        );
        StoreProvisioningPart2RequestEntity reserved = requestEntity("PROCESSING");
        StoreProvisioningPart2RequestEntity completed = requestEntity("COMPLETED");
        StoreReadinessResponse readiness = new StoreReadinessResponse();
        readiness.evidence_id = 77L;
        readiness.readiness_status = "READY";
        readiness.ready = true;

        Part2PlanNormalizer normalizer = mock(Part2PlanNormalizer.class);
        StorePart2RequestCoordinator requestCoordinator = mock(StorePart2RequestCoordinator.class);
        StorePart2ProvisioningWriter writer = mock(StorePart2ProvisioningWriter.class);
        StoreReadinessService readinessService = mock(StoreReadinessService.class);
        StoreActivationRequestCoordinator activationCoordinator = mock(StoreActivationRequestCoordinator.class);
        PrintingRuntimePolicyProperties printingPolicy = mock(PrintingRuntimePolicyProperties.class);

        when(normalizer.normalize(request)).thenReturn(plan);
        when(requestCoordinator.reserve(1L, 42L, "part2-key", plan, 9L))
            .thenReturn(new Part2Reservation(reserved, false));
        when(fixture.storeRepository.findById(42L)).thenReturn(Optional.of(fixture.store));
        when(fixture.storeRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(fixture.store));
        when(writer.write(fixture.store, reserved.id, plan)).thenReturn(new StorePart2ProvisioningWriter.WriteResult(
            List.of(),
            List.of(),
            List.of(),
            0,
            0,
            0,
            0,
            0
        ));
        when(readinessService.evaluate(1L, 42L)).thenReturn(readiness);
        when(requestCoordinator.complete(eq(reserved.id), eq(77L), any(StorePart2ProvisioningWriter.WriteResult.class)))
            .thenReturn(completed);

        StorePart2ProvisioningServiceImpl service = new StorePart2ProvisioningServiceImpl(
            fixture.storeRepository,
            normalizer,
            requestCoordinator,
            writer,
            readinessService,
            activationCoordinator,
            printingPolicy,
            new ObjectMapper()
        );

        StorePart2ProvisioningResponse response = service.provision(
            new AuthenticatedUser(9L, 42L, 3L, "owner", "Owner", "OWNER"),
            1L,
            42L,
            "part2-key",
            request
        );

        assertEquals("COMPLETED", response.status);
    }

    private static StoreProvisioningPart2RequestEntity requestEntity(String status) {
        StoreProvisioningPart2RequestEntity request = new StoreProvisioningPart2RequestEntity();
        request.id = 100L;
        request.store_id = 42L;
        request.organization_id = 1L;
        request.status = status;
        return request;
    }

    private static final class StoreRepositoryFixture {
        private final com.restaurant.system.user.repository.StoreRepository storeRepository =
            mock(com.restaurant.system.user.repository.StoreRepository.class);
        private final Store store = new Store();

        private StoreRepositoryFixture() {
            store.id = 42L;
            store.organization_id = 1L;
            store.store_kind = "VALIDATION_FIXTURE";
            store.provisioning_source = "PHASE_B_OWNER_PROVISIONING";
            store.status = "inactive";
            store.lifecycle_status = "READY_FOR_REVIEW";
        }
    }
}
