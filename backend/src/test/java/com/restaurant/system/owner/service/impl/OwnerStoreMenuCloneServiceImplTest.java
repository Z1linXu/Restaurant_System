package com.restaurant.system.owner.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.ForbiddenException;
import com.restaurant.system.common.auth.OwnerOrganizationAuthorizationService;
import com.restaurant.system.owner.dto.OwnerStoreMenuCloneRequest;
import com.restaurant.system.owner.exception.OwnerStoreMenuCloneException;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneRequestCoordinator;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneReservation;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneSuccessEvidence;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneTransactionResult;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneValidationResult;
import com.restaurant.system.owner.service.StoreMenuCloneTransactionService;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OwnerStoreMenuCloneServiceImplTest {

    private static final long ORGANIZATION_ID = 100L;
    private static final long SOURCE_STORE_ID = 10L;
    private static final long TARGET_STORE_ID = 20L;

    @Mock private OwnerOrganizationAuthorizationService ownerAuthorization;
    @Mock private StoreRepository storeRepository;
    @Mock private OwnerStoreMenuCloneRequestCoordinator requestCoordinator;
    @Mock private StoreMenuCloneTransactionService transactionService;
    @Captor private ArgumentCaptor<com.restaurant.system.owner.service.OwnerStoreMenuCloneFailureEvidence> failureCaptor;

    private OwnerStoreMenuCloneServiceImpl service;
    private AuthenticatedUser owner;

    @BeforeEach
    void setUp() {
        service = new OwnerStoreMenuCloneServiceImpl(ownerAuthorization, storeRepository, requestCoordinator, transactionService);
        owner = new AuthenticatedUser(1L, SOURCE_STORE_ID, 1L, "owner", "Owner", "OWNER");
    }

    private void stubTargetInOrganization() {
        Store target = new Store();
        target.id = TARGET_STORE_ID;
        target.organization_id = ORGANIZATION_ID;
        when(storeRepository.findById(TARGET_STORE_ID)).thenReturn(Optional.of(target));
    }

    @Test
    void validateAuthorizesThenUsesExistingReadOnlyPlannerWithoutReservation() {
        stubTargetInOrganization();
        when(transactionService.validate(any())).thenReturn(validation());

        var response = service.validateMenuClone(ORGANIZATION_ID, TARGET_STORE_ID, request(), owner);

        assertThat(response.valid).isTrue();
        assertThat(response.expected.items).isEqualTo(17);
        verify(ownerAuthorization).requireSourceStoreInOrganization(owner, ORGANIZATION_ID, SOURCE_STORE_ID);
        verify(transactionService).validate(any());
        verify(requestCoordinator, never()).reserve(any());
    }

    @Test
    void executeCompletedReplayDoesNotRunTheCloneTransactionAgain() {
        stubTargetInOrganization();
        when(requestCoordinator.reserve(any())).thenReturn(reservation(true, "COMPLETED", "MENU_CLONE_COMPLETED"));

        var response = service.cloneMenu(ORGANIZATION_ID, TARGET_STORE_ID, "same-key", request(), owner);

        assertThat(response.replayed).isTrue();
        assertThat(response.clone_request_id).isEqualTo(300L);
        verify(transactionService, never()).execute(any());
    }

    @Test
    void firstSuccessfulExecutionIsNotMarkedAsAReplay() {
        stubTargetInOrganization();
        when(requestCoordinator.reserve(any())).thenReturn(reservation(false, "PROCESSING", null));
        when(transactionService.execute(any())).thenReturn(new OwnerStoreMenuCloneTransactionResult(successEvidence()));

        var response = service.cloneMenu(ORGANIZATION_ID, TARGET_STORE_ID, "new-key", request(), owner);

        assertThat(response.replayed).isFalse();
        assertThat(response.status).isEqualTo("COMPLETED");
        assertThat(response.created.items).isEqualTo(17);
    }

    @Test
    void transactionFailureWritesOnlySanitizedTerminalFailureEvidence() {
        stubTargetInOrganization();
        when(requestCoordinator.reserve(any()))
            .thenReturn(reservation(false, "PROCESSING", null));
        OwnerStoreMenuCloneException failure = new OwnerStoreMenuCloneException(
            "TARGET_MENU_NOT_EMPTY", org.springframework.http.HttpStatus.CONFLICT, "Safe message"
        );
        when(transactionService.execute(any())).thenThrow(failure);

        assertThatThrownBy(() -> service.cloneMenu(ORGANIZATION_ID, TARGET_STORE_ID, "key", request(), owner))
            .isSameAs(failure);

        verify(requestCoordinator).fail(failureCaptor.capture());
        assertThat(failureCaptor.getValue().requestId()).isEqualTo(300L);
        assertThat(failureCaptor.getValue().errorCode()).isEqualTo("TARGET_MENU_NOT_EMPTY");
    }

    @Test
    void targetInAnotherOrganizationIsForbiddenBeforeReservation() {
        Store target = new Store();
        target.id = TARGET_STORE_ID;
        target.organization_id = 999L;
        when(storeRepository.findById(TARGET_STORE_ID)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.validateMenuClone(ORGANIZATION_ID, TARGET_STORE_ID, request(), owner))
            .isInstanceOf(OwnerStoreMenuCloneException.class)
            .hasMessageContaining("not available");
        verify(requestCoordinator, never()).reserve(any());
    }

    @Test
    void whitespaceProfileIsRejectedBeforeAuthorizationAndWrites() {
        OwnerStoreMenuCloneRequest request = request();
        request.profile_code = " CHINATOWN_MENU_2026_02_02";

        assertThatThrownBy(() -> service.validateMenuClone(ORGANIZATION_ID, TARGET_STORE_ID, request, owner))
            .isInstanceOf(OwnerStoreMenuCloneException.class)
            .hasMessageContaining("exact profile");
        verify(requestCoordinator, never()).reserve(any());
        verify(ownerAuthorization, never()).requireSourceStoreInOrganization(any(), any(), any());
    }

    private OwnerStoreMenuCloneRequest request() {
        OwnerStoreMenuCloneRequest request = new OwnerStoreMenuCloneRequest();
        request.source_store_id = SOURCE_STORE_ID;
        request.profile_code = "CHINATOWN_MENU_2026_02_02";
        return request;
    }

    private OwnerStoreMenuCloneValidationResult validation() {
        return new OwnerStoreMenuCloneValidationResult(
            true, "CHINATOWN_MENU_2026_02_02", 8L, 1L, 3, 4, 17, 22, List.of(), List.of(), List.of()
        );
    }

    private OwnerStoreMenuCloneReservation reservation(boolean replayed, String status, String resultCode) {
        return new OwnerStoreMenuCloneReservation(
            300L, ORGANIZATION_ID, SOURCE_STORE_ID, TARGET_STORE_ID,
            "CHINATOWN_MENU_2026_02_02", status, replayed,
            8L, 1L, 2L, 3, 4, 17, 22, resultCode, null
        );
    }

    private OwnerStoreMenuCloneSuccessEvidence successEvidence() {
        return new OwnerStoreMenuCloneSuccessEvidence(
            300L, ORGANIZATION_ID, SOURCE_STORE_ID, TARGET_STORE_ID,
            "CHINATOWN_MENU_2026_02_02", 8L, 1L, 2L, 3, 4, 17, 22, "MENU_CLONE_COMPLETED"
        );
    }
}
