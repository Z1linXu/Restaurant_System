package com.restaurant.system.owner.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurant.system.owner.exception.OwnerStoreMenuCloneException;
import com.restaurant.system.owner.menu.ChinatownMenuCloneProfile;
import com.restaurant.system.owner.menu.StoreMenuCloneProfileDescriptor;
import com.restaurant.system.owner.menu.StoreMenuCloneProfileRegistry;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneFailureEvidence;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneReservation;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneReservationCommand;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneSuccessEvidence;
import com.restaurant.system.platform.entity.OwnerStoreMenuCloneRequest;
import com.restaurant.system.platform.repository.OwnerStoreMenuCloneRequestRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OwnerStoreMenuCloneRequestCoordinatorImplTest {

    private static final Long ORGANIZATION_ID = 10L;
    private static final Long TARGET_STORE_ID = 20L;
    private static final Long ACTOR_USER_ID = 30L;
    private static final String IDEMPOTENCY_KEY = "al003-request-key";

    @Mock
    private OwnerStoreMenuCloneRequestRepository requestRepository;

    private OwnerStoreMenuCloneFingerprint fingerprintService;
    private OwnerStoreMenuCloneRequestCoordinatorImpl coordinator;
    private StoreMenuCloneProfileRegistry profileRegistry;

    @BeforeEach
    void setUp() {
        profileRegistry = new StoreMenuCloneProfileRegistry(List.of(new ChinatownMenuCloneProfile()));
        fingerprintService = new OwnerStoreMenuCloneFingerprint(profileRegistry);
        coordinator = new OwnerStoreMenuCloneRequestCoordinatorImpl(
            requestRepository,
            fingerprintService,
            profileRegistry
        );
    }

    @Test
    void firstReservationCreatesProcessingRequestWithoutReturningTheRawKey() {
        OwnerStoreMenuCloneReservationCommand command = command();
        OwnerStoreMenuCloneRequest request = processingRequest(command, fingerprintService.fingerprint(command));
        when(requestRepository.insertIfAbsent(
            anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyString(), anyLong(), any(LocalDateTime.class)
        )).thenReturn(1);
        when(requestRepository.findForUpdate(
            ORGANIZATION_ID,
            ChinatownMenuCloneProfile.SOURCE_STORE_ID,
            TARGET_STORE_ID,
            IDEMPOTENCY_KEY
        )).thenReturn(Optional.of(request));

        OwnerStoreMenuCloneReservation reservation = coordinator.reserve(command);

        assertThat(reservation.status()).isEqualTo("PROCESSING");
        assertThat(reservation.replayed()).isFalse();
        assertThat(reservation.toString()).doesNotContain(IDEMPOTENCY_KEY);
        assertThat(command.toString()).doesNotContain(IDEMPOTENCY_KEY);
    }

    @Test
    void completedSameFingerprintReturnsReplayWithoutInsertOwnership() {
        OwnerStoreMenuCloneReservationCommand command = command();
        OwnerStoreMenuCloneRequest request = processingRequest(command, fingerprintService.fingerprint(command));
        request.status = "COMPLETED";
        request.resultCode = "MENU_CLONE_COMPLETED";
        request.sourceMenuRevision = 7L;
        request.targetRevisionBefore = 1L;
        request.targetRevisionAfter = 2L;
        when(requestRepository.insertIfAbsent(
            anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyString(), anyLong(), any(LocalDateTime.class)
        )).thenReturn(0);
        when(requestRepository.findForUpdate(
            ORGANIZATION_ID,
            ChinatownMenuCloneProfile.SOURCE_STORE_ID,
            TARGET_STORE_ID,
            IDEMPOTENCY_KEY
        )).thenReturn(Optional.of(request));

        OwnerStoreMenuCloneReservation reservation = coordinator.reserve(command);

        assertThat(reservation.replayed()).isTrue();
        assertThat(reservation.requestId()).isEqualTo(request.id);
        assertThat(reservation.targetRevisionAfter()).isEqualTo(2L);
        assertThat(reservation.resultCode()).isEqualTo("MENU_CLONE_COMPLETED");
    }

    @Test
    void sameScopeAndKeyWithDifferentFingerprintConflicts() {
        OwnerStoreMenuCloneReservationCommand command = command();
        OwnerStoreMenuCloneRequest request = processingRequest(command, "f".repeat(64));
        when(requestRepository.insertIfAbsent(
            anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyString(), anyLong(), any(LocalDateTime.class)
        )).thenReturn(0);
        when(requestRepository.findForUpdate(
            ORGANIZATION_ID,
            ChinatownMenuCloneProfile.SOURCE_STORE_ID,
            TARGET_STORE_ID,
            IDEMPOTENCY_KEY
        )).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> coordinator.reserve(command))
            .isInstanceOfSatisfying(
                OwnerStoreMenuCloneException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo("IDEMPOTENCY_CONFLICT")
            )
            .hasMessageNotContaining(IDEMPOTENCY_KEY);
    }

    @Test
    void existingProcessingRequestReturnsInProgress() {
        OwnerStoreMenuCloneReservationCommand command = command();
        OwnerStoreMenuCloneRequest request = processingRequest(command, fingerprintService.fingerprint(command));
        when(requestRepository.insertIfAbsent(
            anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyString(), anyLong(), any(LocalDateTime.class)
        )).thenReturn(0);
        when(requestRepository.findForUpdate(
            ORGANIZATION_ID,
            ChinatownMenuCloneProfile.SOURCE_STORE_ID,
            TARGET_STORE_ID,
            IDEMPOTENCY_KEY
        )).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> coordinator.reserve(command))
            .isInstanceOfSatisfying(
                OwnerStoreMenuCloneException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo("MENU_CLONE_IN_PROGRESS")
            );
    }

    @Test
    void failedRequestIsTerminalForTheSameIdempotencyKey() {
        OwnerStoreMenuCloneReservationCommand command = command();
        OwnerStoreMenuCloneRequest request = processingRequest(command, fingerprintService.fingerprint(command));
        request.status = "FAILED";
        request.errorCode = "MENU_CLONE_FAILED";
        when(requestRepository.insertIfAbsent(
            anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyString(), anyLong(), any(LocalDateTime.class)
        )).thenReturn(0);
        when(requestRepository.findForUpdate(
            ORGANIZATION_ID,
            ChinatownMenuCloneProfile.SOURCE_STORE_ID,
            TARGET_STORE_ID,
            IDEMPOTENCY_KEY
        )).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> coordinator.reserve(command))
            .isInstanceOfSatisfying(
                OwnerStoreMenuCloneException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo("MENU_CLONE_RETRY_REQUIRES_VALIDATION")
            );
        verify(requestRepository, never()).save(any(OwnerStoreMenuCloneRequest.class));
    }

    @Test
    void revalidatedRetryUsesANewIdempotencyKeyAndCreatesANewReservation() {
        String newKey = "al003-revalidated-request-key";
        OwnerStoreMenuCloneReservationCommand command = command(newKey);
        OwnerStoreMenuCloneRequest request = processingRequest(command, fingerprintService.fingerprint(command));
        when(requestRepository.insertIfAbsent(
            anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyString(), anyLong(), any(LocalDateTime.class)
        )).thenReturn(1);
        when(requestRepository.findForUpdate(
            ORGANIZATION_ID,
            ChinatownMenuCloneProfile.SOURCE_STORE_ID,
            TARGET_STORE_ID,
            newKey
        )).thenReturn(Optional.of(request));

        OwnerStoreMenuCloneReservation reservation = coordinator.reserve(command);

        assertThat(reservation.status()).isEqualTo("PROCESSING");
        assertThat(reservation.replayed()).isFalse();
        assertThat(request.idempotencyKey).isEqualTo(newKey);
    }

    @Test
    void completionPersistsOnlyBoundedCountsRevisionsAndSafeCode() {
        OwnerStoreMenuCloneRequest request = processingRequest(command(), fingerprintService.fingerprint(command()));
        when(requestRepository.findByIdForUpdate(request.id)).thenReturn(Optional.of(request));
        when(requestRepository.save(request)).thenReturn(request);

        OwnerStoreMenuCloneReservation reservation = coordinator.complete(new OwnerStoreMenuCloneSuccessEvidence(
            request.id,
            12L,
            3L,
            4L,
            3,
            4,
            17,
            120,
            "unsafe result with spaces " + UUID.randomUUID()
        ));

        assertThat(reservation.status()).isEqualTo("COMPLETED");
        assertThat(request.resultCode).isEqualTo("MENU_CLONE_COMPLETED");
        assertThat(request.errorCode).isNull();
        assertThat(request.createdItemCount).isEqualTo(17);
        verify(requestRepository).save(request);
    }

    @Test
    void failurePersistsOnlySanitizedErrorCodeAndNeverOverwritesCompletedEvidence() {
        OwnerStoreMenuCloneRequest request = processingRequest(command(), fingerprintService.fingerprint(command()));
        when(requestRepository.findByIdForUpdate(request.id)).thenReturn(Optional.of(request));
        when(requestRepository.save(request)).thenReturn(request);

        String sensitive = "unsafe-detail-" + UUID.randomUUID();
        OwnerStoreMenuCloneReservation reservation = coordinator.fail(new OwnerStoreMenuCloneFailureEvidence(
            request.id,
            12L,
            3L,
            sensitive
        ));

        assertThat(reservation.status()).isEqualTo("FAILED");
        assertThat(request.errorCode).isEqualTo("MENU_CLONE_FAILED");
        assertThat(request.errorCode).doesNotContain(sensitive);
        assertThat(new OwnerStoreMenuCloneFailureEvidence(request.id, 12L, 3L, sensitive).toString())
            .doesNotContain(sensitive);
        assertThat(request.resultCode).isNull();

        request.status = "COMPLETED";
        assertThatThrownBy(() -> coordinator.fail(new OwnerStoreMenuCloneFailureEvidence(
            request.id,
            null,
            null,
            "MENU_CLONE_FAILED"
        ))).isInstanceOf(OwnerStoreMenuCloneException.class);
        verify(requestRepository, never()).delete(any(OwnerStoreMenuCloneRequest.class));
    }

    @Test
    void reviewedProfileAndScopeAreRequiredBeforeAnyReservationWrite() {
        OwnerStoreMenuCloneReservationCommand invalid = new OwnerStoreMenuCloneReservationCommand(
            ORGANIZATION_ID,
            2L,
            TARGET_STORE_ID,
            IDEMPOTENCY_KEY,
            ChinatownMenuCloneProfile.PROFILE_CODE,
            ACTOR_USER_ID
        );

        assertThatThrownBy(() -> coordinator.reserve(invalid))
            .isInstanceOfSatisfying(
                OwnerStoreMenuCloneException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo("MENU_CLONE_REQUEST_INVALID")
            );
        verify(requestRepository, never()).insertIfAbsent(
            anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyString(), anyLong(), any(LocalDateTime.class)
        );
    }

    @Test
    void sharedCoordinatorAcceptsAnotherReviewedProfileWithoutStoreSpecificBranching() {
        StoreMenuCloneProfileDescriptor genericProfile = profile(
            "GENERIC_STORE_PROFILE_V1",
            77L,
            "generic-profile-fingerprint-v1"
        );
        StoreMenuCloneProfileRegistry genericRegistry = new StoreMenuCloneProfileRegistry(List.of(genericProfile));
        OwnerStoreMenuCloneFingerprint genericFingerprint = new OwnerStoreMenuCloneFingerprint(genericRegistry);
        OwnerStoreMenuCloneRequestCoordinatorImpl genericCoordinator = new OwnerStoreMenuCloneRequestCoordinatorImpl(
            requestRepository,
            genericFingerprint,
            genericRegistry
        );
        OwnerStoreMenuCloneReservationCommand command = new OwnerStoreMenuCloneReservationCommand(
            ORGANIZATION_ID,
            77L,
            TARGET_STORE_ID,
            IDEMPOTENCY_KEY,
            "GENERIC_STORE_PROFILE_V1",
            ACTOR_USER_ID
        );
        OwnerStoreMenuCloneRequest request = processingRequest(command, genericFingerprint.fingerprint(command));
        when(requestRepository.insertIfAbsent(
            anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyString(), anyLong(), any(LocalDateTime.class)
        )).thenReturn(1);
        when(requestRepository.findForUpdate(
            ORGANIZATION_ID,
            77L,
            TARGET_STORE_ID,
            IDEMPOTENCY_KEY
        )).thenReturn(Optional.of(request));

        OwnerStoreMenuCloneReservation reservation = genericCoordinator.reserve(command);

        assertThat(reservation.status()).isEqualTo("PROCESSING");
        assertThat(reservation.sourceStoreId()).isEqualTo(77L);
    }

    @Test
    void profileFingerprintIsPartOfTheIdempotencyFingerprint() {
        OwnerStoreMenuCloneReservationCommand command = command();
        StoreMenuCloneProfileDescriptor changedProfile = profile(
            ChinatownMenuCloneProfile.PROFILE_CODE,
            ChinatownMenuCloneProfile.SOURCE_STORE_ID,
            "AL003_V1_CHANGED_PROFILE_CONTENT"
        );
        OwnerStoreMenuCloneFingerprint changedFingerprint = new OwnerStoreMenuCloneFingerprint(
            new StoreMenuCloneProfileRegistry(List.of(changedProfile))
        );

        assertThat(changedFingerprint.fingerprint(command)).isNotEqualTo(fingerprintService.fingerprint(command));
    }

    private StoreMenuCloneProfileDescriptor profile(String code, Long sourceStoreId, String fingerprint) {
        return new StoreMenuCloneProfileDescriptor() {
            @Override
            public String profileCode() {
                return code;
            }

            @Override
            public Long sourceStoreId() {
                return sourceStoreId;
            }

            @Override
            public String profileFingerprint() {
                return fingerprint;
            }
        };
    }

    private OwnerStoreMenuCloneReservationCommand command() {
        return command(IDEMPOTENCY_KEY);
    }

    private OwnerStoreMenuCloneReservationCommand command(String idempotencyKey) {
        return new OwnerStoreMenuCloneReservationCommand(
            ORGANIZATION_ID,
            ChinatownMenuCloneProfile.SOURCE_STORE_ID,
            TARGET_STORE_ID,
            idempotencyKey,
            ChinatownMenuCloneProfile.PROFILE_CODE,
            ACTOR_USER_ID
        );
    }

    private OwnerStoreMenuCloneRequest processingRequest(
        OwnerStoreMenuCloneReservationCommand command,
        String fingerprint
    ) {
        OwnerStoreMenuCloneRequest request = new OwnerStoreMenuCloneRequest();
        request.id = 901L;
        request.organizationId = command.organizationId();
        request.sourceStoreId = command.sourceStoreId();
        request.targetStoreId = command.targetStoreId();
        request.idempotencyKey = command.idempotencyKey();
        request.requestFingerprint = fingerprint;
        request.profileCode = command.profileCode();
        request.status = "PROCESSING";
        request.actorUserId = command.actorUserId();
        request.createdAt = LocalDateTime.now();
        request.updatedAt = request.createdAt;
        return request;
    }
}
