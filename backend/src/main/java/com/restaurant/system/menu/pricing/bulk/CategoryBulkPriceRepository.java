package com.restaurant.system.menu.pricing.bulk;

import com.restaurant.system.common.auth.AuthenticatedUser;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CategoryBulkPriceRepository {
    public record CategoryRow(Long id, Long store_id, String code, String name_zh, String name_en,
        String name_fr, Boolean active) {}
    public record ItemRow(Long id, Long store_id, Long category_id, String sku, String name_zh,
        String name_en, String name_fr, String item_type, String refill_category_code,
        Boolean active, BigDecimal base_price, LocalDateTime updated_at) {}
    public record StoredPreview(String preview_id, Long store_id, String body_hash, long menu_revision,
        String before_fingerprint, Instant expires_at) {}
    public record LedgerEntry(String body_hash, String response_json) {}

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public CategoryBulkPriceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    public boolean storeExists(Long storeId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from stores where id=?)", Boolean.class, storeId));
    }

    public boolean bulkEnabled(Long storeId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from store_pricing_policies
                where store_id=? and category_bulk_pricing_enabled=true)
            """, Boolean.class, storeId));
    }

    public boolean standardSizeEnabled(Long storeId) {
        // Template SIZE groups can describe noodle shapes; only actual canonical, non-template Size leaves qualify.
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from menu_item_options o
                join menu_items i on i.id=o.menu_item_id
                join store_pricing_policies p on p.store_id=i.store_id
                where i.store_id=? and i.is_active=true and o.is_active=true
                  and o.template_group_code is null and o.parent_option_id is null
                  and o.store_addon_id is null
                  and lower(trim(coalesce(o.option_code,''))) in ('size_small','size_regular','size_large')
                  and (upper(trim(coalesce(o.option_group,'')))='SIZE'
                       or (trim(coalesce(o.option_group,''))='' and lower(trim(coalesce(o.option_type,'')))='size')))
            """, Boolean.class, storeId));
    }

    public long currentRevision(Long storeId) {
        Long value = jdbc.queryForObject("select coalesce(menu_revision,1) from stores where id=?", Long.class, storeId);
        return value == null ? 1L : value;
    }

    public List<CategoryRow> categories(List<Long> categoryIds) {
        return named.query("select id,store_id,code,name_zh,name_en,name_fr,is_active from menu_categories where id in (:ids) order by id",
            Map.of("ids", categoryIds), (rs, n) -> new CategoryRow(rs.getLong("id"), rs.getObject("store_id", Long.class),
                rs.getString("code"), rs.getString("name_zh"), rs.getString("name_en"), rs.getString("name_fr"), rs.getObject("is_active", Boolean.class)));
    }

    public List<ItemRow> items(List<Long> categoryIds) {
        // Do not conceal an invalid cross-Store category relationship by filtering it out.
        return named.query("""
            select id,store_id,category_id,sku,name_zh,name_en,name_fr,item_type,refill_category_code,is_active,base_price,updated_at
            from menu_items where category_id in (:ids) order by id
            """, Map.of("ids", categoryIds), (rs, n) -> new ItemRow(rs.getLong("id"), rs.getObject("store_id", Long.class),
                rs.getObject("category_id", Long.class), rs.getString("sku"), rs.getString("name_zh"), rs.getString("name_en"),
                rs.getString("name_fr"), rs.getString("item_type"), rs.getString("refill_category_code"),
                rs.getObject("is_active", Boolean.class), rs.getBigDecimal("base_price"),
                rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime()));
    }

    public void savePreview(StoredPreview preview) {
        requireOne(jdbc.update("""
            insert into store_category_price_adjustment_previews(preview_id,store_id,body_hash,menu_revision,before_fingerprint,expires_at)
            values (?,?,?,?,?,?)
            """, preview.preview_id(), preview.store_id(), preview.body_hash(), preview.menu_revision(),
                preview.before_fingerprint(), Timestamp.from(preview.expires_at())));
    }

    public Optional<StoredPreview> preview(String id, Long storeId) {
        return jdbc.query("""
            select preview_id,store_id,body_hash,menu_revision,before_fingerprint,expires_at
            from store_category_price_adjustment_previews where preview_id=? and store_id=?
            """, (rs, n) -> new StoredPreview(rs.getString("preview_id"), rs.getLong("store_id"), rs.getString("body_hash"),
                rs.getLong("menu_revision"), rs.getString("before_fingerprint"), rs.getTimestamp("expires_at").toInstant()),
            id, storeId).stream().findFirst();
    }

    public Optional<LedgerEntry> ledger(Long storeId, String key) {
        return jdbc.query("select body_hash,response_json from store_category_price_adjustments where store_id=? and idempotency_key=?",
            (rs, n) -> new LedgerEntry(rs.getString("body_hash"), rs.getString("response_json")), storeId, key).stream().findFirst();
    }

    public int updateBasePrice(Long storeId, CategoryBulkPriceModels.PriceChange item) {
        return jdbc.update("""
            update menu_items set base_price=?,updated_at=current_timestamp
            where id=? and store_id=? and category_id=? and base_price=?
            """, item.new_base_price(), item.item_id(), storeId, item.category_id(), item.old_base_price());
    }

    public void insertAudit(Long storeId, AuthenticatedUser actor, String metadata) {
        // AuditLogService suppresses failures. This operation requires the audit and price changes to commit together.
        requireOne(jdbc.update("""
            insert into audit_logs(store_id,actor_user_id,actor_name_snapshot,actor_role_snapshot,action,entity_type,entity_id,
                summary,metadata_json,created_at)
            values (?,?,?,?, 'MENU_CATEGORY_BASE_PRICES_ADJUSTED','STORE',?, ?,?,current_timestamp)
            """, storeId, actor.userId(), actor.fullName(), actor.roleCode(), storeId,
            "Adjusted category base prices for future orders", metadata));
    }

    public void saveLedger(Long storeId, String key, String bodyHash, String previewId, String response) {
        requireOne(jdbc.update("""
            insert into store_category_price_adjustments(store_id,idempotency_key,body_hash,preview_id,response_json)
            values (?,?,?,?,?)
            """, storeId, key, bodyHash, previewId, response));
    }

    private void requireOne(int rows) {
        if (rows != 1) throw new IllegalStateException("Category price adjustment durable write failed");
    }
}
