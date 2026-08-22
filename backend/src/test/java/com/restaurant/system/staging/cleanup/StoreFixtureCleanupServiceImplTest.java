package com.restaurant.system.staging.cleanup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.owner.profile.StoreProfileCanonicalJson;
import com.restaurant.system.owner.provisioning.PhaseBProvisioningRuntimeGate;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class StoreFixtureCleanupServiceImplTest {

    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final PhaseBProvisioningRuntimeGate runtimeGate = mock(PhaseBProvisioningRuntimeGate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private StoreFixtureCleanupServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StoreFixtureCleanupServiceImpl(storeRepository, jdbcTemplate, runtimeGate, objectMapper);
    }

    @Test
    void replaysCompletedLedgerWithoutRunningPreflightOrDeletes() throws Exception {
        Store store = phaseBStore(9L, "CHINATOWN");
        doReturn(List.of(store)).when(storeRepository).findAllByIdInForUpdateOrderByIdAsc(List.of(9L));
        doReturn(null).when(jdbcTemplate).queryForObject(anyString(), any(RowMapper.class), eq("same-key"));
        StoreFixtureCleanupResponse completed = new StoreFixtureCleanupResponse();
        completed.organization_id = 1L;
        completed.status = "EXECUTED";
        completed.requested_store_ids = List.of(9L);
        doReturn(List.of(Map.of(
            "request_fingerprint", StoreProfileCanonicalJson.sha256Canonical(
                "{\"store_ids\":[9],\"approved_owner_manual_store_ids\":[9]}"
            ),
            "status", "COMPLETED",
            "result_json", objectMapper.writeValueAsString(completed)
        ))).when(jdbcTemplate).queryForList(anyString(), any(Object[].class));

        StoreFixtureCleanupRequest request = new StoreFixtureCleanupRequest();
        request.store_ids = List.of(9L);
        request.approved_owner_manual_store_ids = List.of(9L);
        request.dry_run = false;

        StoreFixtureCleanupResponse response = service.cleanup(
            new AuthenticatedUser(7L, 1L, 1L, "owner", "Owner", "OWNER"),
            1L,
            "same-key",
            request
        );

        assertEquals("REPLAYED", response.status);
        org.junit.jupiter.api.Assertions.assertTrue(response.replayed);
        verify(runtimeGate).requireEnabled();
    }

    @Test
    void changedRequestFingerprintIsRejectedBeforeAnyDelete() {
        Store store = phaseBStore(9L, "CHINATOWN");
        doReturn(List.of(store)).when(storeRepository).findAllByIdInForUpdateOrderByIdAsc(List.of(9L));
        doReturn(null).when(jdbcTemplate).queryForObject(anyString(), any(RowMapper.class), eq("same-key"));
        doReturn(List.of(Map.of(
            "request_fingerprint", "different" ,
            "status", "COMPLETED",
            "result_json", "{}"
        ))).when(jdbcTemplate).queryForList(anyString(), any(Object[].class));

        StoreFixtureCleanupRequest request = new StoreFixtureCleanupRequest();
        request.store_ids = List.of(9L);
        request.approved_owner_manual_store_ids = List.of(9L);
        request.dry_run = false;

        assertThrows(RuntimeException.class, () -> service.cleanup(
            new AuthenticatedUser(7L, 1L, 1L, "owner", "Owner", "OWNER"),
            1L,
            "same-key",
            request
        ));
    }

    @Test
    void protectedSourceStoreIsRejectedBeforeJdbcMutation() {
        Store store = phaseBStore(1L, "STG005_SRC_20260809_R01");
        doReturn(List.of(store)).when(storeRepository).findAllByIdInForUpdateOrderByIdAsc(List.of(1L));
        StoreFixtureCleanupRequest request = new StoreFixtureCleanupRequest();
        request.store_ids = List.of(1L);
        request.dry_run = true;

        assertThrows(RuntimeException.class, () -> service.cleanup(
            new AuthenticatedUser(7L, 1L, 1L, "owner", "Owner", "OWNER"),
            1L,
            null,
            request
        ));
        verifyNoInteractions(jdbcTemplate);
    }

    private static Store phaseBStore(Long id, String code) {
        Store store = new Store();
        store.id = id;
        store.code = code;
        store.organization_id = 1L;
        store.store_kind = id == 1L ? "BUSINESS" : "VALIDATION_FIXTURE";
        store.provisioning_source = id == 1L ? "LEGACY_EXISTING_STORE" : "PHASE_B_OWNER_PROVISIONING";
        store.status = id == 1L ? "active" : "inactive";
        return store;
    }
}
