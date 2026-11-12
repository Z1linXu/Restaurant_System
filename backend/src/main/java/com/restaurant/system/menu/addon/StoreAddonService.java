package com.restaurant.system.menu.addon;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.printing.rules.PrintingDisplayRuleContext;
import com.restaurant.system.printing.rules.PrintingDisplayRuleService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Store catalog owns current Add-on values; order option snapshots are never written here. */
@Service
public class StoreAddonService {
    public record Addon(Long id, Long store_id, String code, String name_zh, String name_en,
                        BigDecimal price, boolean active, boolean printing_configured) {}
    public record ItemAddon(Long id, Long store_id, String code, String name_zh, String name_en,
                            BigDecimal price, boolean active, boolean printing_configured, boolean enabled) {}
    public record Conflict(String code, List<Long> option_ids, List<String> names_zh,
                           List<String> names_en, List<BigDecimal> prices, String reason) {}
    public record AddonList(List<Addon> addons, List<Conflict> conflicts) {}
    public record ReconciliationReport(Long store_id, boolean dry_run, int linked_groups,
                                       int linked_options, List<Conflict> conflicts,
                                       Long menu_revision, String fingerprint) {
        public ReconciliationReport(Long storeId, boolean dryRun, int groups, int options, List<Conflict> conflicts) {
            this(storeId, dryRun, groups, options, conflicts, null, null);
        }
    }
    public static class WriteRequest {
        public Long store_id;
        public String code;
        public String name_zh;
        public String name_en;
        public BigDecimal price;
        public Boolean active;
    }
    public static class ReconcileRequest {
        public Long store_id;
        public Boolean dry_run;
        public Map<String, BigDecimal> confirmed_prices;
        public Long expected_menu_revision;
        public String expected_fingerprint;
    }
    public static class EligibilityRequest { public Boolean enabled; }

    private record Values(String zh, String en, BigDecimal price, boolean active) {}
    private record Row(Long id, Long itemId, String code, String group, String type, String zh,
                       String en, BigDecimal price, Boolean active, Long addonId, Boolean eligible,
                       Long parentOptionId, String templateGroup) {}
    private record Group(String code, List<Row> rows, Addon existing, String conflict) {}
    private static final String SELECT_ADDONS = """
        select a.id, a.store_id, d.code, a.name_zh, a.name_en, a.price, a.active
        from store_addons a join organization_addon_definitions d
          on d.id = a.organization_addon_definition_id
        join stores s on s.id = a.store_id and s.organization_id = d.organization_id
        """;
    private final JdbcTemplate jdbc;
    private final MenuRevisionService revisions;
    private final PrintingDisplayRuleService printingRules;
    private final EntityManager entityManager;

