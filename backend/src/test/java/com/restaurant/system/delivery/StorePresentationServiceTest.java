package com.restaurant.system.delivery;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.auth.ForbiddenException;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.delivery.StorePresentationService.ReceiptSettings;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

/** Configuration persistence tests with synthetic stores; controller owns authorization. */
class StorePresentationServiceTest {
    private static final long STORE = 17L;
    private static final String ACTIVE_STATION_QUERY =
        "select count(*) from stations where store_id=? and code=? and is_active=true";

    private final StoreRepository stores = mock(StoreRepository.class);
    private final MenuRevisionService revisions = mock(MenuRevisionService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final Store store = new Store();
    private StorePresentationService service;

    @BeforeEach
    void setUp() {
        store.id = STORE;
        store.organization_id = 91L;
        store.name = "Synthetic restaurant";
        when(stores.findById(STORE)).thenReturn(Optional.of(store));
        service = new StorePresentationService(stores, revisions, mapper, jdbc);
    }

    @Test
    void legacyNullPresentationLeavesReceiptExtensionDisabledWithoutWriting() {
        assertTrue(service.publicView(STORE).isEmpty());
        ReceiptSettings settings = service.receipts(STORE);
        assertFalse(settings.enabled);
        assertEquals("zh-CN", settings.default_language);
        assertTrue(settings.kitchen_station_codes.isEmpty());
        assertEquals(0L, settings.revision);
        verify(stores, never()).save(any(Store.class));
        verifyNoInteractions(jdbc, revisions);
    }

    @Test
    void uiSupportsOnlyTheThreeConfiguredLocales() {
        for (String locale : List.of("zh-CN", "en", "fr-CA")) {
            assertEquals(locale, service.save(STORE, Map.of("default_locale", locale)).get("default_locale"));
        }
    }

    @Test
    void unsupportedUiLocaleIsRejectedBeforePersistence() {
        for (String locale : List.of("fr", "fr-FR", "en-CA", "es")) {
            assertCode("LOCALE_INVALID", () -> service.save(STORE, Map.of("default_locale", locale)));
        }
        verify(stores, never()).save(any(Store.class));
    }

    @Test
    void receiptLocalesAcceptThreeLanguagesAndNullTicketFallbacks() {
        long revision = 0L;
        for (String locale : List.of("zh-CN", "en", "fr-CA")) {
            ReceiptSettings request = receipts();
            request.default_language = locale;
            request.kitchen_language = locale;
            request.frontdesk_language = locale;
            ReceiptSettings saved = service.saveReceipts(STORE, request);
            assertEquals(locale, saved.default_language);
            assertEquals(locale, service.receipts(STORE).kitchen_language);
            assertEquals(++revision, saved.revision);
        }
        ReceiptSettings fallback = receipts();
        assertDoesNotThrow(() -> service.saveReceipts(STORE, fallback));
        assertNull(service.receipts(STORE).kitchen_language);
        assertNull(service.receipts(STORE).frontdesk_language);
    }

    @Test
    void unsupportedDefaultOrPerTicketReceiptLocaleIsRejected() {
        for (String locale : List.of("fr", "fr-FR", "en-CA", "es")) {
            ReceiptSettings badDefault = receipts();
            badDefault.default_language = locale;
            assertCode("RECEIPT_LANGUAGE_INVALID", () -> service.saveReceipts(STORE, badDefault));
            ReceiptSettings badKitchen = receipts();
            badKitchen.kitchen_language = locale;
            assertCode("RECEIPT_LANGUAGE_INVALID", () -> service.saveReceipts(STORE, badKitchen));
            ReceiptSettings badFrontdesk = receipts();
            badFrontdesk.frontdesk_language = locale;
            assertCode("RECEIPT_LANGUAGE_INVALID", () -> service.saveReceipts(STORE, badFrontdesk));
        }
        verify(stores, never()).save(any(Store.class));
    }

    @Test
    void stationEligibilityQueryUsesRequestedStoreAndActiveStationPredicate() {
        when(jdbc.queryForObject(ACTIVE_STATION_QUERY, Integer.class, STORE, "SYNTHETIC_KITCHEN")).thenReturn(1);
        ReceiptSettings request = receipts();
        request.enabled = true;
        request.kitchen_station_codes = List.of("SYNTHETIC_KITCHEN");
        assertEquals(List.of("SYNTHETIC_KITCHEN"), service.saveReceipts(STORE, request).kitchen_station_codes);
        verify(jdbc).queryForObject(ACTIVE_STATION_QUERY, Integer.class, STORE, "SYNTHETIC_KITCHEN");
        verify(revisions).lockStoresInOrder(List.of(STORE));
        verify(stores).save(store);
    }

    @Test
    void foreignOrInactiveStationIsRejectedWithoutWriting() {
        when(jdbc.queryForObject(eq(ACTIVE_STATION_QUERY), eq(Integer.class), eq(STORE), anyString())).thenReturn(0);
        for (String station : List.of("OTHER_STORE_STATION", "INACTIVE_STATION")) {
            ReceiptSettings request = receipts();
            request.enabled = true;
            request.kitchen_station_codes = List.of(station);
            assertCode("RECEIPT_STATION_INVALID", () -> service.saveReceipts(STORE, request));
            verify(jdbc).queryForObject(ACTIVE_STATION_QUERY, Integer.class, STORE, station);
        }
        verify(stores, never()).save(any(Store.class));
    }

    @Test
    void duplicateOrMalformedStationCodesAreRejectedBeforeLookup() {
        ReceiptSettings repeated = receipts();
        repeated.kitchen_station_codes = List.of("SYNTHETIC_KITCHEN", "SYNTHETIC_KITCHEN");
        assertCode("RECEIPT_STATION_INVALID", () -> service.saveReceipts(STORE, repeated));
        ReceiptSettings malformed = receipts();
        malformed.kitchen_station_codes = List.of("../foreign");
        assertCode("RECEIPT_STATION_INVALID", () -> service.saveReceipts(STORE, malformed));
        verifyNoInteractions(jdbc);
        verify(stores, never()).save(any(Store.class));
    }

    @Test
    void savingUiLanguageAndBrandPreservesExistingReceiptConfiguration() throws Exception {
        store.presentation_json = mapper.writeValueAsString(Map.of(
            "display_name", "Existing synthetic name", "default_locale", "en",
            "receipts", Map.of("enabled", true, "default_language", "zh-CN", "kitchen_language", "en",
                "frontdesk_language", "fr-CA", "revision", 7)));
        JsonNode beforeReceipts = mapper.readTree(store.presentation_json).get("receipts");
        Map<String, Object> response = service.save(STORE,
            Map.of("display_name", "Updated synthetic name", "default_locale", "fr-CA"));
        assertEquals(beforeReceipts, mapper.readTree(store.presentation_json).get("receipts"));
        assertEquals("Updated synthetic name", response.get("display_name"));
        assertEquals("fr-CA", response.get("default_locale"));
        assertFalse(response.containsKey("receipts"));
        verify(revisions).lockStoresInOrder(List.of(STORE));
    }

    @Test
    void savingReceiptsPreservesBrandUiLocaleAndNoticeAndUsesServerRevision() throws Exception {
        Map<String, Object> brand = Map.of("display_name", "Synthetic restaurant", "primary_color", "#123456",
            "default_locale", "fr-CA", "notice", Map.of("en", "Synthetic notice"));
        var initial = new java.util.LinkedHashMap<String, Object>(brand);
        initial.put("receipts", Map.of("enabled", false, "default_language", "en", "revision", 7));
        store.presentation_json = mapper.writeValueAsString(initial);
        ReceiptSettings request = receipts();
        request.default_language = "zh-CN";
        request.frontdesk_language = "en";
        request.revision = 999L;
        ReceiptSettings saved = service.saveReceipts(STORE, request);
        assertEquals(brand, service.publicView(STORE));
        assertEquals(8L, saved.revision);
        assertEquals("zh-CN", service.receipts(STORE).default_language);
        assertEquals("en", service.receipts(STORE).frontdesk_language);
    }

    @Test
    void uiEndpointCannotWriteReceiptSettingsThroughPresentationFields() {
        assertCode("PRESENTATION_FIELD_INVALID", () -> service.save(STORE,
            Map.of("receipts", Map.of("enabled", true, "default_language", "en"))));
        verify(stores, never()).save(any(Store.class));
    }

    @Test
    void controllerRejectsUnauthorizedStoreBeforeAnyServiceReadOrWrite() {
        AuthorizationService auth = mock(AuthorizationService.class);
        StorePresentationService scoped = mock(StorePresentationService.class);
        StorePresentationController controller = new StorePresentationController(scoped, auth);
        doThrow(new ForbiddenException("synthetic foreign store"))
            .when(auth).requireManagerOrOwnerForStore(STORE, Capability.ADMIN_MENU_MANAGE);
        doThrow(new ForbiddenException("synthetic foreign store"))
            .when(auth).requireManagerOrOwnerForStore(STORE, Capability.ADMIN_PRINTING_MANAGE);
        assertThrows(ForbiddenException.class, () -> controller.get(STORE));
        assertThrows(ForbiddenException.class, () -> controller.save(STORE, Map.of("display_name", "Forbidden")));
        assertThrows(ForbiddenException.class, () -> controller.receipts(STORE));
        assertThrows(ForbiddenException.class, () -> controller.saveReceipts(STORE, receipts()));
        verifyNoInteractions(scoped);
    }

    @Test
    void controllerAuthorizesExactStoreAndCapabilityBeforeDelegatingEachEndpoint() {
        AuthorizationService auth = mock(AuthorizationService.class);
        StorePresentationService scoped = mock(StorePresentationService.class);
        StorePresentationController controller = new StorePresentationController(scoped, auth);
        Map<String, Object> input = Map.of("display_name", "Synthetic restaurant");
        ReceiptSettings request = receipts();
        controller.get(STORE);
        controller.save(STORE, input);
        controller.receipts(STORE);
        controller.saveReceipts(STORE, request);
        InOrder calls = inOrder(auth, scoped);
        calls.verify(auth).requireManagerOrOwnerForStore(STORE, Capability.ADMIN_MENU_MANAGE);
        calls.verify(scoped).publicView(STORE);
        calls.verify(auth).requireManagerOrOwnerForStore(STORE, Capability.ADMIN_MENU_MANAGE);
        calls.verify(scoped).save(STORE, input);
        calls.verify(auth).requireManagerOrOwnerForStore(STORE, Capability.ADMIN_PRINTING_MANAGE);
        calls.verify(scoped).receipts(STORE);
        calls.verify(auth).requireManagerOrOwnerForStore(STORE, Capability.ADMIN_PRINTING_MANAGE);
        calls.verify(scoped).saveReceipts(STORE, request);
        verifyNoMoreInteractions(scoped);
    }

    private ReceiptSettings receipts() {
        return new ReceiptSettings();
    }

    private void assertCode(String expected, Runnable action) {
        BusinessException error = assertThrows(BusinessException.class, action::run);
        assertTrue(error.getMessage().startsWith(expected), error.getMessage());
    }
}
