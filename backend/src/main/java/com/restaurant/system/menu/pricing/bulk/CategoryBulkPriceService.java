package com.restaurant.system.menu.pricing.bulk;

import static com.restaurant.system.menu.pricing.bulk.CategoryBulkPriceModels.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.menu.service.MenuRevisionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryBulkPriceService {
    static final Duration PREVIEW_LIFETIME = Duration.ofMinutes(10);
    private static final Set<String> FIXED_OFFER_TYPES = Set.of("COMBO", "OFFER", "FIXED_OFFER", "FIXED_COMBO");
    private record NormalizedRequest(Long store_id, List<Long> category_ids, String direction, BigDecimal amount,
        String currency, boolean include_inactive) {}
    private record Snapshot(Long store_id, List<CategoryBulkPriceRepository.CategoryRow> categories,
        List<CategoryBulkPriceRepository.ItemRow> items) {}
    private record ApplyIdentity(NormalizedRequest adjustment, String preview_id, long expected_revision, String fingerprint) {}
    private record Plan(List<PriceChange> items, List<ExcludedItem> excluded) {}

    private final CategoryBulkPriceRepository repository;
    private final MenuRevisionService revisions;
    private final ObjectMapper json;
    private final Clock clock;

    @Autowired
    public CategoryBulkPriceService(CategoryBulkPriceRepository repository, MenuRevisionService revisions, ObjectMapper json) {
        this(repository, revisions, json, Clock.systemUTC());
    }

    CategoryBulkPriceService(CategoryBulkPriceRepository repository, MenuRevisionService revisions, ObjectMapper json, Clock clock) {
        this.repository = repository;
        this.revisions = revisions;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PricingCapabilities capabilities(Long storeId) {
        requireStore(storeId);
        return new PricingCapabilities(storeId, repository.bulkEnabled(storeId), repository.standardSizeEnabled(storeId));
    }

    @Transactional
    public Preview preview(PreviewRequest request) {
        NormalizedRequest adjustment = normalize(request);
        requireStore(adjustment.store_id());
        revisions.lockStoresInOrder(List.of(adjustment.store_id()));
        requireBulkCapability(adjustment.store_id());
        Snapshot snapshot = snapshot(adjustment);
        Plan plan = plan(adjustment, snapshot);
        long revision = repository.currentRevision(adjustment.store_id());
        String fingerprint = hash(snapshot);
        var stored = new CategoryBulkPriceRepository.StoredPreview(UUID.randomUUID().toString(), adjustment.store_id(),
            hash(adjustment), revision, fingerprint, clock.instant().plus(PREVIEW_LIFETIME));
        repository.savePreview(stored);
        return new Preview(stored.preview_id(), adjustment.store_id(), adjustment.category_ids(), adjustment.direction(),
            adjustment.amount(), "CAD", adjustment.include_inactive(), revision, fingerprint, stored.expires_at(),
            plan.items().size(), plan.excluded().size(), plan.items(), plan.excluded());
    }

    @Transactional
    public ApplyResult apply(ApplyRequest request, AuthenticatedUser actor) {
        NormalizedRequest adjustment = normalize(request);
        validateApply(request);
        if (actor == null || actor.userId() == null) throw failure(HttpStatus.FORBIDDEN, "CATEGORY_PRICING_ACTOR_REQUIRED", "An authorized user is required.");
        requireStore(adjustment.store_id());
        revisions.lockStoresInOrder(List.of(adjustment.store_id()));
        requireBulkCapability(adjustment.store_id());
        String bodyHash = hash(new ApplyIdentity(adjustment, request.preview_id, request.expected_revision, request.fingerprint));

        // Replay must precede expiration/revision validation: the first commit may have advanced the menu already.
        var existing = repository.ledger(adjustment.store_id(), request.idempotency_key);
        if (existing.isPresent()) {
            if (!Objects.equals(existing.get().body_hash(), bodyHash)) {
                throw conflict("CATEGORY_PRICING_IDEMPOTENCY_CONFLICT", "This save identity was already used for a different adjustment.");
            }
            return deserializeResult(existing.get().response_json());
        }

        var stored = repository.preview(request.preview_id, adjustment.store_id())
            .orElseThrow(() -> conflict("CATEGORY_PRICING_PREVIEW_STALE", "Preview is no longer available. Preview the prices again."));
        if (!stored.expires_at().isAfter(clock.instant())) {
            throw conflict("CATEGORY_PRICING_PREVIEW_EXPIRED", "Preview has expired. Preview the prices again.");
        }
        if (!Objects.equals(stored.body_hash(), hash(adjustment))
            || stored.menu_revision() != request.expected_revision
            || !Objects.equals(stored.before_fingerprint(), request.fingerprint)
            || repository.currentRevision(adjustment.store_id()) != request.expected_revision) {
            throw conflict("CATEGORY_PRICING_PREVIEW_STALE", "Menu or adjustment changed. Preview the prices again.");
        }
        Snapshot snapshot = snapshot(adjustment);
        if (!Objects.equals(hash(snapshot), stored.before_fingerprint())) {
            throw conflict("CATEGORY_PRICING_PREVIEW_STALE", "Menu prices changed. Preview the prices again.");
        }
        Plan plan = plan(adjustment, snapshot);
        if (plan.items().isEmpty()) throw invalid("CATEGORY_PRICING_NO_ELIGIBLE_ITEMS", "No eligible items were selected.");

        for (PriceChange item : plan.items()) {
            if (repository.updateBasePrice(adjustment.store_id(), item) != 1) {
                throw conflict("CATEGORY_PRICING_PREVIEW_STALE", "Menu prices changed. Preview the prices again.");
            }
        }
        revisions.incrementRevision(adjustment.store_id());
        var result = new ApplyResult(adjustment.store_id(), request.preview_id, request.idempotency_key, adjustment.direction(),
            adjustment.amount(), "CAD", adjustment.include_inactive(), adjustment.category_ids(), request.expected_revision,
            repository.currentRevision(adjustment.store_id()), stored.before_fingerprint(), plan.items().size(),
            plan.excluded().size(), plan.items(), plan.excluded());
        String response = serialize(result);
        repository.insertAudit(adjustment.store_id(), actor, response);
        repository.saveLedger(adjustment.store_id(), request.idempotency_key, bodyHash, request.preview_id, response);
        return result;
    }

    private Snapshot snapshot(NormalizedRequest request) {
        var categories = repository.categories(request.category_ids());
        if (categories.size() != request.category_ids().size() || categories.stream().anyMatch(category ->
            !Objects.equals(category.store_id(), request.store_id()) || !Boolean.TRUE.equals(category.active()))) {
            throw invalid("CATEGORY_PRICING_CATEGORY_INVALID", "Choose active categories belonging to this Store.");
        }
        var items = repository.items(request.category_ids());
        if (items.stream().anyMatch(item -> !Objects.equals(item.store_id(), request.store_id()))) {
            throw invalid("CATEGORY_PRICING_ITEM_SCOPE_INVALID", "Selected categories contain an invalid Store relationship.");
        }
        return new Snapshot(request.store_id(), categories, items);
    }

    private Plan plan(NormalizedRequest request, Snapshot snapshot) {
        List<PriceChange> changes = new ArrayList<>();
        List<ExcludedItem> excluded = new ArrayList<>();
        for (var item : snapshot.items()) {
            String reason = exclusionReason(item, request.include_inactive());
            if (reason != null) {
                excluded.add(new ExcludedItem(item.id(), item.category_id(), item.sku(), item.name_zh(), item.name_en(), item.name_fr(), reason));
                continue;
            }
            BigDecimal before = item.base_price();
            if (before == null || before.signum() < 0 || before.scale() > 2) {
                throw invalid("CATEGORY_PRICING_BASE_PRICE_INVALID", "Every selected item must have a valid base price.");
            }
            BigDecimal after = "INCREASE".equals(request.direction()) ? before.add(request.amount()) : before.subtract(request.amount());
            if (after.signum() < 0) throw invalid("CATEGORY_PRICING_NEGATIVE_PRICE", "The adjustment would make an item price negative. No prices were changed.");
            if (after.precision() - (long) after.scale() > 36) throw invalid("CATEGORY_PRICING_PRICE_TOO_LARGE", "The adjusted price is too large.");
            changes.add(new PriceChange(item.id(), item.category_id(), item.sku(), item.name_zh(), item.name_en(), item.name_fr(),
                before.setScale(2, RoundingMode.UNNECESSARY), after.setScale(2, RoundingMode.UNNECESSARY)));
        }
        return new Plan(List.copyOf(changes), List.copyOf(excluded));
    }

    private String exclusionReason(CategoryBulkPriceRepository.ItemRow item, boolean includeInactive) {
        if (item.refill_category_code() != null) return "REFILL_ITEM";
        String type = item.item_type() == null ? "" : item.item_type().trim().toUpperCase(Locale.ROOT);
        if (FIXED_OFFER_TYPES.contains(type)) return "FIXED_OFFER";
        if (!includeInactive && !Boolean.TRUE.equals(item.active())) return "INACTIVE_ITEM";
        return null;
    }

    private NormalizedRequest normalize(PreviewRequest request) {
        if (request == null || request.store_id == null || request.store_id <= 0) {
            throw invalid("CATEGORY_PRICING_STORE_REQUIRED", "Store is required.");
        }
        if (request.category_ids == null || request.category_ids.isEmpty() || request.category_ids.size() > 500
            || request.category_ids.stream().anyMatch(id -> id == null || id <= 0)
            || request.category_ids.stream().distinct().count() != request.category_ids.size()) {
            throw invalid("CATEGORY_PRICING_CATEGORIES_REQUIRED", "Choose one or more distinct categories.");
        }
        if (!"INCREASE".equals(request.direction) && !"DECREASE".equals(request.direction)) {
            throw invalid("CATEGORY_PRICING_DIRECTION_INVALID", "Choose increase or decrease.");
        }
        if (!"CAD".equals(request.currency)) throw invalid("CATEGORY_PRICING_CURRENCY_INVALID", "The adjustment currency must be CAD.");
        if (request.include_inactive == null) throw invalid("CATEGORY_PRICING_ACTIVE_SCOPE_REQUIRED", "Specify whether inactive items are included.");
        if (request.amount == null || request.amount.signum() <= 0 || request.amount.scale() > 2
            || request.amount.precision() - (long) request.amount.scale() > 36) {
            throw invalid("CATEGORY_PRICING_AMOUNT_INVALID", "Enter a positive CAD amount with at most two decimal places.");
        }
        return new NormalizedRequest(request.store_id, request.category_ids.stream().sorted().toList(), request.direction,
            request.amount.setScale(2, RoundingMode.UNNECESSARY), "CAD", request.include_inactive);
    }

    private void validateApply(ApplyRequest request) {
        if (request.expected_revision == null || request.expected_revision < 1 || request.fingerprint == null
            || !request.fingerprint.matches("[0-9a-f]{64}") || request.preview_id == null
            || !request.preview_id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw invalid("CATEGORY_PRICING_PREVIEW_REQUIRED", "Preview the adjustment before saving.");
        }
        if (request.idempotency_key == null || !request.idempotency_key.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw invalid("CATEGORY_PRICING_IDEMPOTENCY_REQUIRED", "A valid save identity is required.");
        }
    }

    private void requireStore(Long storeId) {
        if (storeId == null || storeId <= 0) throw invalid("CATEGORY_PRICING_STORE_REQUIRED", "Store is required.");
        if (!repository.storeExists(storeId)) throw failure(HttpStatus.NOT_FOUND, "CATEGORY_PRICING_STORE_NOT_FOUND", "Store was not found.");
    }

    private void requireBulkCapability(Long storeId) {
        if (!repository.bulkEnabled(storeId)) throw failure(HttpStatus.FORBIDDEN, "CATEGORY_PRICING_DISABLED", "Category price adjustment is not enabled for this Store.");
    }

    private String hash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serialize(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String serialize(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Category price adjustment serialization failed", exception); }
    }

    private ApplyResult deserializeResult(String value) {
        try { return json.readValue(value, ApplyResult.class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Stored category price adjustment result is invalid", exception); }
    }

    private static CategoryPricingException invalid(String code, String message) { return failure(HttpStatus.BAD_REQUEST, code, message); }
    private static CategoryPricingException conflict(String code, String message) { return failure(HttpStatus.CONFLICT, code, message); }
    private static CategoryPricingException failure(HttpStatus status, String code, String message) {
        return new CategoryPricingException(status, code, message);
    }
}
