package com.restaurant.system.menu.pricing;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StorePricingPolicyRepository extends JpaRepository<StorePricingPolicy, Long> {

    @Query("select policy from StorePricingPolicy policy where policy.store_id = :storeId")
    Optional<StorePricingPolicy> findByStoreId(@Param("storeId") Long storeId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        update menu_item_options option_row
        set price_delta = case
                when lower(coalesce(option_row.option_code, '')) = 'size_small'
                  or option_row.name_zh = '小碗'
                  or lower(coalesce(option_row.name_en, '')) = 'small'
                then (select size_small_delta from store_pricing_policies where store_id = :storeId)
                when lower(coalesce(option_row.option_code, '')) = 'size_regular'
                  or option_row.name_zh = '中碗'
                  or lower(coalesce(option_row.name_en, '')) = 'regular'
                then (select size_regular_delta from store_pricing_policies where store_id = :storeId)
                when lower(coalesce(option_row.option_code, '')) = 'size_large'
                  or option_row.name_zh = '大碗'
                  or lower(coalesce(option_row.name_en, '')) = 'large'
                then (select size_large_delta from store_pricing_policies where store_id = :storeId)
                when lower(coalesce(option_row.option_group, '')) = 'combo'
                  or lower(coalesce(option_row.option_code, '')) = 'combo'
                  or (
                    lower(coalesce(option_row.option_type, '')) = 'addon'
                    and option_row.name_zh = '套餐'
                  )
                  or (
                    lower(coalesce(option_row.option_type, '')) = 'addon'
                    and lower(coalesce(option_row.name_en, '')) = 'combo'
                  )
                then (select combo_delta from store_pricing_policies where store_id = :storeId)
                else option_row.price_delta
            end,
            updated_at = current_timestamp
        from menu_items item
        where item.id = option_row.menu_item_id
          and item.store_id = :storeId
          and (
            lower(coalesce(option_row.option_group, '')) = 'size'
            or lower(coalesce(option_row.option_type, '')) = 'size'
            or lower(coalesce(option_row.option_group, '')) = 'combo'
            or lower(coalesce(option_row.option_code, '')) = 'combo'
            or (
                lower(coalesce(option_row.option_type, '')) = 'addon'
                and option_row.name_zh = '套餐'
            )
            or (
                lower(coalesce(option_row.option_type, '')) = 'addon'
                and lower(coalesce(option_row.name_en, '')) = 'combo'
            )
          )
        """, nativeQuery = true)
    int mirrorPolicyToSizeAndComboOptions(@Param("storeId") Long storeId);
}
