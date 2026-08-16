package com.restaurant.system.owner.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.owner.exception.OwnerStoreProvisioningException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OwnerStoreProvisioningRequestCoordinatorTest {

    @Mock
    private OwnerStoreProvisioningRequestRepository requestRepository;

    @Test
    void reservesNewProcessingRequest() {
        OwnerStoreProvisioningRequestCoordinatorImpl coordinator = new OwnerStoreProvisioningRequestCoordinatorImpl(
            requestRepository
        );
        ResolvedOwnerStoreProvisioningInput input = input("a".repeat(64), "key-1");
        OwnerStoreProvisioningRequestEntity request = request("PROCESSING", "a".repeat(64));
        when(requestRepository.insertIfAbsent(
            eq(10L),
            eq("key-1"),
            eq("a".repeat(64)),
            eq("Phase B Validation Store"),
            eq("PHASE_B_VALIDATION_STORE"),
            eq("ST_DENIS_CANONICAL_PROFILE"),
            eq("v2"),
            eq("p".repeat(64)),
            eq("LANZHOU_CHAIN_MASTER_MENU"),
            eq("v1"),
            eq("m".repeat(64)),
            eq(20L),
            any(LocalDateTime.class)
        )).thenReturn(1);
        when(requestRepository.findForUpdate(10L, "key-1")).thenReturn(Optional.of(request));

        OwnerStoreProvisioningReservation reservation = coordinator.reserve(input);

        assertThat(reservation.requestId()).isEqualTo(99L);
        assertThat(reservation.replayed()).isFalse();
        assertThat(reservation.status()).isEqualTo("PROCESSING");
    }

    @Test
    void completedRequestReplaysOnlyWhenFingerprintMatches() {
        OwnerStoreProvisioningRequestCoordinatorImpl coordinator = new OwnerStoreProvisioningRequestCoordinatorImpl(
            requestRepository
        );
        ResolvedOwnerStoreProvisioningInput input = input("b".repeat(64), "key-2");
        OwnerStoreProvisioningRequestEntity request = request("COMPLETED", "b".repeat(64));
        request.store_id = 123L;
        when(requestRepository.insertIfAbsent(
            eq(10L),
            eq("key-2"),
            eq("b".repeat(64)),
            eq("Phase B Validation Store"),
            eq("PHASE_B_VALIDATION_STORE"),
            eq("ST_DENIS_CANONICAL_PROFILE"),
            eq("v2"),
            eq("p".repeat(64)),
            eq("LANZHOU_CHAIN_MASTER_MENU"),
            eq("v1"),
            eq("m".repeat(64)),
            eq(20L),
            any(LocalDateTime.class)
        )).thenReturn(0);
        when(requestRepository.findForUpdate(10L, "key-2")).thenReturn(Optional.of(request));

        OwnerStoreProvisioningReservation reservation = coordinator.reserve(input);

        assertThat(reservation.replayed()).isTrue();
        assertThat(reservation.storeId()).isEqualTo(123L);
    }

    @Test
    void reusedIdempotencyKeyWithDifferentFingerprintConflicts() {
        OwnerStoreProvisioningRequestCoordinatorImpl coordinator = new OwnerStoreProvisioningRequestCoordinatorImpl(
            requestRepository
        );
        ResolvedOwnerStoreProvisioningInput input = input("c".repeat(64), "key-3");
        when(requestRepository.insertIfAbsent(
            eq(10L),
            eq("key-3"),
            eq("c".repeat(64)),
            eq("Phase B Validation Store"),
            eq("PHASE_B_VALIDATION_STORE"),
            eq("ST_DENIS_CANONICAL_PROFILE"),
            eq("v2"),
            eq("p".repeat(64)),
            eq("LANZHOU_CHAIN_MASTER_MENU"),
            eq("v1"),
            eq("m".repeat(64)),
            eq(20L),
            any(LocalDateTime.class)
        )).thenReturn(0);
        when(requestRepository.findForUpdate(10L, "key-3"))
            .thenReturn(Optional.of(request("PROCESSING", "d".repeat(64))));

        assertThatThrownBy(() -> coordinator.reserve(input))
            .isInstanceOfSatisfying(OwnerStoreProvisioningException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void completePreservesWarningValidationStatusAsCompletedEvidence() {
        OwnerStoreProvisioningRequestCoordinatorImpl coordinator = new OwnerStoreProvisioningRequestCoordinatorImpl(
            requestRepository
        );
        OwnerStoreProvisioningRequestEntity request = request("PROCESSING", "e".repeat(64));
        when(requestRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(request));

        OwnerStoreProvisioningReservation reservation = coordinator.complete(successEvidence("WARNING"));

        assertThat(reservation.status()).isEqualTo("COMPLETED");
        assertThat(reservation.validationStatus()).isEqualTo("WARNING");
        verify(requestRepository).save(request);
    }

    @Test
    void completeRejectsBlockingValidationStatus() {
        OwnerStoreProvisioningRequestCoordinatorImpl coordinator = new OwnerStoreProvisioningRequestCoordinatorImpl(
            requestRepository
        );
        when(requestRepository.findByIdForUpdate(99L))
            .thenReturn(Optional.of(request("PROCESSING", "f".repeat(64))));

        assertThatThrownBy(() -> coordinator.complete(successEvidence("BLOCKING")))
            .isInstanceOfSatisfying(OwnerStoreProvisioningException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo("STORE_PROVISIONING_VALIDATION_STATUS_INVALID"));
    }

    private ResolvedOwnerStoreProvisioningInput input(String fingerprint, String idempotencyKey) {
        return new ResolvedOwnerStoreProvisioningInput(
            new OwnerStoreProvisioningCommand(
                new AuthenticatedUser(20L, null, 1L, "owner", "Owner", "OWNER"),
                10L,
                idempotencyKey,
                "Phase B Validation Store",
                "PHASE_B_VALIDATION_STORE",
                "ST_DENIS_CANONICAL_PROFILE",
                "v2",
                "p".repeat(64),
                "LANZHOU_CHAIN_MASTER_MENU",
                "v1",
                "m".repeat(64)
            ),
            null,
            List.of(),
            null,
            List.of(),
            List.of(),
            List.of(),
            fingerprint
        );
    }

    private OwnerStoreProvisioningRequestEntity request(String status, String fingerprint) {
        OwnerStoreProvisioningRequestEntity request = new OwnerStoreProvisioningRequestEntity();
        request.id = 99L;
        request.organization_id = 10L;
        request.idempotency_key = "key";
        request.request_fingerprint = fingerprint;
        request.status = status;
        request.store_name = "Phase B Validation Store";
        request.store_code = "PHASE_B_VALIDATION_STORE";
        request.profile_code = "ST_DENIS_CANONICAL_PROFILE";
        request.profile_version = "v2";
        request.profile_fingerprint_sha256 = "p".repeat(64);
        request.master_menu_key = "LANZHOU_CHAIN_MASTER_MENU";
        request.master_menu_version = "v1";
        request.master_menu_fingerprint_sha256 = "m".repeat(64);
        request.validation_status = "PENDING";
        request.actor_user_id = 20L;
        request.created_at = LocalDateTime.now();
        request.updated_at = request.created_at;
        return request;
    }

    private OwnerStoreProvisioningSuccessEvidence successEvidence(String validationStatus) {
        return new OwnerStoreProvisioningSuccessEvidence(
            99L,
            10L,
            123L,
            "ST_DENIS_CANONICAL_PROFILE",
            "v2",
            "p".repeat(64),
            "LANZHOU_CHAIN_MASTER_MENU",
            "v1",
            "m".repeat(64),
            validationStatus,
            new OwnerStoreProvisioningCounts(5, 6, 39, 380, 1, 5, 1),
            "PHASE_B_STORE_PROVISIONED"
        );
    }
}
