package com.restaurant.system.staging.cleanup;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.owner.exception.OwnerStoreProvisioningException;
import com.restaurant.system.owner.profile.StoreProfileCanonicalJson;
import com.restaurant.system.owner.provisioning.PhaseBProvisioningRuntimeGate;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Restricted, Staging-only cleanup for audited synthetic/test Store fixtures.
 *
 * <p>This class deliberately does not implement generic Store deletion. The
 * target identity, direct-store table inventory, FK inventory, historical
 * evidence policy and source-reference checks are all fail-closed.</p>
 */
@Service
public class StoreFixtureCleanupServiceImpl implements StoreFixtureCleanupService {

    private static final Set<String> DELETABLE_STORE_ID_TABLES = Set.of(
        "analytics_alerts",
        "dining_tables",
        "frontdesk_beverage_items",
        "inventory_items",
        "kitchen_tasks",
        "menu_categories",
        "menu_item_sales_summary",
        "menu_items",
        "order_dispatch_outbox",
        "order_submission_requests",
        "orders",
        "print_jobs",
        "printer_assignments",
        "printer_configs",
        "printing_display_rule_sets",
        "production_tasks",
        "receipt_templates",
        "refresh_tokens",
        "sales_daily_summary",
        "sales_hourly_summary",
        "stations",
        "store_combo_components",
        "store_combo_groups",
        "store_device_readiness",
        "store_devices",
        "store_kds_display_configs",
        "store_logical_printer_roles",
        "store_memberships",
        "store_menu_master_mappings",
        "store_modules",
        "store_performance_summary",
        "store_pricing_policies",
        "users"
    );

    private static final Set<String> PRESERVED_STORE_ID_TABLES = Set.of(
        "audit_logs",
        "owner_store_onboarding_requests",
        "owner_store_provisioning_requests",
        "store_activation_requests",
        "store_provisioning_part2_requests",
        "store_provisioning_resources",
        "store_readiness_evidence",
        "store_readiness_evidence_history"
    );

    private static final Set<String> ALLOWED_STORE_FK_TABLES = Set.of(
        "owner_store_provisioning_requests",
        "printing_display_rule_sets",
        "store_combo_components",
        "store_combo_groups",
        "store_menu_master_mappings",
        "store_modules",
        "store_pricing_policies"
    );

    private static final Set<String> PRESERVED_EVIDENCE_TABLES = Set.of(
        "audit_logs",
        "owner_store_onboarding_requests",
        "owner_store_provisioning_requests",
        "store_activation_requests",
        "store_provisioning_part2_requests",
        "store_provisioning_resources",
        "store_readiness_evidence",
        "store_readiness_evidence_history"
    );

    private final StoreRepository storeRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PhaseBProvisioningRuntimeGate runtimeGate;
    private final ObjectMapper objectMapper;

