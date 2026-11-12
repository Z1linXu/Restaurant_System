package com.restaurant.system.menu.pricing.bulk;

import static com.restaurant.system.menu.pricing.bulk.CategoryBulkPriceModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.menu.service.MenuRevisionService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class CategoryBulkPriceServiceTest {
    private CategoryBulkPriceRepository repository;
    private MenuRevisionService revisions;
    private CategoryBulkPriceService service;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-12T12:00:00Z"), ZoneOffset.UTC);
    private final AuthenticatedUser actor = new AuthenticatedUser(8L, 1L, 1L, "owner", "Owner", "OWNER");
    private AtomicReference<CategoryBulkPriceRepository.StoredPreview> stored;
    private AtomicLong revision;

    @BeforeEach void setUp() {
        repository = mock(CategoryBulkPriceRepository.class);
        revisions = mock(MenuRevisionService.class);
        service = new CategoryBulkPriceService(repository, revisions, json, clock);
        stored = new AtomicReference<>();
        revision = new AtomicLong(4);
        when(repository.storeExists(1L)).thenReturn(true);
        when(repository.bulkEnabled(1L)).thenReturn(true);
        when(repository.currentRevision(1L)).thenAnswer(invocation -> revision.get());
        when(repository.categories(List.of(11L))).thenReturn(List.of(category(11L, 1L, true)));
        when(repository.items(List.of(11L))).thenReturn(List.of(item(100L, "FOOD", null, true, "10.00")));
        when(repository.updateBasePrice(eq(1L), any())).thenReturn(1);
        doAnswer(invocation -> { stored.set(invocation.getArgument(0)); return null; }).when(repository).savePreview(any());
        when(repository.preview(anyString(), eq(1L))).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        doAnswer(invocation -> { revision.incrementAndGet(); return null; }).when(revisions).incrementRevision(1L);
    }

    @Test void previewUsesRealPricesAndAlwaysExcludesRefillsAndFixedOffers() {
        when(repository.items(List.of(11L))).thenReturn(List.of(
            item(100L, "FOOD", null, true, "10.00"), item(101L, "DRINK", null, false, "4.00"),
            item(102L, "FOOD", "SOUP_NOODLE", true, "0.00"), item(103L, "combo", null, true, "22.00"),
            item(104L, "FIXED_OFFER", null, true, "19.00")));
        Preview result = service.preview(request("INCREASE", "1.25"));
        assertThat(result.items()).extracting(PriceChange::item_id).containsExactly(100L);
        assertThat(result.items().get(0).old_base_price()).isEqualByComparingTo("10.00");
        assertThat(result.items().get(0).new_base_price()).isEqualByComparingTo("11.25");
        assertThat(result.excluded_items()).extracting(ExcludedItem::reason)
            .containsExactly("INACTIVE_ITEM", "REFILL_ITEM", "FIXED_OFFER", "FIXED_OFFER");
        assertThat(result.menu_revision()).isEqualTo(4);
        assertThat(result.expires_at()).isEqualTo(clock.instant().plusSeconds(600));
        verify(revisions).lockStoresInOrder(List.of(1L));
        verify(repository, never()).updateBasePrice(anyLong(), any());
        verify(repository, never()).insertAudit(anyLong(), any(), anyString());
    }

    @Test void inactiveInclusionIsExplicitAndDoesNotOverrideRefillOrOfferExclusions() {
        when(repository.items(List.of(11L))).thenReturn(List.of(item(100L, "FOOD", null, false, "3.00"),
            item(101L, "OFFER", null, false, "18.00"), item(102L, "FOOD", "RICE", false, "0.00")));
        var request = request("DECREASE", "3.00"); request.include_inactive = true;
        Preview result = service.preview(request);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).new_base_price()).isEqualByComparingTo("0.00");
        assertThat(result.excluded_item_count()).isEqualTo(2);
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-0.01", "1.001", "1.000", "1E+40"})
    void rejectsInvalidAmountBeforeAnyDatabaseMutation(String amount) {
        assertThatThrownBy(() -> service.preview(request("INCREASE", amount)))
            .isInstanceOf(CategoryPricingException.class).hasMessageContaining("positive CAD");
        verifyNoInteractions(revisions);
        verify(repository, never()).savePreview(any());
    }

    @Test void rejectsMissingInactiveScopePercentageAndOtherCurrency() {
        var request = request("INCREASE", "1"); request.include_inactive = null;
        assertCode(() -> service.preview(request), "CATEGORY_PRICING_ACTIVE_SCOPE_REQUIRED", HttpStatus.BAD_REQUEST);
        request.include_inactive = false; request.direction = "PERCENT";
        assertCode(() -> service.preview(request), "CATEGORY_PRICING_DIRECTION_INVALID", HttpStatus.BAD_REQUEST);
        request.direction = "INCREASE"; request.currency = "USD";
        assertCode(() -> service.preview(request), "CATEGORY_PRICING_CURRENCY_INVALID", HttpStatus.BAD_REQUEST);
        verify(repository, never()).savePreview(any());
    }

    @Test void capabilityFailsClosedWithoutCreatingPreview() {
        when(repository.bulkEnabled(1L)).thenReturn(false);
        assertCode(() -> service.preview(request("INCREASE", "1")), "CATEGORY_PRICING_DISABLED", HttpStatus.FORBIDDEN);
        verify(repository, never()).savePreview(any());
    }

    @Test void crossStoreAndInactiveCategoriesRejectTheWholeRequest() {
        when(repository.categories(List.of(11L))).thenReturn(List.of(category(11L, 2L, true)));
        assertCode(() -> service.preview(request("INCREASE", "1")), "CATEGORY_PRICING_CATEGORY_INVALID", HttpStatus.BAD_REQUEST);
        when(repository.categories(List.of(11L))).thenReturn(List.of(category(11L, 1L, false)));
        assertCode(() -> service.preview(request("INCREASE", "1")), "CATEGORY_PRICING_CATEGORY_INVALID", HttpStatus.BAD_REQUEST);
        verify(repository, never()).savePreview(any());
    }

    @Test void negativeResultRejectsTheEntireBatchBeforePreviewOrWrites() {
        when(repository.items(List.of(11L))).thenReturn(List.of(item(100L, "FOOD", null, true, "10.00"),
            item(101L, "DRINK", null, true, "0.50")));
        assertCode(() -> service.preview(request("DECREASE", "1")), "CATEGORY_PRICING_NEGATIVE_PRICE", HttpStatus.BAD_REQUEST);
        verify(repository, never()).savePreview(any());
        verify(repository, never()).updateBasePrice(anyLong(), any());
    }

    @Test void applyLocksRecalculatesAndWritesOneRevisionAuditAndLedger() {
        ApplyRequest apply = apply(service.preview(request("INCREASE", "1.25")));
        ApplyResult result = service.apply(apply, actor);
        assertThat(result.before_revision()).isEqualTo(4);
        assertThat(result.menu_revision()).isEqualTo(5);
        assertThat(result.items().get(0).new_base_price()).isEqualByComparingTo("11.25");
        var order = inOrder(revisions, repository);
        order.verify(revisions, times(2)).lockStoresInOrder(List.of(1L));
        order.verify(repository).updateBasePrice(eq(1L), any());
        order.verify(revisions).incrementRevision(1L);
        order.verify(repository).insertAudit(eq(1L), eq(actor), anyString());
        order.verify(repository).saveLedger(eq(1L), eq("save-1"), anyString(), eq(apply.preview_id), anyString());
    }

    @Test void replayReturnsTheOriginalResultWithoutRevalidatingExpiredPreviewOrWriting() throws Exception {
        var apply = apply(service.preview(request("INCREASE", "1.25")));
        ApplyResult first = service.apply(apply, actor);
        AtomicReference<String> savedHash = new AtomicReference<>();
        // Capture the canonical hash from the durable-write arguments without reproducing the hashing implementation.
        var hash = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(repository).saveLedger(eq(1L), eq("save-1"), hash.capture(), eq(apply.preview_id), anyString());
        savedHash.set(hash.getValue());
        when(repository.ledger(1L, "save-1")).thenReturn(Optional.of(new CategoryBulkPriceRepository.LedgerEntry(savedHash.get(), json.writeValueAsString(first))));
        stored.set(new CategoryBulkPriceRepository.StoredPreview(apply.preview_id, 1L, "unused", 4L, apply.fingerprint, clock.instant().minusSeconds(1)));
        assertThat(service.apply(apply, actor)).isEqualTo(first);
        apply.amount = new BigDecimal("2.00");
        assertCode(() -> service.apply(apply, actor), "CATEGORY_PRICING_IDEMPOTENCY_CONFLICT", HttpStatus.CONFLICT);
        verify(repository, times(1)).updateBasePrice(anyLong(), any());
        verify(revisions, times(1)).incrementRevision(1L);
        verify(repository, times(1)).insertAudit(anyLong(), any(), anyString());
        verify(repository, times(1)).preview(anyString(), anyLong());
    }

    @Test void changedRevisionFingerprintBodyAndExpirationAllRejectBeforeWriting() {
        var apply = apply(service.preview(request("INCREASE", "1")));
        revision.incrementAndGet();
        assertCode(() -> service.apply(apply, actor), "CATEGORY_PRICING_PREVIEW_STALE", HttpStatus.CONFLICT);
        revision.set(4);
        when(repository.items(List.of(11L))).thenReturn(List.of(item(100L, "FOOD", null, true, "9.00")));
        assertCode(() -> service.apply(apply, actor), "CATEGORY_PRICING_PREVIEW_STALE", HttpStatus.CONFLICT);
        when(repository.items(List.of(11L))).thenReturn(List.of(item(100L, "FOOD", null, true, "10.00")));
        apply.amount = new BigDecimal("2");
        assertCode(() -> service.apply(apply, actor), "CATEGORY_PRICING_PREVIEW_STALE", HttpStatus.CONFLICT);
        apply.amount = new BigDecimal("1");
        var row = stored.get();
        stored.set(new CategoryBulkPriceRepository.StoredPreview(row.preview_id(), row.store_id(), row.body_hash(), row.menu_revision(), row.before_fingerprint(), clock.instant()));
        assertCode(() -> service.apply(apply, actor), "CATEGORY_PRICING_PREVIEW_EXPIRED", HttpStatus.CONFLICT);
        verify(repository, never()).updateBasePrice(anyLong(), any());
        verify(repository, never()).insertAudit(anyLong(), any(), anyString());
    }

    @Test void missingPreviewAndApplyIdentityCannotWrite() {
        var apply = apply(service.preview(request("INCREASE", "1")));
        apply.idempotency_key = " ";
        assertCode(() -> service.apply(apply, actor), "CATEGORY_PRICING_IDEMPOTENCY_REQUIRED", HttpStatus.BAD_REQUEST);
        apply.idempotency_key = "save-1";
        stored.set(null);
        assertCode(() -> service.apply(apply, actor), "CATEGORY_PRICING_PREVIEW_STALE", HttpStatus.CONFLICT);
        verify(repository, never()).updateBasePrice(anyLong(), any());
    }

    private static PreviewRequest request(String direction, String amount) {
        var request = new PreviewRequest(); request.store_id = 1L; request.category_ids = List.of(11L);
        request.direction = direction; request.amount = new BigDecimal(amount); request.currency = "CAD"; request.include_inactive = false;
        return request;
    }
    private static ApplyRequest apply(Preview preview) {
        var request = new ApplyRequest(); request.store_id = preview.store_id(); request.category_ids = preview.category_ids();
        request.direction = preview.direction(); request.amount = preview.amount(); request.currency = preview.currency(); request.include_inactive = preview.include_inactive();
        request.preview_id = preview.preview_id(); request.expected_revision = preview.menu_revision(); request.fingerprint = preview.fingerprint(); request.idempotency_key = "save-1";
        return request;
    }
    private static CategoryBulkPriceRepository.CategoryRow category(Long id, Long store, boolean active) {
        return new CategoryBulkPriceRepository.CategoryRow(id, store, "FOOD", "面", "Noodles", "Nouilles", active);
    }
    private static CategoryBulkPriceRepository.ItemRow item(Long id, String type, String refill, boolean active, String price) {
        return new CategoryBulkPriceRepository.ItemRow(id, 1L, 11L, "SKU" + id, "菜品", "Item", "Article", type, refill, active,
            new BigDecimal(price), LocalDateTime.of(2026, 11, 12, 10, 0));
    }
    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String code, HttpStatus status) {
        assertThatThrownBy(action).isInstanceOfSatisfying(CategoryPricingException.class, error -> {
            assertThat(error.getErrorCode()).isEqualTo(code); assertThat(error.getStatus()).isEqualTo(status);
        });
    }
}