    public StoreAddonService(JdbcTemplate jdbc, MenuRevisionService revisions,
                             PrintingDisplayRuleService printingRules, EntityManager entityManager) {
        this.jdbc = jdbc;
        this.revisions = revisions;
        this.printingRules = printingRules;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public AddonList getAddons(Long storeId) {
        organizationId(storeId);
        List<Addon> addons = addons(storeId);
        return new AddonList(addons, conflicts(plan(storeId, addons)));
    }

    @Transactional(readOnly = true)
    public Long storeIdForAddon(Long addonId) {
        return requireAddon(addonId).store_id();
    }

    @Transactional(readOnly = true)
    public Long storeIdForItem(Long itemId) {
        if (itemId == null) throw failure("ADDON_ITEM_REQUIRED");
        List<Long> ids = jdbc.query("select store_id from menu_items where id = ?",
            (rs, n) -> rs.getLong(1), itemId);
        if (ids.isEmpty()) throw failure("ADDON_ITEM_NOT_FOUND");
        return ids.get(0);
    }

    @Transactional
    public Addon create(WriteRequest request) {
        if (request == null) throw failure("ADDON_PAYLOAD_REQUIRED");
        String code = normalizeCode(request.code);
        requireNormalCode(code);
        Values values = validateValues(request);
        Long organizationId = lockStore(request.store_id);
        if (addons(request.store_id).stream().anyMatch(a -> a.code().equals(code))) {
            throw failure("ADDON_CODE_EXISTS: use the existing Store Add-on");
        }
        // Never create a competing catalog for an unresolved legacy identity.
        if (rows(request.store_id).stream().anyMatch(row -> code.equals(row.code()))) {
            throw failure("ADDON_LEGACY_CONFLICT: reconcile existing options before creating this code");
        }
        Long definitionId = definition(organizationId, code);
        long id = insertAddon(request.store_id, definitionId, values);
        printingRules.ensureCanonicalAddonFallback(request.store_id);
        revisions.incrementRevision(request.store_id);
        return requireAddon(id);
    }

    @Transactional
    public Addon update(Long addonId, WriteRequest request) {
        Addon before = requireAddon(addonId);
        lockStore(before.store_id());
        before = requireAddon(addonId);
        requireNormalCode(before.code());
        if (request == null) throw failure("ADDON_PAYLOAD_REQUIRED");
        if (request.code != null && !before.code().equals(normalizeCode(request.code))) {
            throw failure("ADDON_CODE_IMMUTABLE");
        }
        Values values = validateValues(request);
        printingRules.ensureCanonicalAddonFallback(before.store_id());
        jdbc.update("""
            update store_addons set name_zh=?, name_en=?, price=?, active=?, updated_at=current_timestamp
            where id=? and store_id=?
            """, values.zh(), values.en(), values.price(), values.active(), addonId, before.store_id());
        jdbc.update("""
            update menu_item_options set name_zh=?, name_en=?, price_delta=?,
              is_active=(? and addon_eligible), updated_at=current_timestamp
            where store_addon_id=? and store_addon_store_id=? and menu_item_id in (select id from menu_items where store_id=?)
            """, values.zh(), values.en(), values.price(), values.active(), addonId, before.store_id(), before.store_id());
        revisions.incrementRevision(before.store_id());
        return requireAddon(addonId);
    }

    @Transactional(readOnly = true)
    public List<ItemAddon> getItemAddons(Long itemId) {
        return getItemAddons(itemId, storeIdForItem(itemId));
    }

    @Transactional(readOnly = true)
    public List<ItemAddon> getItemAddons(Long itemId, Long authorizedStoreId) {
        Long storeId = storeIdForItem(itemId);
        if (!storeId.equals(authorizedStoreId)) throw failure("ADDON_STORE_MISMATCH");
        List<Long> enabled = jdbc.query("""
            select distinct store_addon_id from menu_item_options
            where menu_item_id=? and store_addon_store_id=? and store_addon_id is not null and addon_eligible=true
            """, (rs, n) -> rs.getLong(1), itemId, authorizedStoreId);
        return addons(storeId).stream().map(a -> new ItemAddon(a.id(), a.store_id(), a.code(),
            a.name_zh(), a.name_en(), a.price(), a.active(), a.printing_configured(), enabled.contains(a.id()))).toList();
    }

    @Transactional
    public void setEligibility(Long itemId, Long addonId, Boolean enabled) {
        setEligibility(itemId, addonId, enabled, storeIdForItem(itemId));
    }

    @Transactional
    public void setEligibility(Long itemId, Long addonId, Boolean enabled, Long authorizedStoreId) {
        if (enabled == null) throw failure("ADDON_ENABLED_REQUIRED");
        Long storeId = storeIdForItem(itemId);
        if (!storeId.equals(authorizedStoreId)) throw failure("ADDON_STORE_MISMATCH");
        lockStore(storeId);
        if (!storeId.equals(storeIdForItem(itemId))) throw failure("ADDON_STORE_MISMATCH");
        Addon addon = requireAddon(addonId);
        requireNormalCode(addon.code());
        if (!storeId.equals(addon.store_id())) throw failure("ADDON_STORE_MISMATCH");
        List<Row> itemRows = rows(storeId).stream().filter(r -> itemId.equals(r.itemId())).toList();
        if (itemRows.stream().anyMatch(r -> r.addonId() == null && addon.code().equals(r.code()))) {
            throw failure("ADDON_LEGACY_CONFLICT: reconcile existing options before changing eligibility");
        }
        if (hasAmbiguousRelationships(itemRows.stream().filter(r -> addon.code().equals(r.code())).toList())) {
            throw failure("ADDON_RELATIONSHIP_CONFLICT: resolve duplicate item relationships before changing eligibility");
        }
        List<Row> assigned = itemRows.stream().filter(row -> addonId.equals(row.addonId())).toList();
        String templateGroup = null;
        if (enabled && (assigned.isEmpty() || assigned.stream().anyMatch(row -> row.templateGroup() == null))) {
            templateGroup = eligibilityTemplateGroup(storeId, itemId);
        }
        int changed = jdbc.update("""
            update menu_item_options set addon_eligible=?, is_active=?,
              template_group_code=coalesce(template_group_code,?), updated_at=current_timestamp
            where menu_item_id=? and store_addon_id=? and store_addon_store_id=?
              and (addon_eligible is distinct from ? or is_active is distinct from ?
                or (template_group_code is null and cast(? as varchar) is not null))
            """, enabled, addon.active() && enabled, templateGroup, itemId, addonId, storeId,
            enabled, addon.active() && enabled, templateGroup);
        if (assigned.isEmpty()) {
            if (!enabled) return;
            jdbc.update("""
                insert into menu_item_options(menu_item_id,option_type,option_code,option_group,
                  name_zh,name_en,price_delta,is_active,store_addon_id,store_addon_store_id,addon_eligible,template_group_code,template_default,sort_order,created_at,updated_at)
                values (?,'addon',?,'ADD_ON',?,?,?,?,?,?,true,?,false,
                  (select coalesce(max(o.sort_order),0)+10 from menu_item_options o where o.menu_item_id=?),
                  current_timestamp,current_timestamp)
                """, itemId, addon.code(), addon.name_zh(), addon.name_en(), addon.price(), addon.active(), addonId, storeId, templateGroup, itemId);
            changed++;
        }
        if (changed > 0) revisions.incrementRevision(storeId);
    }

    /** New eligibility must be visible to a configured item; never revive a disabled group. */
    private String eligibilityTemplateGroup(Long storeId, Long itemId) {
        Boolean managed = jdbc.queryForObject("select template_option_groups_enabled from menu_items where id=? and store_id=?", Boolean.class, itemId, storeId);
        if (!Boolean.TRUE.equals(managed)) return null;
        var candidates = jdbc.queryForList("select group_code,is_active from template_item_option_groups where menu_item_id=? and store_id=? and kind='ADD_ON' order by sort_order,id", itemId, storeId);
        if (candidates.size() == 1 && Boolean.TRUE.equals(candidates.get(0).get("is_active"))) return (String) candidates.get(0).get("group_code");
        if (!candidates.isEmpty()) throw failure("ADDON_TEMPLATE_GROUP_UNAVAILABLE");
        String code = "catalog_addons";
        jdbc.update("""
            insert into template_item_option_groups(store_id,menu_item_id,group_code,name,kind,selection_mode,required,is_active,sort_order)
            values (?,?,?,'Add-ons / 加料','ADD_ON','MULTIPLE',false,true,
              (select coalesce(max(g.sort_order),0)+10 from template_item_option_groups g where g.menu_item_id=?))
            """, storeId, itemId, code, itemId);
        return code;
    }

    /** Owner API apply must match a read-only plan rechecked inside the existing Store lock. */
    @Transactional
    public ReconciliationReport reconcile(Long storeId, boolean dryRun, Long expectedRevision, String expectedFingerprint) {
        lockStore(storeId);
        long revision = currentRevision(storeId);
        String fingerprint = fingerprint(storeId);
        if (!dryRun) {
            if (expectedRevision == null || expectedFingerprint == null || expectedFingerprint.isBlank()) throw failure("ADDON_RECONCILE_PREVIEW_REQUIRED");
            if (revision != expectedRevision || !fingerprint.equals(expectedFingerprint)) throw failure("ADDON_RECONCILE_CHANGED");
            if (!conflicts(plan(storeId, addons(storeId))).isEmpty()) throw failure("ADDON_RECONCILE_CONFLICT");
        }
        return reconcile(storeId, dryRun);
    }

    /** Explicit, bounded reconciliation; GET never materializes or changes eligibility. */
    @Transactional
    public ReconciliationReport reconcile(Long storeId, boolean dryRun) {
        entityManager.flush();
        Long organizationId = dryRun ? organizationId(storeId) : lockStore(storeId);
        List<Group> groups = plan(storeId, addons(storeId));
        int groupCount = 0;
        int optionCount = 0;
        for (Group group : groups) {
            if (group.conflict() != null) continue;
            List<Row> unlinked = group.rows().stream().filter(r -> r.addonId() == null).toList();
            if (unlinked.isEmpty()) continue;
            groupCount++;
            optionCount += unlinked.size();
            if (dryRun) continue;
            Row first = group.rows().get(0);
            boolean anyEligible = group.rows().stream().anyMatch(r -> Boolean.TRUE.equals(eligibility(r)));
            long addonId;
            boolean catalogActive;
            if (group.existing() == null) {
                addonId = insertAddon(storeId, definition(organizationId, group.code()),
                    new Values(first.zh(), first.en(), first.price(), anyEligible));
                catalogActive = anyEligible;
            } else {
                addonId = group.existing().id();
                catalogActive = group.existing().active();
            }
            for (Row row : unlinked) {
                jdbc.update("""
                    update menu_item_options set store_addon_id=?, store_addon_store_id=?, addon_eligible=?, is_active=?,
                      option_group='ADD_ON', updated_at=current_timestamp
                    where id=? and store_addon_id is null
                    """, addonId, storeId, Boolean.TRUE.equals(row.active()), catalogActive && Boolean.TRUE.equals(row.active()), row.id());
            }
        }
        if (!dryRun && optionCount > 0) {
            printingRules.ensureCanonicalAddonFallback(storeId);
            revisions.incrementRevision(storeId);
        }
        return new ReconciliationReport(storeId, dryRun, groupCount, optionCount, conflicts(groups), currentRevision(storeId), fingerprint(storeId));
    }

    /** Explicit current-data business decisions; never called by automatic provisioning. */
    @Transactional
    public ReconciliationReport reconcilePrices(Long storeId, boolean dryRun, Map<String, BigDecimal> prices) {
        lockStore(storeId);
        if (prices == null || prices.isEmpty()) throw failure("ADDON_PRICE_DECISIONS_REQUIRED");
        for (var entry : prices.entrySet()) {
            if (!entry.getKey().equals(normalizeCode(entry.getKey()))) throw failure("ADDON_CODE_INVALID");
            requireNormalCode(entry.getKey());
            if (entry.getValue() == null || entry.getValue().signum() < 0 || entry.getValue().scale() > 2)
                throw failure("ADDON_PRICE_INVALID");
        }
        if (dryRun) return reconcile(storeId, true);
        int changed = 0;
        for (Row row : rows(storeId)) {
            BigDecimal price = prices.get(row.code());
            if (price != null && !moneyEqual(price, row.price())) {
                changed += jdbc.update("update menu_item_options set price_delta=?,updated_at=current_timestamp where id=?", price, row.id());
            }
        }
        for (Addon addon : addons(storeId)) {
            BigDecimal price = prices.get(addon.code());
            if (price != null && !moneyEqual(price, addon.price())) {
                changed += jdbc.update("update store_addons set price=?,updated_at=current_timestamp where id=? and store_id=?", price, addon.id(), storeId);
            }
        }
        if (changed > 0) revisions.incrementRevision(storeId);
        return reconcile(storeId, false);
    }

    private List<Group> plan(Long storeId, List<Addon> catalog) {
        Map<String, List<Row>> grouped = new LinkedHashMap<>();
        for (Row row : rows(storeId)) grouped.computeIfAbsent(row.code(), ignored -> new ArrayList<>()).add(row);
        Map<String, Addon> byCode = new LinkedHashMap<>();
        catalog.forEach(a -> byCode.put(a.code(), a));
        List<Group> result = new ArrayList<>();
        grouped.forEach((code, groupRows) -> {
            boolean ambiguousRelationships = hasAmbiguousRelationships(groupRows);
            if (!ambiguousRelationships && groupRows.stream().noneMatch(r -> r.addonId() == null)) return;
            Addon existing = byCode.get(code);
            String conflict = null;
            Row first = groupRows.get(0);
            if (!validCode(code)) conflict = "ADDON_CODE_INVALID";
            else if (first.zh() == null || first.zh().isBlank() || first.price() == null
                || first.price().signum() < 0 || first.price().scale() > 2) conflict = "ADDON_VALUES_INVALID";
            else if (groupRows.stream().anyMatch(r -> !sameValues(first, r))) conflict = "ADDON_BUSINESS_VALUES_CONFLICT";
            else if (ambiguousRelationships) conflict = "ADDON_RELATIONSHIP_CONFLICT";
            else if (existing != null && (!Objects.equals(existing.name_zh(), first.zh())
                || !Objects.equals(existing.name_en(), first.en()) || !moneyEqual(existing.price(), first.price()))) {
                conflict = "ADDON_CATALOG_VALUES_CONFLICT";
            } else if (existing != null && !existing.active() && groupRows.stream().anyMatch(r -> r.addonId() == null && Boolean.TRUE.equals(r.active())))
                conflict = "ADDON_CATALOG_AVAILABILITY_CONFLICT";
            result.add(new Group(code, groupRows, existing, conflict));
        });
        return result;
    }

    private boolean hasAmbiguousRelationships(List<Row> rows) {
        Map<Long, Row> byItem = new LinkedHashMap<>();
        for (Row row : rows) {
            Row first = byItem.putIfAbsent(row.itemId(), row);
            if (first != null && (!Objects.equals(first.active(), row.active())
                || !Objects.equals(eligibility(first), eligibility(row))
                || !Objects.equals(first.parentOptionId(), row.parentOptionId()))) return true;
        }
        return false;
    }

    private Boolean eligibility(Row row) { return row.addonId() == null ? row.active() : row.eligible(); }

    private List<Conflict> conflicts(List<Group> groups) {
        return groups.stream().filter(g -> g.conflict() != null).map(g -> new Conflict(g.code(),
            g.rows().stream().map(Row::id).toList(), g.rows().stream().map(Row::zh).distinct().toList(),
            g.rows().stream().map(Row::en).distinct().toList(),
            g.rows().stream().map(Row::price).distinct().toList(), g.conflict())).toList();
    }

    // Only unlinked, explicitly inactive legacy rows with no current business
    // references leave the editable catalog. Their DB rows and order snapshots
    // remain intact. Linked inactive eligibility and ambiguous active rows stay.
    private List<Row> rows(Long storeId) {
        return jdbc.query("""
            select o.* from menu_item_options o join menu_items i on i.id=o.menu_item_id
            where i.store_id=?
              and (
                o.store_addon_id is not null or o.is_active is distinct from false
                or exists (
                  select 1 from menu_item_options child
                  where child.parent_option_id=o.id and child.is_active=true
                )
                or exists (
                  select 1 from order_item_options chosen
                  join order_items line on line.id=chosen.order_item_id
                  join orders current_order on current_order.id=line.order_id
                  where (chosen.option_id=o.id or chosen.parent_option_id_snapshot=o.id)
                    and coalesce(current_order.status,'') not in ('completed','cancelled')
                    and coalesce(line.status,'') <> 'cancelled'
                )
              )
            order by o.option_code asc nulls first, o.id asc
            """, (rs, n) -> new Row(rs.getLong("id"), rs.getLong("menu_item_id"), rs.getString("option_code"),
                rs.getString("option_group"), rs.getString("option_type"), rs.getString("name_zh"),
                rs.getString("name_en"), rs.getBigDecimal("price_delta"), rs.getObject("is_active", Boolean.class),
                rs.getObject("store_addon_id", Long.class), rs.getObject("addon_eligible", Boolean.class),
                rs.getObject("parent_option_id", Long.class), rs.getString("template_group_code")), storeId)
            .stream().filter(r -> isAddon(r.group(), r.type(), r.code(), r.zh(), r.en())).toList();
    }

    public static boolean isAddon(String group, String type, String code, String zh, String en) {
        if (isComboOnlyCode(code)) return false;
        if (group != null && !group.isBlank()) return "ADD_ON".equalsIgnoreCase(group.trim());
        return "addon".equalsIgnoreCase(type == null ? null : type.trim())
            && !"combo".equalsIgnoreCase(code == null ? null : code.trim())
            && !"套餐".equals(zh) && !"combo".equalsIgnoreCase(en);
    }

    private Long lockStore(Long storeId) {
        if (storeId == null) throw failure("ADDON_STORE_REQUIRED");
        entityManager.flush();
        revisions.lockStoresInOrder(List.of(storeId));
        return organizationId(storeId);
    }

    private Long organizationId(Long storeId) {
        if (storeId == null) throw failure("ADDON_STORE_REQUIRED");
        List<Long> ids = jdbc.query("select organization_id from stores where id=?",
            (rs, n) -> rs.getObject(1, Long.class), storeId);
        if (ids.isEmpty() || ids.get(0) == null) throw failure("ADDON_STORE_ORGANIZATION_REQUIRED");
        return ids.get(0);
    }

    private long currentRevision(Long storeId) {
        return jdbc.queryForObject("select menu_revision from stores where id=?", Long.class, storeId);
    }

    private String fingerprint(Long storeId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            hashValue(digest, storeId);
            List<Addon> catalog = addons(storeId);
            List<Row> options = rows(storeId);
            hashValue(digest, "catalog");hashValue(digest, catalog.size());
            for (Addon addon : catalog) {
                for (Object value : new Object[]{addon.id(), addon.code(), addon.name_zh(), addon.name_en(), addon.price(), addon.active()}) hashValue(digest, value);
            }
            hashValue(digest, "options");hashValue(digest, options.size());
            for (Row row : options) {
                for (Object value : new Object[]{row.id(),row.itemId(),row.code(),row.group(),row.type(),row.zh(),row.en(),row.price(),row.active(),row.addonId(),row.eligible(),row.parentOptionId(),row.templateGroup()}) hashValue(digest, value);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private void hashValue(MessageDigest digest, Object value) {
        digest.update((byte) (value == null ? 0 : 1));
        if (value == null) return;
        String text = value instanceof BigDecimal amount ? amount.stripTrailingZeros().toPlainString() : value.toString();
        digest.update((text.length() + ":" + text).getBytes(StandardCharsets.UTF_8));
    }

    private Long definition(Long organizationId, String code) {
        // Different Stores may first introduce the same Organization code concurrently.
        if (jdbc.query("select id from organizations where id=? for update", (rs, n) -> rs.getLong(1), organizationId).isEmpty()) {
            throw failure("ADDON_ORGANIZATION_NOT_FOUND");
        }
        List<Long> ids = jdbc.query("select id from organization_addon_definitions where organization_id=? and code=?",
            (rs, n) -> rs.getLong(1), organizationId, code);
        if (!ids.isEmpty()) return ids.get(0);
        return insert("insert into organization_addon_definitions(organization_id,code,created_at,updated_at) values (?,?,current_timestamp,current_timestamp)",
            organizationId, code);
    }

    private long insertAddon(Long storeId, Long definitionId, Values values) {
        return insert("""
            insert into store_addons(store_id,organization_id,organization_addon_definition_id,name_zh,name_en,price,active,created_at,updated_at)
            values (?,?,?,?,?,?,?,current_timestamp,current_timestamp)
            """, storeId, organizationId(storeId), definitionId, values.zh(), values.en(), values.price(), values.active());
    }

    private long insert(String sql, Object... args) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, new String[]{"id"});
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    private List<Addon> addons(Long storeId) {
        PrintingDisplayRuleContext context = printingRules.activeContext(storeId);
        return jdbc.query(SELECT_ADDONS + " where a.store_id=? order by d.code,a.id", (rs, n) -> addon(rs, context), storeId)
            .stream().filter(a -> !isComboOnlyCode(a.code())).toList();
    }

    private static boolean isComboOnlyCode(String code) {
        return "combo_tea_egg".equalsIgnoreCase(code) || "combo_fried_egg".equalsIgnoreCase(code);
    }

    private static void requireNormalCode(String code) {
        if (isComboOnlyCode(code)) throw failure("ADDON_COMBO_COMPONENT_IDENTITY: configure Combo components separately");
    }

    private Addon requireAddon(Long id) {
        List<Addon> found = jdbc.query(SELECT_ADDONS + " where a.id=?", (rs, n) -> addon(rs, null), id);
        if (found.isEmpty()) throw failure("ADDON_NOT_FOUND");
        Addon addon = found.get(0);
        boolean configured = configured(printingRules.activeContext(addon.store_id()), addon.code());
        return new Addon(addon.id(), addon.store_id(), addon.code(), addon.name_zh(), addon.name_en(), addon.price(), addon.active(), configured);
    }

    private Addon addon(ResultSet rs, PrintingDisplayRuleContext context) throws SQLException {
        return new Addon(rs.getLong("id"), rs.getLong("store_id"), rs.getString("code"), rs.getString("name_zh"),
            rs.getString("name_en"), rs.getBigDecimal("price"), rs.getBoolean("active"), configured(context, rs.getString("code")));
    }

    private boolean configured(PrintingDisplayRuleContext context, String code) {
        if (context == null) return false;
        String token = context.resolveModifierToken("MODIFIER_ADD", code, null);
        return token != null && !token.isBlank();
    }

    public static String normalizeCode(String input) {
        String code = input == null ? null : input.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        if (!validCode(code)) throw failure("ADDON_CODE_INVALID: use lower_snake_case letters and digits");
        return code;
    }

    private static boolean validCode(String code) {
        return code != null && code.length() <= 255 && code.matches("[a-z][a-z0-9]*(?:_[a-z0-9]+)*");
    }

    private Values validateValues(WriteRequest request) {
        if (request.name_zh == null || request.name_zh.isBlank() || request.name_zh.trim().length() > 255
            || (request.name_en != null && request.name_en.trim().length() > 255)) throw failure("ADDON_NAMES_INVALID");
        if (request.price == null || request.price.signum() < 0) {
            throw failure("ADDON_PRICE_INVALID");
        }
        BigDecimal price;
        try { price = request.price.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) { throw failure("ADDON_PRICE_INVALID"); }
        if (price.precision() > 38) throw failure("ADDON_PRICE_INVALID");
        if (request.active == null) throw failure("ADDON_ACTIVE_REQUIRED");
        return new Values(request.name_zh.trim(), request.name_en == null ? null : request.name_en.trim(), price, request.active);
    }

    private boolean sameValues(Row a, Row b) {
        return Objects.equals(a.zh(), b.zh()) && Objects.equals(a.en(), b.en()) && moneyEqual(a.price(), b.price());
    }

    private boolean moneyEqual(BigDecimal a, BigDecimal b) { return a != null && b != null && a.compareTo(b) == 0; }
    private static BusinessException failure(String message) { return new BusinessException(message); }
}