    public StoreFixtureCleanupServiceImpl(
        StoreRepository storeRepository,
        JdbcTemplate jdbcTemplate,
        PhaseBProvisioningRuntimeGate runtimeGate,
        ObjectMapper objectMapper
    ) {
        this.storeRepository = storeRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.runtimeGate = runtimeGate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public StoreFixtureCleanupResponse cleanup(
        AuthenticatedUser actor,
        Long organizationId,
        String idempotencyKey,
        StoreFixtureCleanupRequest request
    ) {
        runtimeGate.requireEnabled();
        requireActor(actor);
        if (organizationId == null || request == null) {
            throw conflict("STAGING_FIXTURE_CLEANUP_SCOPE_REQUIRED", "Organization and cleanup request are required");
        }
        List<Long> storeIds = normalizeIds(request.store_ids);
        if (storeIds.isEmpty() || storeIds.size() > 32) {
            throw conflict("STAGING_FIXTURE_CLEANUP_TARGETS_REQUIRED", "One to thirty-two explicit Store IDs are required");
        }
        Set<Long> ownerManualIds = normalizeIds(request.approved_owner_manual_store_ids).stream()
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!StoreFixtureCleanupPolicy.APPROVED_OWNER_MANUAL_STORE_IDS.containsAll(ownerManualIds)) {
            throw conflict("STAGING_FIXTURE_CLEANUP_OWNER_MANUAL_SCOPE_INVALID", "Owner-manual approval contains an unapproved Store ID");
        }
        boolean dryRun = !Boolean.FALSE.equals(request.dry_run);
        if (!dryRun && (idempotencyKey == null || idempotencyKey.isBlank())) {
            throw conflict("STAGING_FIXTURE_CLEANUP_IDEMPOTENCY_KEY_REQUIRED", "Execute cleanup requires Idempotency-Key");
        }
        String normalizedIdempotencyKey = idempotencyKey == null ? null : idempotencyKey.trim();
        if (!dryRun && normalizedIdempotencyKey.length() > 255) {
            throw conflict("STAGING_FIXTURE_CLEANUP_IDEMPOTENCY_KEY_INVALID", "Idempotency-Key must be at most 255 characters");
        }

        StoreFixtureCleanupResponse response = baseResponse(organizationId, storeIds, dryRun);
        List<Store> lockedStores = storeRepository.findAllByIdInForUpdateOrderByIdAsc(storeIds);
        Map<Long, Store> storesById = lockedStores.stream().collect(Collectors.toMap(store -> store.id, store -> store));
        List<Store> existingStores = new ArrayList<>();
        for (Long storeId : storeIds) {
            Store store = storesById.get(storeId);
            StoreFixtureCleanupResponse.StoreResult result = new StoreFixtureCleanupResponse.StoreResult();
            result.store_id = storeId;
            if (store == null) {
                result.disposition = "ALREADY_CLEANED";
                result.reason = "Store row is already absent; idempotent no-op";
                response.stores.add(result);
                continue;
            }
            result.code = store.code;
            if (!organizationId.equals(store.organization_id)) {
                result.classification = "REVIEW_UNSAFE_OR_UNKNOWN";
                result.disposition = "REJECTED";
                result.reason = "Store belongs to a different Organization";
                response.stores.add(result);
                throw conflict("STAGING_FIXTURE_CLEANUP_ORGANIZATION_MISMATCH", "A target Store does not belong to the requested Organization");
            }
            StoreFixtureCleanupPolicy.Classification classification = StoreFixtureCleanupPolicy.classify(store, ownerManualIds);
            result.classification = classification.code();
            result.reason = classification.reason();
            result.disposition = classification.deletable() ? "READY" : "REJECTED";
            response.stores.add(result);
            if (!classification.deletable()) {
                throw conflict("STAGING_FIXTURE_CLEANUP_TARGET_REJECTED", classification.reason());
            }
            existingStores.add(store);
        }

        if (!dryRun) {
            LedgerReservation reservation = reserveLedger(
                organizationId,
                normalizedIdempotencyKey,
                requestFingerprint(storeIds, ownerManualIds),
                storeIds,
                ownerManualIds,
                actor.userId()
            );
            if (reservation.replayed()) {
                return replayResponse(reservation.resultJson());
            }
        }
        preflight(response, storeIds, existingStores);
        if (dryRun) {
            response.status = "DRY_RUN_PASS";
            response.dependency_checks.add("NO_WRITE_EXECUTED");
            return response;
        }

        response.status = "EXECUTED";
        deleteFixtureGraph(response, storeIds);
        completeLedger(organizationId, normalizedIdempotencyKey, response);
        return response;
    }

