package com.restaurant.system.owner.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.auth.entity.UserCredential;
import com.restaurant.system.auth.repository.UserCredentialRepository;
import com.restaurant.system.auth.service.PasswordService;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.ForbiddenException;
import com.restaurant.system.common.auth.OwnerOrganizationAuthorizationService;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.owner.dto.OwnerStoreOnboardingRequest;
import com.restaurant.system.owner.dto.OwnerStoreOnboardingResponse;
import com.restaurant.system.owner.dto.OwnerStoreOnboardingStaffRequest;
import com.restaurant.system.owner.exception.OwnerStoreOnboardingException;
import com.restaurant.system.owner.service.OnboardingStaffProvisioningService;
import com.restaurant.system.owner.service.ProvisionedStoreStaff;
import com.restaurant.system.platform.repository.OwnerStoreOnboardingRequestRepository;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.entity.User;
import com.restaurant.system.user.repository.StoreRepository;
import com.restaurant.system.user.repository.UserRepository;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class OwnerStoreOnboardingServiceImplTest {

    private static final long ORGANIZATION_ID = 100L;
    private static final long SOURCE_STORE_ID = 101L;
    private static final long TARGET_STORE_ID = 102L;
    private static final long ONBOARDING_REQUEST_ID = 103L;
    private static final long USER_ID = 104L;
    private static final String IDEMPOTENCY_KEY = "onboarding-test-key";
    private static final String LOGIN_IDENTIFIER = "onboarding-test-user";

    @Mock
    private OwnerOrganizationAuthorizationService ownerOrganizationAuthorizationService;
    @Mock
    private OwnerStoreOnboardingRequestRepository onboardingRequestRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserCredentialRepository userCredentialRepository;
    @Mock
    private PasswordService passwordService;
    @Mock
    private OnboardingStaffProvisioningService staffProvisioningService;
    @Captor
    private ArgumentCaptor<Store> storeCaptor;
    @Captor
    private ArgumentCaptor<String> fingerprintCaptor;

    private OwnerStoreOnboardingServiceImpl service;
    private AuthenticatedUser owner;
    private com.restaurant.system.platform.entity.OwnerStoreOnboardingRequest persistedRequest;
    private Store createdStore;
    private String syntheticPassword;
    private String differentSyntheticPassword;

    @BeforeEach
    void setUp() {
        service = new OwnerStoreOnboardingServiceImpl(
            ownerOrganizationAuthorizationService,
            onboardingRequestRepository,
            storeRepository,
            userRepository,
            userCredentialRepository,
            passwordService,
            staffProvisioningService
        );
        owner = new AuthenticatedUser(1L, SOURCE_STORE_ID, 1L, "owner-test", "Owner Test", "OWNER");
        persistedRequest = processingRequest();
        createdStore = store(TARGET_STORE_ID, ORGANIZATION_ID, "TARGET");
        syntheticPassword = "test-" + UUID.randomUUID();
        differentSyntheticPassword = "test-" + UUID.randomUUID();
    }

    @Test
    void sameKeyAndSameRequestReplaysExactlyOneProvisionedStoreWithoutPasswordLeakage() throws Exception {
        configureFirstSuccessfulSubmission(1, 0);
        configureReplayLookup(syntheticPassword, true);

        OwnerStoreOnboardingResponse first = service.onboard(
            ORGANIZATION_ID,
            IDEMPOTENCY_KEY,
            request(syntheticPassword, "Target Store"),
            owner
        );
        OwnerStoreOnboardingResponse replay = service.onboard(
            ORGANIZATION_ID,
            IDEMPOTENCY_KEY,
            request(syntheticPassword, "Target Store"),
            owner
        );

        verify(storeRepository, times(1)).save(storeCaptor.capture());
        Store savedStore = storeCaptor.getValue();
        assertEquals(ORGANIZATION_ID, savedStore.organization_id);
        assertEquals("TARGET", savedStore.code);
        assertEquals("inactive", savedStore.status);
        assertFalse(savedStore.enable_bar_kitchen_tasks);
        assertFalse(savedStore.printing_enabled);
        assertEquals("DISABLED", savedStore.printing_mode);
        assertFalse(first.replayed);
        assertTrue(replay.replayed);
        assertEquals(first.store_id, replay.store_id);
        assertEquals(first.onboarding_request_id, replay.onboarding_request_id);
        verify(staffProvisioningService, times(1)).provision(any());
        verify(onboardingRequestRepository, times(1)).save(persistedRequest);
        verify(onboardingRequestRepository, times(2)).insertIfAbsent(
            eq(ORGANIZATION_ID),
            eq(IDEMPOTENCY_KEY),
            fingerprintCaptor.capture(),
            any(LocalDateTime.class)
        );
        assertThat(fingerprintCaptor.getAllValues())
            .allSatisfy(fingerprint -> {
                assertThat(fingerprint).hasSize(64);
                assertThat(fingerprint).doesNotContain(syntheticPassword);
            });
        assertThat(new ObjectMapper().writeValueAsString(first)).doesNotContain(syntheticPassword);
    }

    @Test
    void sameKeyWithChangedStructuralContentReturnsConflictWithoutAnotherStore() {
        configureFirstSuccessfulSubmission(1, 0);

        service.onboard(ORGANIZATION_ID, IDEMPOTENCY_KEY, request(syntheticPassword, "Target Store"), owner);

        OwnerStoreOnboardingException exception = assertThrows(
            OwnerStoreOnboardingException.class,
            () -> service.onboard(
                ORGANIZATION_ID,
                IDEMPOTENCY_KEY,
                request(syntheticPassword, "Different Target Store"),
                owner
            )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("IDEMPOTENCY_CONFLICT", exception.getErrorCode());
        verify(storeRepository, times(1)).save(any(Store.class));
        verify(staffProvisioningService, times(1)).provision(any());
    }

    @Test
    void sameKeyWithDifferentStaffPasswordReturnsConflictAfterCompletedResult() {
        configureFirstSuccessfulSubmission(1, 0);
        configureReplayLookup(differentSyntheticPassword, false);

        service.onboard(ORGANIZATION_ID, IDEMPOTENCY_KEY, request(syntheticPassword, "Target Store"), owner);

        OwnerStoreOnboardingException exception = assertThrows(
            OwnerStoreOnboardingException.class,
            () -> service.onboard(
                ORGANIZATION_ID,
                IDEMPOTENCY_KEY,
                request(differentSyntheticPassword, "Target Store"),
                owner
            )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("IDEMPOTENCY_CONFLICT", exception.getErrorCode());
        assertThat(exception.getMessage())
            .doesNotContain(syntheticPassword)
            .doesNotContain(differentSyntheticPassword);
        verify(storeRepository, times(1)).save(any(Store.class));
        verify(staffProvisioningService, times(1)).provision(any());
    }

    @Test
    void concurrentContenderWithExistingProcessingRequestDoesNotCreateAnotherStore() {
        persistedRequest.status = "PROCESSING";
        when(onboardingRequestRepository.insertIfAbsent(eq(ORGANIZATION_ID), eq(IDEMPOTENCY_KEY), anyString(), any(LocalDateTime.class)))
            .thenAnswer(invocation -> {
                persistedRequest.requestFingerprint = invocation.getArgument(2);
                return 0;
            });
        when(onboardingRequestRepository.findForUpdate(ORGANIZATION_ID, IDEMPOTENCY_KEY))
            .thenReturn(Optional.of(persistedRequest));

        OwnerStoreOnboardingException exception = assertThrows(
            OwnerStoreOnboardingException.class,
            () -> service.onboard(ORGANIZATION_ID, IDEMPOTENCY_KEY, request(syntheticPassword, "Target Store"), owner)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("ONBOARDING_REQUEST_IN_PROGRESS", exception.getErrorCode());
        verify(storeRepository, never()).save(any(Store.class));
        verifyNoInteractions(staffProvisioningService);
    }

    @Test
    void crossOrganizationSourceIsRejectedBeforeAnyIdempotencyWrite() {
        when(ownerOrganizationAuthorizationService.requireSourceStoreInOrganization(owner, ORGANIZATION_ID, SOURCE_STORE_ID))
            .thenThrow(new ForbiddenException("Access denied. Source store belongs to a different organization"));

        assertThrows(
            ForbiddenException.class,
            () -> service.onboard(ORGANIZATION_ID, IDEMPOTENCY_KEY, request(syntheticPassword, "Target Store"), owner)
        );

        verifyNoInteractions(onboardingRequestRepository, staffProvisioningService);
        verify(storeRepository, never()).save(any(Store.class));
    }

    @Test
    void provisioningFailureEscapesWithoutCompletingTheIdempotencyRecordAndUsesTransactionalBoundary() throws Exception {
        configureFirstSubmission(1);
        doThrow(new BusinessException("Synthetic provisioning failure"))
            .when(staffProvisioningService)
            .provision(any());

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.onboard(ORGANIZATION_ID, IDEMPOTENCY_KEY, request(syntheticPassword, "Target Store"), owner)
        );

        assertThat(exception.getMessage()).doesNotContain(syntheticPassword);
        verify(onboardingRequestRepository, never()).save(any());
        Method onboard = OwnerStoreOnboardingServiceImpl.class.getMethod(
            "onboard",
            Long.class,
            String.class,
            OwnerStoreOnboardingRequest.class,
            AuthenticatedUser.class
        );
        assertThat(onboard.getAnnotation(Transactional.class)).isNotNull();
    }

    private void configureFirstSuccessfulSubmission(int... insertResults) {
        configureFirstSubmission(insertResults);
        when(staffProvisioningService.provision(any())).thenReturn(
            new ProvisionedStoreStaff(USER_ID, TARGET_STORE_ID, LOGIN_IDENTIFIER, "FRONTDESK")
        );
        when(onboardingRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void configureFirstSubmission(int... insertResults) {
        when(ownerOrganizationAuthorizationService.requireSourceStoreInOrganization(owner, ORGANIZATION_ID, SOURCE_STORE_ID))
            .thenReturn(store(SOURCE_STORE_ID, ORGANIZATION_ID, "SOURCE"));
        AtomicInteger invocationIndex = new AtomicInteger();
        when(onboardingRequestRepository.insertIfAbsent(eq(ORGANIZATION_ID), eq(IDEMPOTENCY_KEY), anyString(), any(LocalDateTime.class)))
            .thenAnswer(invocation -> {
                if (persistedRequest.requestFingerprint == null) {
                    persistedRequest.requestFingerprint = invocation.getArgument(2);
                }
                int index = Math.min(invocationIndex.getAndIncrement(), insertResults.length - 1);
                return insertResults[index];
            });
        when(onboardingRequestRepository.findForUpdate(ORGANIZATION_ID, IDEMPOTENCY_KEY))
            .thenReturn(Optional.of(persistedRequest));
        when(storeRepository.findAllByOrganizationIdOrderByIdAsc(ORGANIZATION_ID))
            .thenReturn(List.of(store(SOURCE_STORE_ID, ORGANIZATION_ID, "SOURCE")));
        when(storeRepository.save(any(Store.class))).thenAnswer(invocation -> {
            Store store = invocation.getArgument(0);
            store.id = TARGET_STORE_ID;
            createdStore = store;
            return store;
        });
    }

    private void configureReplayLookup(String replayPassword, boolean matches) {
        when(storeRepository.findById(TARGET_STORE_ID)).thenReturn(Optional.of(createdStore));
        User user = new User();
        user.setId(USER_ID);
        user.setStore_id(TARGET_STORE_ID);
        user.setUsername(LOGIN_IDENTIFIER);
        when(userRepository.findFirstByUsernameIgnoreCase(LOGIN_IDENTIFIER)).thenReturn(Optional.of(user));
        UserCredential credential = new UserCredential();
        credential.userId = USER_ID;
        credential.loginIdentifier = LOGIN_IDENTIFIER;
        credential.passwordHash = "bcrypt-hash-only";
        when(userCredentialRepository.findFirstByLoginIdentifierIgnoreCase(LOGIN_IDENTIFIER)).thenReturn(Optional.of(credential));
        when(passwordService.matches(replayPassword, credential.passwordHash)).thenReturn(matches);
    }

    private OwnerStoreOnboardingRequest request(String password, String storeName) {
        OwnerStoreOnboardingStaffRequest staff = new OwnerStoreOnboardingStaffRequest();
        staff.login_identifier = LOGIN_IDENTIFIER;
        staff.full_name = "Synthetic Staff";
        staff.role_code = "FRONTDESK";
        staff.initial_password = password;

        OwnerStoreOnboardingRequest request = new OwnerStoreOnboardingRequest();
        request.source_store_id = SOURCE_STORE_ID;
        request.store_name = storeName;
        request.store_code = "target";
        request.staff = List.of(staff);
        return request;
    }

    private com.restaurant.system.platform.entity.OwnerStoreOnboardingRequest processingRequest() {
        com.restaurant.system.platform.entity.OwnerStoreOnboardingRequest request =
            new com.restaurant.system.platform.entity.OwnerStoreOnboardingRequest();
        request.id = ONBOARDING_REQUEST_ID;
        request.organizationId = ORGANIZATION_ID;
        request.idempotencyKey = IDEMPOTENCY_KEY;
        request.status = "PROCESSING";
        return request;
    }

    private Store store(Long id, Long organizationId, String code) {
        Store store = new Store();
        store.id = id;
        store.organization_id = organizationId;
        store.code = code;
        store.status = "active";
        return store;
    }
}
