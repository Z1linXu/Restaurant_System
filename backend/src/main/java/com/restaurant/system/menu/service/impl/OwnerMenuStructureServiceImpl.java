package com.restaurant.system.menu.service.impl;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.combo.StoreComboComponent;
import com.restaurant.system.menu.combo.StoreComboComponentRepository;
import com.restaurant.system.menu.dto.MenuCategoryUpsertRequest;
import com.restaurant.system.menu.dto.StationUpsertRequest;
import com.restaurant.system.menu.entity.MenuCategory;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.menu.service.OwnerMenuStructureService;
import com.restaurant.system.station.entity.Station;
import com.restaurant.system.station.repository.StationRepository;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerMenuStructureServiceImpl implements OwnerMenuStructureService {

    private static final Set<String> STATION_TYPES = Set.of("KITCHEN", "BAR", "COLD", "PASS", "OTHER");
    private static final String LEGACY_COMBO_SIDE_TASK = "LEGACY_COMBO_SIDE_TASK";

    private final MenuCategoryRepository menuCategoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final StationRepository stationRepository;
    private final StoreComboComponentRepository storeComboComponentRepository;
    private final MenuRevisionService menuRevisionService;

    public OwnerMenuStructureServiceImpl(
        MenuCategoryRepository menuCategoryRepository,
        MenuItemRepository menuItemRepository,
        StationRepository stationRepository,
        StoreComboComponentRepository storeComboComponentRepository,
        MenuRevisionService menuRevisionService
    ) {
        this.menuCategoryRepository = menuCategoryRepository;
        this.menuItemRepository = menuItemRepository;
        this.stationRepository = stationRepository;
        this.storeComboComponentRepository = storeComboComponentRepository;
        this.menuRevisionService = menuRevisionService;
    }

    @Override
    @Transactional
    public MenuCategory createCategory(Long storeId, MenuCategoryUpsertRequest request) {
        requireStoreId(storeId);
        requireStoreMatch(storeId, request == null ? null : request.store_id);
        requireName(request == null ? null : request.name_zh, request == null ? null : request.name_en, "CATEGORY_NAME_REQUIRED");
        menuRevisionService.lockStoresInOrder(List.of(storeId));

        LocalDateTime now = LocalDateTime.now();
        MenuCategory category = new MenuCategory();
        category.store_id = storeId;
        category.code = nextCode(storeId, "CATEGORY", firstNonBlank(request.name_en, request.name_zh), true);
        category.name_zh = cleanRequired(request.name_zh, "CATEGORY_NAME_ZH_REQUIRED");
        category.name_en = cleanRequired(request.name_en, "CATEGORY_NAME_EN_REQUIRED");
        category.sort_order = request.sort_order == null ? nextCategorySortOrder(storeId) : request.sort_order;
        category.is_active = bool(request.enabled, request.is_active, true);
        category.created_at = now;
        category.updated_at = now;

        MenuCategory saved = menuCategoryRepository.save(category);
        menuRevisionService.incrementRevision(storeId);
        return saved;
    }

    @Override
    @Transactional
    public MenuCategory updateCategory(Long storeId, Long categoryId, MenuCategoryUpsertRequest request) {
        requireStoreId(storeId);
        requireStoreMatch(storeId, request == null ? null : request.store_id);
        requireId(categoryId, "CATEGORY_ID_REQUIRED");
        requireName(request == null ? null : request.name_zh, request == null ? null : request.name_en, "CATEGORY_NAME_REQUIRED");
        menuRevisionService.lockStoresInOrder(List.of(storeId));

        MenuCategory category = menuCategoryRepository.findById(categoryId)
            .orElseThrow(() -> new BusinessException("CATEGORY_NOT_FOUND"));
        if (!storeId.equals(category.store_id)) {
            throw new BusinessException("CATEGORY_STORE_MISMATCH");
        }
        Boolean nextActive = bool(request.enabled, request.is_active, true);

        category.name_zh = cleanRequired(request.name_zh, "CATEGORY_NAME_ZH_REQUIRED");
        category.name_en = cleanRequired(request.name_en, "CATEGORY_NAME_EN_REQUIRED");
        category.sort_order = request.sort_order == null ? category.sort_order : request.sort_order;
        category.is_active = nextActive;
        category.updated_at = LocalDateTime.now();
        MenuCategory saved = menuCategoryRepository.save(category);
        menuRevisionService.incrementRevision(storeId);
        return saved;
    }

    @Override
    @Transactional
    public List<MenuCategory> deleteCategory(Long storeId, Long categoryId) {
        requireStoreId(storeId);
        requireId(categoryId, "CATEGORY_ID_REQUIRED");
        menuRevisionService.lockStoresInOrder(List.of(storeId));

        MenuCategory category = menuCategoryRepository.findById(categoryId)
            .orElseThrow(() -> new BusinessException("CATEGORY_NOT_FOUND"));
        if (!storeId.equals(category.store_id)) {
            throw new BusinessException("CATEGORY_STORE_MISMATCH");
        }
        if (menuItemRepository.countByStoreIdAndCategoryId(storeId, category.id) > 0) {
            throw new BusinessException("CATEGORY_NOT_EMPTY");
        }
        menuCategoryRepository.delete(category);
        menuRevisionService.incrementRevision(storeId);
        return menuCategoryRepository.findAllByStoreIdOrderByIdAsc(storeId);
    }

    @Override
    @Transactional
    public Station createStation(Long storeId, StationUpsertRequest request) {
        requireStoreId(storeId);
        requireStoreMatch(storeId, request == null ? null : request.store_id);
        requireName(request == null ? null : request.name_zh, request == null ? null : request.name_en, "STATION_NAME_REQUIRED");
        menuRevisionService.lockStoresInOrder(List.of(storeId));

        LocalDateTime now = LocalDateTime.now();
        Station station = new Station();
        station.store_id = storeId;
        station.code = nextCode(storeId, "STATION", firstNonBlank(request.name_en, request.name_zh), false);
        station.name_zh = cleanRequired(request.name_zh, "STATION_NAME_ZH_REQUIRED");
        station.name_en = cleanRequired(request.name_en, "STATION_NAME_EN_REQUIRED");
        station.name = station.name_zh;
        station.station_type = normalizeStationType(request.station_type);
        station.sort_order = request.sort_order == null ? nextStationSortOrder(storeId) : request.sort_order;
        station.is_active = bool(request.enabled, request.is_active, true);
        station.created_at = now;
        station.updated_at = now;

        Station saved = stationRepository.save(station);
        menuRevisionService.incrementRevision(storeId);
        return saved;
    }

    @Override
    @Transactional
    public Station updateStation(Long storeId, Long stationId, StationUpsertRequest request) {
        requireStoreId(storeId);
        requireStoreMatch(storeId, request == null ? null : request.store_id);
        requireId(stationId, "STATION_ID_REQUIRED");
        requireName(request == null ? null : request.name_zh, request == null ? null : request.name_en, "STATION_NAME_REQUIRED");
        menuRevisionService.lockStoresInOrder(List.of(storeId));

        Station station = stationRepository.findById(stationId)
            .orElseThrow(() -> new BusinessException("STATION_NOT_FOUND"));
        if (!storeId.equals(station.store_id)) {
            throw new BusinessException("STATION_STORE_MISMATCH");
        }
        Boolean nextActive = bool(request.enabled, request.is_active, true);
        if (!Boolean.TRUE.equals(nextActive)) {
            validateStationSafeToDisable(storeId, station);
        }

        station.name_zh = cleanRequired(request.name_zh, "STATION_NAME_ZH_REQUIRED");
        station.name_en = cleanRequired(request.name_en, "STATION_NAME_EN_REQUIRED");
        station.name = station.name_zh;
        station.station_type = normalizeStationType(request.station_type);
        station.sort_order = request.sort_order == null ? station.sort_order : request.sort_order;
        station.is_active = nextActive;
        station.updated_at = LocalDateTime.now();
        Station saved = stationRepository.save(station);
        menuRevisionService.incrementRevision(storeId);
        return saved;
    }

    @Override
    @Transactional
    public List<Station> deleteStation(Long storeId, Long stationId) {
        requireStoreId(storeId);
        requireId(stationId, "STATION_ID_REQUIRED");
        menuRevisionService.lockStoresInOrder(List.of(storeId));

        Station station = stationRepository.findById(stationId)
            .orElseThrow(() -> new BusinessException("STATION_NOT_FOUND"));
        if (!storeId.equals(station.store_id)) {
            throw new BusinessException("STATION_STORE_MISMATCH");
        }
        validateStationSafeToDisable(storeId, station);
        if (menuItemRepository.countByStoreIdAndStationId(storeId, station.id) > 0) {
            throw new BusinessException("STATION_IN_USE");
        }
        stationRepository.delete(station);
        menuRevisionService.incrementRevision(storeId);
        return stationRepository.findAllByStoreIdOrderByIdAsc(storeId);
    }

    private void validateStationSafeToDisable(Long storeId, Station station) {
        if (menuItemRepository.countActiveByStoreIdAndStationId(storeId, station.id) > 0) {
            throw new BusinessException("STATION_HAS_ACTIVE_ITEMS");
        }
        if ("COLD".equalsIgnoreCase(blankToEmpty(station.code)) && hasEnabledLegacyComboSideTask(storeId)) {
            throw new BusinessException("STATION_HAS_LEGACY_COMBO_SIDE_ROUTING");
        }
    }

    private boolean hasEnabledLegacyComboSideTask(Long storeId) {
        return storeComboComponentRepository.findActiveByStoreIdOrdered(storeId).stream()
            .filter(component -> "COMBO_SIDE".equalsIgnoreCase(blankToEmpty(component.component_group)))
            .filter(component -> Boolean.TRUE.equals(component.enabled))
            .anyMatch(component -> LEGACY_COMBO_SIDE_TASK.equalsIgnoreCase(blankToEmpty(component.business_behavior)));
    }

    private String nextCode(Long storeId, String prefix, String source, boolean category) {
        String base = prefix + "_" + slug(source);
        String candidate = base;
        int suffix = 2;
        while (codeExists(storeId, candidate, category)) {
            candidate = base + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private boolean codeExists(Long storeId, String code, boolean category) {
        return category
            ? !menuCategoryRepository.findAllByStoreIdAndCode(storeId, code).isEmpty()
            : !stationRepository.findAllByStoreIdAndCode(storeId, code).isEmpty();
    }

    private int nextCategorySortOrder(Long storeId) {
        return menuCategoryRepository.findAllByStoreIdOrderByIdAsc(storeId).stream()
            .map(category -> category.sort_order == null ? 0 : category.sort_order)
            .max(Integer::compareTo)
            .orElse(0) + 10;
    }

    private int nextStationSortOrder(Long storeId) {
        return stationRepository.findAllByStoreIdOrderByIdAsc(storeId).stream()
            .map(station -> station.sort_order == null ? 0 : station.sort_order)
            .max(Integer::compareTo)
            .orElse(0) + 10;
    }

    private String normalizeStationType(String value) {
        String normalized = blankToEmpty(value).isBlank() ? "KITCHEN" : value.trim().toUpperCase(Locale.ROOT);
        if (!STATION_TYPES.contains(normalized)) {
            throw new BusinessException("STATION_TYPE_UNSUPPORTED");
        }
        return normalized;
    }

    private String slug(String source) {
        String normalized = Normalizer.normalize(blankToEmpty(source), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
        if (!normalized.isBlank()) {
            return normalized;
        }
        return "CUSTOM_" + Integer.toUnsignedString(fnv1a32(source), 36).toUpperCase(Locale.ROOT);
    }

    private int fnv1a32(String value) {
        int hash = 0x811c9dc5;
        for (byte current : blankToEmpty(value).getBytes(StandardCharsets.UTF_8)) {
            hash ^= current & 0xff;
            hash *= 0x01000193;
        }
        return hash;
    }

    private Boolean bool(Boolean enabled, Boolean isActive, boolean fallback) {
        if (enabled != null) {
            return enabled;
        }
        if (isActive != null) {
            return isActive;
        }
        return fallback;
    }

    private String cleanRequired(String value, String code) {
        String cleaned = blankToEmpty(value);
        if (cleaned.isBlank()) {
            throw new BusinessException(code);
        }
        return cleaned;
    }

    private void requireName(String zh, String en, String code) {
        if (blankToEmpty(zh).isBlank() || blankToEmpty(en).isBlank()) {
            throw new BusinessException(code);
        }
    }

    private void requireStoreMatch(Long storeId, Long requestStoreId) {
        if (requestStoreId != null && !requestStoreId.equals(storeId)) {
            throw new BusinessException("STORE_ID_MISMATCH");
        }
    }

    private void requireStoreId(Long storeId) {
        if (storeId == null) {
            throw new BusinessException("STORE_ID_REQUIRED");
        }
    }

    private void requireId(Long id, String code) {
        if (id == null) {
            throw new BusinessException(code);
        }
    }

    private String firstNonBlank(String first, String second) {
        String cleanedFirst = blankToEmpty(first);
        return cleanedFirst.isBlank() ? blankToEmpty(second) : cleanedFirst;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