    private LedgerReservation reserveLedger(
        Long organizationId,
        String idempotencyKey,
        String requestFingerprint,
        List<Long> storeIds,
        Set<Long> ownerManualIds,
        Long actorUserId
    ) {
        jdbcTemplate.queryForObject(
            "select pg_advisory_xact_lock(hashtextextended(?, 0))",
            (ResultSet rs, int rowNum) -> rs.getObject(1),
            idempotencyKey
        );
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "select request_fingerprint, status, result_json from staging_store_fixture_cleanup_requests "
                + "where organization_id = ? and idempotency_key = ? for update",
            organizationId,
            idempotencyKey
        );
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            String existingFingerprint = String.valueOf(row.get("request_fingerprint"));
            if (!requestFingerprint.equalsIgnoreCase(existingFingerprint)) {
                throw conflict("STAGING_FIXTURE_CLEANUP_IDEMPOTENCY_CONFLICT", "Idempotency-Key was already used for a different cleanup request");
            }
            if ("COMPLETED".equalsIgnoreCase(String.valueOf(row.get("status")))) {
                return new LedgerReservation(true, (String) row.get("result_json"));
            }
            throw conflict("STAGING_FIXTURE_CLEANUP_IN_PROGRESS", "An identical cleanup request is already in progress");
        }
        LocalDateTime now = LocalDateTime.now();
        int inserted = jdbcTemplate.update(
            "insert into staging_store_fixture_cleanup_requests "
                + "(organization_id, idempotency_key, request_fingerprint, status, store_ids_json, "
                + "approved_owner_manual_store_ids_json, actor_user_id, created_at, updated_at) "
                + "values (?, ?, ?, 'PROCESSING', ?, ?, ?, ?, ?) "
                + "on conflict (organization_id, idempotency_key) do nothing",
            organizationId,
            idempotencyKey,
            requestFingerprint,
            storeIds.toString(),
            ownerManualIds.stream().sorted().toList().toString(),
            actorUserId,
            now,
            now
        );
        if (inserted == 0) {
            List<Map<String, Object>> concurrentRows = jdbcTemplate.queryForList(
                "select request_fingerprint, status, result_json from staging_store_fixture_cleanup_requests "
                    + "where organization_id = ? and idempotency_key = ? for update",
                organizationId,
                idempotencyKey
            );
            if (concurrentRows.isEmpty()) {
                throw conflict("STAGING_FIXTURE_CLEANUP_LEDGER_RECHECK_FAILED", "Concurrent cleanup ledger row could not be re-read");
            }
            Map<String, Object> row = concurrentRows.get(0);
            if (!requestFingerprint.equalsIgnoreCase(String.valueOf(row.get("request_fingerprint")))) {
                throw conflict("STAGING_FIXTURE_CLEANUP_IDEMPOTENCY_CONFLICT", "Idempotency-Key was already used for a different cleanup request");
            }
            if ("COMPLETED".equalsIgnoreCase(String.valueOf(row.get("status")))) {
                return new LedgerReservation(true, (String) row.get("result_json"));
            }
            throw conflict("STAGING_FIXTURE_CLEANUP_IN_PROGRESS", "An identical cleanup request is already in progress");
        }
        return new LedgerReservation(false, null);
    }

    private void completeLedger(Long organizationId, String idempotencyKey, StoreFixtureCleanupResponse response) {
        try {
            LocalDateTime now = LocalDateTime.now();
            int updated = jdbcTemplate.update(
                "update staging_store_fixture_cleanup_requests set status = 'COMPLETED', result_json = ?, updated_at = ?, completed_at = ? "
                    + "where organization_id = ? and idempotency_key = ? and status = 'PROCESSING'",
                objectMapper.writeValueAsString(response),
                now,
                now,
                organizationId,
                idempotencyKey
            );
            if (updated != 1) {
                throw conflict("STAGING_FIXTURE_CLEANUP_LEDGER_COMPLETION_FAILED", "Cleanup ledger completion was not unique");
            }
        } catch (JsonProcessingException exception) {
            throw conflict("STAGING_FIXTURE_CLEANUP_LEDGER_SERIALIZATION_FAILED", "Sanitized cleanup result could not be recorded");
        }
    }

    private StoreFixtureCleanupResponse replayResponse(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) {
            throw conflict("STAGING_FIXTURE_CLEANUP_LEDGER_RESULT_MISSING", "Completed cleanup ledger result is missing");
        }
        try {
            StoreFixtureCleanupResponse response = objectMapper.readValue(resultJson, StoreFixtureCleanupResponse.class);
            response.replayed = true;
            response.status = "REPLAYED";
            return response;
        } catch (JsonProcessingException exception) {
            throw conflict("STAGING_FIXTURE_CLEANUP_LEDGER_RESULT_INVALID", "Completed cleanup ledger result is invalid");
        }
    }

    private String requestFingerprint(List<Long> storeIds, Set<Long> ownerManualIds) {
        String canonical = "{\"store_ids\":" + storeIds + ",\"approved_owner_manual_store_ids\":"
            + ownerManualIds.stream().sorted().toList() + "}";
        return StoreProfileCanonicalJson.sha256Canonical(canonical);
    }

    private record LedgerReservation(boolean replayed, String resultJson) {
    }

    private void preflight(StoreFixtureCleanupResponse response, List<Long> storeIds, List<Store> stores) {
        Set<String> actualStoreIdTables = queryStrings(
            "select distinct table_name from information_schema.columns where table_schema = 'public' and column_name = 'store_id' order by table_name",
            "table_name"
        );
        Set<String> knownStoreIdTables = new HashSet<>(DELETABLE_STORE_ID_TABLES);
        knownStoreIdTables.addAll(PRESERVED_STORE_ID_TABLES);
        knownStoreIdTables.add("stores");
        Set<String> unknownTables = new LinkedHashSet<>(actualStoreIdTables);
        unknownTables.removeAll(knownStoreIdTables);
        if (!unknownTables.isEmpty()) {
            throw conflict("STAGING_FIXTURE_CLEANUP_UNKNOWN_STORE_DEPENDENCY", "Unhandled Store-local tables: " + String.join(",", unknownTables));
        }
        List<String> directStoreFkTables = jdbcTemplate.query(
            """
            select child.relname
            from pg_constraint constraint_row
            join pg_class child on child.oid = constraint_row.conrelid
            join pg_class parent on parent.oid = constraint_row.confrelid
            join pg_namespace child_schema on child_schema.oid = child.relnamespace
            join pg_namespace parent_schema on parent_schema.oid = parent.relnamespace
            where constraint_row.contype = 'f'
              and child_schema.nspname = 'public'
              and parent_schema.nspname = 'public'
              and parent.relname = 'stores'
            order by child.relname
            """,
            (ResultSet rs, int rowNum) -> rs.getString(1)
        );
        Set<String> unknownFks = new LinkedHashSet<>(directStoreFkTables);
        unknownFks.removeAll(ALLOWED_STORE_FK_TABLES);
        if (!unknownFks.isEmpty()) {
            throw conflict("STAGING_FIXTURE_CLEANUP_UNKNOWN_STORE_FK", "Unhandled direct Store foreign keys: " + String.join(",", unknownFks));
        }
        String placeholders = placeholders(storeIds.size());
        assertNoSourceReference(response, "staging_synthetic_bootstrap_requests", "source_store_id", placeholders, storeIds);
        assertNoSourceReference(response, "restaurant_templates", "source_store_id", placeholders, storeIds);
        assertNoSourceReference(response, "owner_store_menu_clone_requests", "source_store_id", placeholders, storeIds);
        assertNoTargetReference(response, "owner_store_menu_clone_requests", "target_store_id", placeholders, storeIds);
        assertSafeUsers(response, placeholders, storeIds);
        assertSafeInventoryReferences(response, placeholders, storeIds);
        response.dependency_checks.add("DIRECT_STORE_ID_TABLES_COVERED");
        response.dependency_checks.add("DIRECT_STORE_FK_GRAPH_COVERED");
        response.dependency_checks.add("SOURCE_REFERENCE_CHECK_PASS");
        response.dependency_checks.add("STAFF_AND_INVENTORY_DEPENDENCY_CHECK_PASS");
        for (String evidenceTable : PRESERVED_EVIDENCE_TABLES) {
            int count = count("select count(*) from " + evidenceTable + " where store_id in (" + placeholders + ")", storeIds);
            if (count > 0) {
                response.preserved_evidence_counts.put(evidenceTable, count);
            }
        }
    }

    private void assertNoSourceReference(
        StoreFixtureCleanupResponse response,
        String table,
        String column,
        String placeholders,
        List<Long> storeIds
    ) {
        int count = count("select count(*) from " + table + " where " + column + " in (" + placeholders + ")", storeIds);
        if (count > 0) {
            throw conflict("STAGING_FIXTURE_CLEANUP_SOURCE_DEPENDENCY", table + " has " + count + " source/reference row(s)");
        }
        response.dependency_checks.add(table + "." + column + "=0");
    }

    private void assertNoTargetReference(
        StoreFixtureCleanupResponse response,
        String table,
        String column,
        String placeholders,
        List<Long> storeIds
    ) {
        int count = count("select count(*) from " + table + " where " + column + " in (" + placeholders + ")", storeIds);
        if (count > 0) {
            throw conflict("STAGING_FIXTURE_CLEANUP_TARGET_DEPENDENCY", table + " has " + count + " historical target row(s)");
        }
        response.dependency_checks.add(table + "." + column + "=0");
    }

    private void assertSafeUsers(StoreFixtureCleanupResponse response, String placeholders, List<Long> storeIds) {
        int foreignOrganizationMemberships = count(
            """
            select count(*) from organization_memberships membership
            join users user_row on user_row.id = membership.user_id
            where user_row.store_id in (%s)
              and (membership.organization_id is distinct from (select organization_id from stores where id = user_row.store_id limit 1)
                   or upper(coalesce(membership.role_code, '')) = 'OWNER'
                   or upper(coalesce((select role.code from roles role where role.id = membership.role_id), '')) = 'OWNER')
            """.formatted(placeholders),
            storeIds
        );
        if (foreignOrganizationMemberships > 0) {
            throw conflict("STAGING_FIXTURE_CLEANUP_STAFF_DEPENDENCY", "Target staff has an external or Owner membership");
        }
        int foreignStoreMemberships = count(
            """
            select count(*) from store_memberships membership
            join users user_row on user_row.id = membership.user_id
            where user_row.store_id in (%s)
              and membership.store_id not in (%s)
            """.formatted(placeholders, placeholders),
            concat(storeIds, storeIds)
        );
        if (foreignStoreMemberships > 0) {
            throw conflict("STAGING_FIXTURE_CLEANUP_STAFF_DEPENDENCY", "Target staff has a membership in another Store");
        }
        response.dependency_checks.add("STAFF_MEMBERSHIP_BOUNDARY_PASS");
    }

    private void assertSafeInventoryReferences(StoreFixtureCleanupResponse response, String placeholders, List<Long> storeIds) {
        int externalBomReferences = count(
            """
            select count(*) from menu_item_bom bom
            join inventory_items inventory on inventory.id = bom.inventory_item_id
            join menu_items menu_item on menu_item.id = bom.menu_item_id
            where inventory.store_id in (%s) and menu_item.store_id not in (%s)
            """.formatted(placeholders, placeholders),
            concat(storeIds, storeIds)
        );
        int externalOptionBomReferences = count(
            """
            select count(*) from menu_item_option_bom bom
            join inventory_items inventory on inventory.id = bom.inventory_item_id
            join menu_item_options option_row on option_row.id = bom.menu_item_option_id
            join menu_items menu_item on menu_item.id = option_row.menu_item_id
            where inventory.store_id in (%s) and menu_item.store_id not in (%s)
            """.formatted(placeholders, placeholders),
            concat(storeIds, storeIds)
        );
        int recipeReferences = count(
            """
            select count(*) from prep_recipes recipe
            join inventory_items inventory on inventory.id = recipe.output_inventory_item_id
            where inventory.store_id in (%s)
            """.formatted(placeholders),
            storeIds
        ) + count(
            """
            select count(*) from prep_recipe_details detail
            join inventory_items inventory on inventory.id = detail.input_inventory_item_id
            where inventory.store_id in (%s)
            """.formatted(placeholders),
            storeIds
        );
        if (externalBomReferences + externalOptionBomReferences + recipeReferences > 0) {
            throw conflict("STAGING_FIXTURE_CLEANUP_INVENTORY_DEPENDENCY", "Target inventory is referenced outside the target Store-local menu graph");
        }
        response.dependency_checks.add("INVENTORY_DEPENDENCY_BOUNDARY_PASS");
    }

    private void deleteFixtureGraph(StoreFixtureCleanupResponse response, List<Long> storeIds) {
        String p = placeholders(storeIds.size());
        delete(response, "analytics_alerts", "delete from analytics_alerts where store_id in (" + p + ")", storeIds);
        delete(response, "order_item_options", "delete from order_item_options where order_item_id in (select id from order_items where order_id in (select id from orders where store_id in (" + p + "))) ", storeIds);
        delete(response, "frontdesk_beverage_items", "delete from frontdesk_beverage_items where store_id in (" + p + ") or order_id in (select id from orders where store_id in (" + p + "))", concat(storeIds, storeIds));
        delete(response, "kitchen_tasks", "delete from kitchen_tasks where store_id in (" + p + ") or order_id in (select id from orders where store_id in (" + p + "))", concat(storeIds, storeIds));
        delete(response, "production_tasks", "delete from production_tasks where store_id in (" + p + ") or order_id in (select id from orders where store_id in (" + p + "))", concat(storeIds, storeIds));
        delete(response, "order_dispatch_outbox", "delete from order_dispatch_outbox where store_id in (" + p + ") or order_id in (select id from orders where store_id in (" + p + "))", concat(storeIds, storeIds));
        delete(response, "order_submission_requests", "delete from order_submission_requests where store_id in (" + p + ") or order_id in (select id from orders where store_id in (" + p + "))", concat(storeIds, storeIds));
        delete(response, "print_job_attempts", "delete from print_job_attempts where print_job_id in (select id from print_jobs where store_id in (" + p + "))", storeIds);
        delete(response, "print_jobs", "delete from print_jobs where store_id in (" + p + ")", storeIds);
        delete(response, "order_update_batches", "delete from order_update_batches where order_id in (select id from orders where store_id in (" + p + "))", storeIds);
        delete(response, "order_items", "delete from order_items where order_id in (select id from orders where store_id in (" + p + "))", storeIds);
        delete(response, "orders", "delete from orders where store_id in (" + p + ")", storeIds);

        delete(response, "store_logical_printer_roles", "delete from store_logical_printer_roles where store_id in (" + p + ")", storeIds);
        delete(response, "printer_assignments", "delete from printer_assignments where store_id in (" + p + ")", storeIds);
        delete(response, "printer_configs", "delete from printer_configs where store_id in (" + p + ")", storeIds);
        jdbcTemplate.update("update printing_display_rule_sets set active_revision_id = null where store_id in (" + p + ")", storeIds.toArray());
        delete(response, "printing_display_rule_revisions", "delete from printing_display_rule_revisions where rule_set_id in (select id from printing_display_rule_sets where store_id in (" + p + "))", storeIds);
        delete(response, "printing_display_rule_sets", "delete from printing_display_rule_sets where store_id in (" + p + ")", storeIds);

        delete(response, "store_combo_components", "delete from store_combo_components where store_id in (" + p + ")", storeIds);
        delete(response, "store_combo_groups", "delete from store_combo_groups where store_id in (" + p + ")", storeIds);
        delete(response, "menu_item_option_bom", "delete from menu_item_option_bom where menu_item_option_id in (select id from menu_item_options where menu_item_id in (select id from menu_items where store_id in (" + p + "))) or inventory_item_id in (select id from inventory_items where store_id in (" + p + "))", concat(storeIds, storeIds));
        delete(response, "menu_item_bom", "delete from menu_item_bom where menu_item_id in (select id from menu_items where store_id in (" + p + ")) or inventory_item_id in (select id from inventory_items where store_id in (" + p + "))", concat(storeIds, storeIds));
        delete(response, "menu_item_sales_summary", "delete from menu_item_sales_summary where store_id in (" + p + ")", storeIds);
        delete(response, "menu_item_options", "delete from menu_item_options where menu_item_id in (select id from menu_items where store_id in (" + p + "))", storeIds);
        delete(response, "menu_items", "delete from menu_items where store_id in (" + p + ")", storeIds);
        delete(response, "menu_categories", "delete from menu_categories where store_id in (" + p + ")", storeIds);
        delete(response, "inventory_transactions", "delete from inventory_transactions where inventory_item_id in (select id from inventory_items where store_id in (" + p + "))", storeIds);
        delete(response, "inventory_items", "delete from inventory_items where store_id in (" + p + ")", storeIds);

        delete(response, "store_menu_master_mappings", "delete from store_menu_master_mappings where store_id in (" + p + ")", storeIds);
        delete(response, "store_modules", "delete from store_modules where store_id in (" + p + ")", storeIds);
        delete(response, "store_pricing_policies", "delete from store_pricing_policies where store_id in (" + p + ")", storeIds);
        delete(response, "store_device_readiness", "delete from store_device_readiness where store_id in (" + p + ")", storeIds);
        delete(response, "store_devices", "delete from store_devices where store_id in (" + p + ")", storeIds);
        delete(response, "store_kds_display_configs", "delete from store_kds_display_configs where store_id in (" + p + ")", storeIds);
        delete(response, "receipt_templates", "delete from receipt_templates where store_id in (" + p + ")", storeIds);
        delete(response, "sales_daily_summary", "delete from sales_daily_summary where store_id in (" + p + ")", storeIds);
        delete(response, "sales_hourly_summary", "delete from sales_hourly_summary where store_id in (" + p + ")", storeIds);
        delete(response, "store_performance_summary", "delete from store_performance_summary where store_id in (" + p + ")", storeIds);
        delete(response, "dining_tables", "delete from dining_tables where store_id in (" + p + ")", storeIds);
        delete(response, "user_stations", "delete from user_stations where user_id in (select id from users where store_id in (" + p + ")) or station_id in (select id from stations where store_id in (" + p + "))", concat(storeIds, storeIds));
        delete(response, "store_memberships", "delete from store_memberships where store_id in (" + p + ")", storeIds);
        delete(response, "refresh_tokens", "delete from refresh_tokens where store_id in (" + p + ") or user_id in (select id from users where store_id in (" + p + "))", concat(storeIds, storeIds));
        delete(response, "user_credentials", "delete from user_credentials where user_id in (select id from users where store_id in (" + p + "))", storeIds);
        delete(response, "organization_memberships", "delete from organization_memberships where user_id in (select id from users where store_id in (" + p + "))", storeIds);
        delete(response, "users", "delete from users where store_id in (" + p + ")", storeIds);
        delete(response, "stations", "delete from stations where store_id in (" + p + ")", storeIds);

        int detachedEvidence = jdbcTemplate.update("update owner_store_provisioning_requests set store_id = null where store_id in (" + p + ")", storeIds.toArray());
        if (detachedEvidence > 0) {
            response.preserved_evidence_counts.put("owner_store_provisioning_requests_detached", detachedEvidence);
        }
        delete(response, "store_root", "delete from stores where id in (" + p + ")", storeIds);
        response.dependency_checks.add("STORE_ROOT_DELETE_COMPLETED_TRANSACTIONALLY");
    }

    private void delete(StoreFixtureCleanupResponse response, String key, String sql, List<Long> arguments) {
        int count = jdbcTemplate.update(sql, arguments.toArray());
        if (count > 0) {
            response.deleted_counts.merge(key, count, Integer::sum);
        }
    }

    private int count(String sql, List<Long> arguments) {
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class, arguments.toArray());
        return result == null ? 0 : result;
    }

    private Set<String> queryStrings(String sql, String column) {
        return jdbcTemplate.query(sql, (ResultSet rs, int rowNum) -> rs.getString(column))
            .stream()
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private StoreFixtureCleanupResponse baseResponse(Long organizationId, List<Long> storeIds, boolean dryRun) {
        StoreFixtureCleanupResponse response = new StoreFixtureCleanupResponse();
        response.organization_id = organizationId;
        response.dry_run = dryRun;
        response.requested_store_ids.addAll(storeIds);
        return response;
    }

    private List<Long> normalizeIds(Collection<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
            .filter(id -> id != null && id > 0)
            .distinct()
            .sorted()
            .toList();
    }

    private List<Long> concat(List<Long> first, List<Long> second) {
        List<Long> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return result;
    }

    private String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    private void requireActor(AuthenticatedUser actor) {
        if (actor == null || actor.userId() == null) {
            throw conflict("STAGING_FIXTURE_CLEANUP_ACTOR_REQUIRED", "Owner actor is required");
        }
    }

    private OwnerStoreProvisioningException conflict(String code, String message) {
        return new OwnerStoreProvisioningException(code, HttpStatus.CONFLICT, message);
    }
}
