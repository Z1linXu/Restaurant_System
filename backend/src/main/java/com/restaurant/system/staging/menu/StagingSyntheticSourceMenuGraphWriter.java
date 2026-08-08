package com.restaurant.system.staging.menu;

import com.restaurant.system.menu.entity.MenuCategory;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.station.entity.Station;
import com.restaurant.system.station.repository.StationRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Persists one already-validated manifest inside the caller-owned transaction. */
@Component
@Profile("staging-synthetic-bootstrap")
final class StagingSyntheticSourceMenuGraphWriter {

    private final MenuCategoryRepository categoryRepository;
    private final StationRepository stationRepository;
    private final MenuItemRepository itemRepository;
    private final MenuItemOptionRepository optionRepository;

    StagingSyntheticSourceMenuGraphWriter(
        MenuCategoryRepository categoryRepository,
        StationRepository stationRepository,
        MenuItemRepository itemRepository,
        MenuItemOptionRepository optionRepository
    ) {
        this.categoryRepository = categoryRepository;
        this.stationRepository = stationRepository;
        this.itemRepository = itemRepository;
        this.optionRepository = optionRepository;
    }

    void persist(Long storeId, StagingSyntheticSourceMenuManifest manifest) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Long> categoryIds = new LinkedHashMap<>();
        for (StagingSyntheticSourceMenuManifest.Category source : manifest.categories()) {
            MenuCategory category = new MenuCategory();
            category.store_id = storeId;
            category.code = source.code();
            category.name_zh = source.nameZh();
            category.name_en = source.nameEn();
            category.sort_order = source.sortOrder();
            category.is_active = source.active();
            category.created_at = now;
            category.updated_at = now;
            category = categoryRepository.save(category);
            categoryIds.put(source.code(), category.id);
        }

        Map<String, Long> stationIds = new LinkedHashMap<>();
        for (StagingSyntheticSourceMenuManifest.Station source : manifest.stations()) {
            Station station = new Station();
            station.store_id = storeId;
            station.code = source.code();
            station.name = source.name();
            station.sort_order = source.sortOrder();
            station.is_active = source.active();
            station.created_at = now;
            station.updated_at = now;
            station = stationRepository.save(station);
            stationIds.put(source.code(), station.id);
        }

        Map<String, Long> itemIds = new LinkedHashMap<>();
        for (StagingSyntheticSourceMenuManifest.Item source : manifest.items()) {
            MenuItem item = new MenuItem();
            item.store_id = storeId;
            item.category_id = categoryIds.get(source.categoryCode());
            item.station_id = stationIds.get(source.stationCode());
            item.sku = source.sku();
            item.item_type = source.itemType();
            item.name_zh = source.nameZh();
            item.name_en = source.nameEn();
            item.base_price = source.basePrice();
            item.cost_per_item = source.costPerItem();
            item.is_active = source.active();
            item.is_sold_out = source.soldOut();
            item.sort_order = source.sortOrder();
            item.created_at = now;
            item.updated_at = now;
            item = itemRepository.save(item);
            itemIds.put(source.sku(), item.id);
        }

        Map<String, Long> optionIds = new LinkedHashMap<>();
        List<StagingSyntheticSourceMenuManifest.Option> pending = new ArrayList<>(manifest.options());
        while (!pending.isEmpty()) {
            int before = pending.size();
            pending.removeIf(source -> persistOption(source, itemIds, optionIds, now));
            if (pending.size() == before) {
                throw new StagingSyntheticSourceMenuException(
                    "STG005_SOURCE_MENU_PARENT_GRAPH_INVALID",
                    "Synthetic option parent graph cannot be persisted"
                );
            }
        }
        optionRepository.flush();
    }

    private boolean persistOption(
        StagingSyntheticSourceMenuManifest.Option source,
        Map<String, Long> itemIds,
        Map<String, Long> optionIds,
        LocalDateTime now
    ) {
        Long parentId = source.parentOptionCode() == null
            ? null
            : optionIds.get(optionKey(source.itemSku(), source.parentOptionCode()));
        if (source.parentOptionCode() != null && parentId == null) {
            return false;
        }
        MenuItemOption option = new MenuItemOption();
        option.menu_item_id = itemIds.get(source.itemSku());
        option.option_type = source.optionType();
        option.option_group = source.optionGroup();
        option.option_code = source.optionCode();
        option.parent_option_id = parentId;
        option.sort_order = source.sortOrder();
        option.name_zh = source.nameZh();
        option.name_en = source.nameEn();
        option.price_delta = source.priceDelta();
        option.is_active = source.active();
        option.created_at = now;
        option.updated_at = now;
        option = optionRepository.save(option);
        optionIds.put(optionKey(source.itemSku(), source.optionCode()), option.id);
        return true;
    }

    private String optionKey(String itemSku, String optionCode) {
        return itemSku + "\u0000" + optionCode;
    }
}
