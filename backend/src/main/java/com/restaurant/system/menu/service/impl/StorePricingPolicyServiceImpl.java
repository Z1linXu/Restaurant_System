package com.restaurant.system.menu.service.impl;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.dto.MenuItemComboPolicyRequest;
import com.restaurant.system.menu.dto.MenuItemOptionAdminResponse;
import com.restaurant.system.menu.dto.MenuItemSizeConfigurationRequest;
import com.restaurant.system.menu.dto.StorePricingPolicyPreviewRequest;
import com.restaurant.system.menu.dto.StorePricingPolicyPreviewResponse;
import com.restaurant.system.menu.dto.StorePricingPolicyResponse;
import com.restaurant.system.menu.dto.StorePricingPolicyUpdateRequest;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.pricing.StandardSize;
import com.restaurant.system.menu.pricing.StorePricingPolicy;
import com.restaurant.system.menu.pricing.StorePricingPolicyRepository;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.menu.service.StorePricingPolicyService;
import com.restaurant.system.user.repository.StoreRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StorePricingPolicyServiceImpl implements StorePricingPolicyService {

    private static final String GROUP_SIZE = "SIZE";
    private static final String GROUP_COMBO = "COMBO";
    private static final String TYPE_SIZE = "size";
    private static final String TYPE_ADDON = "addon";

    private final StorePricingPolicyRepository storePricingPolicyRepository;
    private final StoreRepository storeRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuItemOptionRepository menuItemOptionRepository;
    private final MenuRevisionService menuRevisionService;

    public StorePricingPolicyServiceImpl(
        StorePricingPolicyRepository storePricingPolicyRepository,
        StoreRepository storeRepository,
        MenuItemRepository menuItemRepository,
        MenuItemOptionRepository menuItemOptionRepository,
        MenuRevisionService menuRevisionService
    ) {
        this.storePricingPolicyRepository = storePricingPolicyRepository;
        this.storeRepository = storeRepository;
        this.menuItemRepository = menuItemRepository;
        this.menuItemOptionRepository = menuItemOptionRepository;
        this.menuRevisionService = menuRevisionService;
    }

    @Override
    @Transactional(readOnly = true)
    public StorePricingPolicy getEffectivePolicy(Long storeId) {
        requireStore(storeId);
        return storePricingPolicyRepository.findByStoreId(storeId)
            .orElseGet(() -> defaultPolicy(storeId));
    }

    @Override
    @Transactional(readOnly = true)
    public StorePricingPolicyResponse getPolicyResponse(Long storeId) {
        return toResponse(getEffectivePolicy(storeId));
    }

    @Override
    @Transactional(readOnly = true)
    public StorePricingPolicyPreviewResponse preview(Long storeId, StorePricingPolicyPreviewRequest request) {
        StorePricingPolicy current = getEffectivePolicy(storeId);
        StorePricingPolicy proposed = merge(current, request, false);
        List<MenuItem> items = menuItemRepository.findAllByStoreIdOrderByIdAsc(storeId);
        List<Long> itemIds = items.stream().map(item -> item.id).toList();
        List<MenuItemOption> options = itemIds.isEmpty()
            ? List.of()
            : menuItemOptionRepository.findAllByMenuItemIdsOrdered(itemIds);
        Map<Long, List<MenuItemOption>> optionsByItemId = groupOptions(options);

        StorePricingPolicyPreviewResponse response = new StorePricingPolicyPreviewResponse();
        response.store_id = storeId;
        response.current_policy = toResponse(current);
        response.proposed_policy = toResponse(proposed);

        for (StandardSize size : StandardSize.values()) {
            BigDecimal oldDelta = deltaForSize(current, size);
            BigDecimal newDelta = deltaForSize(proposed, size);
            if (moneyEquals(oldDelta, newDelta)) {
                continue;
            }
            StorePricingPolicyPreviewResponse.ImpactGroup group = impactGroup(size.semantic + "_DELTA", oldDelta, newDelta);
            for (MenuItem item : items) {
                boolean affected = optionsByItemId.getOrDefault(item.id, List.of()).stream()
                    .filter(option -> Boolean.TRUE.equals(option.is_active))
                    .anyMatch(option -> size.equals(resolveSize(option).orElse(null)));
                if (affected) {
                    group.sample_items.add(impactItem(item, oldDelta, newDelta));
                }
            }
            group.affected_item_count = group.sample_items.size();
            response.impact_groups.add(group);
        }

        if (!moneyEquals(current.combo_delta, proposed.combo_delta)) {
            StorePricingPolicyPreviewResponse.ImpactGroup group = impactGroup("COMBO_DELTA", current.combo_delta, proposed.combo_delta);
            for (MenuItem item : items) {
                boolean affected = optionsByItemId.getOrDefault(item.id, List.of()).stream()
                    .filter(option -> Boolean.TRUE.equals(option.is_active))
                    .anyMatch(this::isComboUpcharge);
                if (affected) {
                    group.sample_items.add(impactItem(item, current.combo_delta, proposed.combo_delta));
                }
            }
            group.affected_item_count = group.sample_items.size();
            response.impact_groups.add(group);
        }

        return response;
    }

    @Override
    @Transactional
    public StorePricingPolicyResponse updatePolicy(Long storeId, StorePricingPolicyUpdateRequest request) {
        requireStore(storeId);
        menuRevisionService.lockStoresInOrder(List.of(storeId));
        StorePricingPolicy policy = storePricingPolicyRepository.findByStoreId(storeId)
            .orElseGet(() -> defaultPolicy(storeId));
        policy = merge(policy, request, true);
        policy.policy_revision = policy.policy_revision == null ? 1L : policy.policy_revision + 1L;
        policy.updated_at = now();
        if (policy.created_at == null) {
            policy.created_at = policy.updated_at;
        }
        StorePricingPolicy saved = storePricingPolicyRepository.save(policy);
        storePricingPolicyRepository.mirrorPolicyToSizeAndComboOptions(storeId);
        menuRevisionService.incrementRevision(storeId);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public List<MenuItemOptionAdminResponse> updateSizeConfiguration(Long itemId, MenuItemSizeConfigurationRequest request) {
        MenuItem item = loadItem(itemId);
        menuRevisionService.lockStoresInOrder(List.of(item.store_id));
        StorePricingPolicy policy = getEffectivePolicy(item.store_id);
        Set<StandardSize> enabled = parseEnabledSizes(request);
        String defaultCode = request == null ? null : blankToNull(request.default_size_code);
        validateDefault(enabled, defaultCode);

        List<MenuItemOption> existing = menuItemOptionRepository.findAllByMenuItemIdOrdered(itemId);
        Map<StandardSize, MenuItemOption> canonical = new LinkedHashMap<>();
        for (MenuItemOption option : existing) {
            resolveSize(option).ifPresent(size -> canonical.merge(size, option, this::preferCanonicalSizeOption));
        }

        int sort = 10;
        Set<Long> canonicalOptionIds = new HashSet<>();
        for (StandardSize size : defaultOrderedSizes(enabled, defaultCode)) {
            MenuItemOption option = canonical.computeIfAbsent(size, ignored -> new MenuItemOption());
            boolean creating = option.id == null;
            option.menu_item_id = itemId;
            option.option_type = TYPE_SIZE;
            option.option_group = GROUP_SIZE;
            option.option_code = size.code;
            option.name_zh = size.labelZh;
            option.name_en = size.labelEn;
            option.parent_option_id = null;
            option.sort_order = sort;
            option.price_delta = deltaForSize(policy, size);
            option.is_active = enabled.contains(size);
            option.updated_at = now();
            if (creating) {
                option.created_at = option.updated_at;
            }
            sort += 10;
            MenuItemOption saved = menuItemOptionRepository.save(option);
            if (saved.id != null) {
                canonicalOptionIds.add(saved.id);
            }
        }

        // Preserve legacy/noncanonical/duplicate Size rows for historical references,
        // but never expose them as active Owner/Ordering choices.
        for (MenuItemOption option : existing) {
            boolean duplicateOrLegacySize = isSizeOption(option)
                && (option.id == null || !canonicalOptionIds.contains(option.id));
            if (duplicateOrLegacySize && Boolean.TRUE.equals(option.is_active)) {
                option.is_active = false;
                option.updated_at = now();
                menuItemOptionRepository.save(option);
            }
        }

        storePricingPolicyRepository.mirrorPolicyToSizeAndComboOptions(item.store_id);
        menuRevisionService.incrementRevision(item.store_id);
        return menuItemOptionRepository.findAllByMenuItemIdOrdered(itemId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    @Transactional
    public List<MenuItemOptionAdminResponse> updateComboPolicy(Long itemId, MenuItemComboPolicyRequest request) {
        MenuItem item = loadItem(itemId);
        menuRevisionService.lockStoresInOrder(List.of(item.store_id));
        StorePricingPolicy policy = getEffectivePolicy(item.store_id);
        boolean allowed = request != null && Boolean.TRUE.equals(request.combo_allowed);
        List<MenuItemOption> options = menuItemOptionRepository.findAllByMenuItemIdOrdered(itemId);
        MenuItemOption combo = options.stream()
            .filter(this::isComboUpcharge)
            .findFirst()
            .orElseGet(MenuItemOption::new);
        boolean creating = combo.id == null;
        combo.menu_item_id = itemId;
        combo.option_type = TYPE_ADDON;
        combo.option_group = GROUP_COMBO;
        combo.option_code = "combo";
        combo.name_zh = "套餐";
        combo.name_en = "Combo";
        combo.parent_option_id = null;
        combo.sort_order = combo.sort_order == null ? nextSortOrder(options, GROUP_COMBO) : combo.sort_order;
        combo.price_delta = policy.combo_delta;
        combo.is_active = allowed;
        combo.updated_at = now();
        if (creating) {
            combo.created_at = combo.updated_at;
        }
        MenuItemOption savedCombo = menuItemOptionRepository.save(combo);
        for (MenuItemOption option : options) {
            if (!option.id.equals(savedCombo.id) && isComboUpcharge(option) && Boolean.TRUE.equals(option.is_active)) {
                option.is_active = false;
                option.updated_at = now();
                menuItemOptionRepository.save(option);
            }
        }
        menuRevisionService.incrementRevision(item.store_id);
        return menuItemOptionRepository.findAllByMenuItemIdOrdered(itemId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    public MenuItemOption applyEffectiveCatalogPricing(MenuItemOption option, StorePricingPolicy policy) {
        if (option == null || policy == null) {
            return option;
        }
        Optional<StandardSize> size = resolveSize(option);
        if (size.isPresent()) {
            MenuItemOption copy = copy(option);
            StandardSize standardSize = size.get();
            copy.option_type = TYPE_SIZE;
            copy.option_group = GROUP_SIZE;
            copy.option_code = standardSize.code;
            copy.name_zh = standardSize.labelZh;
            copy.name_en = standardSize.labelEn;
            copy.price_delta = deltaForSize(policy, standardSize);
            return copy;
        }
        if (isComboUpcharge(option)) {
            MenuItemOption copy = copy(option);
            copy.option_group = GROUP_COMBO;
            copy.option_code = "combo";
            copy.name_zh = "套餐";
            copy.name_en = "Combo";
            copy.price_delta = policy.combo_delta;
            return copy;
        }
        return option;
    }

    private MenuItem loadItem(Long itemId) {
        if (itemId == null) {
            throw new BusinessException("Menu item id is required");
        }
        return menuItemRepository.findById(itemId)
            .orElseThrow(() -> new BusinessException("Menu item not found: " + itemId));
    }

    private void requireStore(Long storeId) {
        if (storeId == null) {
            throw new BusinessException("Store id is required");
        }
        if (!storeRepository.existsById(storeId)) {
            throw new BusinessException("Store not found: " + storeId);
        }
    }

    private StorePricingPolicy defaultPolicy(Long storeId) {
        StorePricingPolicy policy = new StorePricingPolicy();
        policy.store_id = storeId;
        policy.size_small_delta = StandardSize.SMALL.defaultDelta;
        policy.size_regular_delta = StandardSize.REGULAR.defaultDelta;
        policy.size_large_delta = StandardSize.LARGE.defaultDelta;
        policy.combo_delta = DEFAULT_COMBO_DELTA;
        policy.policy_revision = 1L;
        policy.created_at = now();
        policy.updated_at = policy.created_at;
        return policy;
    }

    private StorePricingPolicy merge(StorePricingPolicy current, StorePricingPolicyUpdateRequest request, boolean requireComplete) {
        if (request == null) {
            if (requireComplete) {
                throw new BusinessException("Pricing policy payload is required");
            }
            return current;
        }
        StorePricingPolicy next = new StorePricingPolicy();
        next.id = current.id;
        next.store_id = current.store_id;
        next.policy_revision = current.policy_revision;
        next.created_at = current.created_at;
        next.updated_at = current.updated_at;
        next.size_small_delta = money(request.size_small_delta == null ? current.size_small_delta : request.size_small_delta);
        next.size_regular_delta = money(request.size_regular_delta == null ? current.size_regular_delta : request.size_regular_delta);
        next.size_large_delta = money(request.size_large_delta == null ? current.size_large_delta : request.size_large_delta);
        next.combo_delta = money(request.combo_delta == null ? current.combo_delta : request.combo_delta);
        return next;
    }

    private Set<StandardSize> parseEnabledSizes(MenuItemSizeConfigurationRequest request) {
        if (request == null || request.enabled_size_codes == null || request.enabled_size_codes.isEmpty()) {
            throw new BusinessException("At least one enabled Size is required");
        }
        Set<StandardSize> enabled = new HashSet<>();
        for (String code : request.enabled_size_codes) {
            StandardSize size = StandardSize.fromCode(code)
                .orElseThrow(() -> new BusinessException("Unsupported Size code: " + code));
            enabled.add(size);
        }
        if (enabled.isEmpty()) {
            throw new BusinessException("At least one enabled Size is required");
        }
        return enabled;
    }

    private void validateDefault(Set<StandardSize> enabled, String defaultCode) {
        if (enabled.contains(StandardSize.REGULAR)) {
            return;
        }
        if (enabled.size() == 1) {
            return;
        }
        StandardSize explicitDefault = StandardSize.fromCode(defaultCode)
            .orElseThrow(() -> new BusinessException("default_size_code is required when REGULAR is disabled and multiple Sizes are enabled"));
        if (!enabled.contains(explicitDefault)) {
            throw new BusinessException("default_size_code must be one of the enabled Sizes");
        }
    }

    private List<StandardSize> defaultOrderedSizes(Set<StandardSize> enabled, String defaultCode) {
        List<StandardSize> sizes = new ArrayList<>(List.of(StandardSize.SMALL, StandardSize.REGULAR, StandardSize.LARGE));
        if (enabled.contains(StandardSize.REGULAR)) {
            sizes.sort(Comparator.comparingInt(size -> size == StandardSize.REGULAR ? 0 : size == StandardSize.SMALL ? 1 : 2));
            return sizes;
        }
        if (enabled.size() == 1) {
            StandardSize only = enabled.iterator().next();
            sizes.sort(Comparator.comparingInt(size -> size == only ? 0 : size == StandardSize.SMALL ? 1 : size == StandardSize.REGULAR ? 2 : 3));
            return sizes;
        }
        StandardSize explicitDefault = StandardSize.fromCode(defaultCode).orElseThrow();
        sizes.sort(Comparator.comparingInt(size -> size == explicitDefault ? 0 : size == StandardSize.SMALL ? 1 : size == StandardSize.REGULAR ? 2 : 3));
        return sizes;
    }

    private Optional<StandardSize> resolveSize(MenuItemOption option) {
        if (!isSizeOption(option)) {
            return Optional.empty();
        }
        return StandardSize.fromOption(option.option_code, option.name_zh, option.name_en);
    }

    private boolean isSizeOption(MenuItemOption option) {
        return "SIZE".equalsIgnoreCase(blankToEmpty(option.option_group))
            || "size".equalsIgnoreCase(blankToEmpty(option.option_type));
    }

    private MenuItemOption preferCanonicalSizeOption(MenuItemOption current, MenuItemOption candidate) {
        boolean currentCanonical = isCanonicalSizeIdentity(current);
        boolean candidateCanonical = isCanonicalSizeIdentity(candidate);
        if (currentCanonical != candidateCanonical) {
            return candidateCanonical ? candidate : current;
        }
        return current;
    }

    private boolean isCanonicalSizeIdentity(MenuItemOption option) {
        return option != null
            && GROUP_SIZE.equalsIgnoreCase(blankToEmpty(option.option_group))
            && StandardSize.fromCode(option.option_code).isPresent();
    }

    private boolean isComboUpcharge(MenuItemOption option) {
        if (option == null) {
            return false;
        }
        if ("COMBO".equalsIgnoreCase(blankToEmpty(option.option_group))) {
            return true;
        }
        if ("combo".equalsIgnoreCase(blankToEmpty(option.option_code))) {
            return true;
        }
        return "addon".equalsIgnoreCase(blankToEmpty(option.option_type))
            && ("套餐".equals(option.name_zh) || "combo".equalsIgnoreCase(blankToEmpty(option.name_en)));
    }

    private Map<Long, List<MenuItemOption>> groupOptions(List<MenuItemOption> options) {
        Map<Long, List<MenuItemOption>> grouped = new LinkedHashMap<>();
        for (MenuItemOption option : options) {
            grouped.computeIfAbsent(option.menu_item_id, ignored -> new ArrayList<>()).add(option);
        }
        return grouped;
    }

    private BigDecimal deltaForSize(StorePricingPolicy policy, StandardSize size) {
        return switch (size) {
            case SMALL -> policy.size_small_delta;
            case REGULAR -> policy.size_regular_delta;
            case LARGE -> policy.size_large_delta;
        };
    }

    private StorePricingPolicyPreviewResponse.ImpactGroup impactGroup(String key, BigDecimal oldDelta, BigDecimal newDelta) {
        StorePricingPolicyPreviewResponse.ImpactGroup group = new StorePricingPolicyPreviewResponse.ImpactGroup();
        group.policy_key = key;
        group.old_delta = oldDelta;
        group.new_delta = newDelta;
        return group;
    }

    private StorePricingPolicyPreviewResponse.ImpactItem impactItem(MenuItem item, BigDecimal oldDelta, BigDecimal newDelta) {
        StorePricingPolicyPreviewResponse.ImpactItem response = new StorePricingPolicyPreviewResponse.ImpactItem();
        response.item_id = item.id;
        response.sku = item.sku;
        response.name_zh = item.name_zh;
        response.name_en = item.name_en;
        response.old_price = money(defaultMoney(item.base_price).add(defaultMoney(oldDelta)));
        response.new_price = money(defaultMoney(item.base_price).add(defaultMoney(newDelta)));
        return response;
    }

    private MenuItemOption copy(MenuItemOption source) {
        MenuItemOption copy = new MenuItemOption();
        copy.id = source.id;
        copy.menu_item_id = source.menu_item_id;
        copy.option_type = source.option_type;
        copy.option_code = source.option_code;
        copy.option_group = source.option_group;
        copy.parent_option_id = source.parent_option_id;
        copy.sort_order = source.sort_order;
        copy.name_zh = source.name_zh;
        copy.name_en = source.name_en;
        copy.price_delta = source.price_delta;
        copy.is_active = source.is_active;
        copy.created_at = source.created_at;
        copy.updated_at = source.updated_at;
        return copy;
    }

    private int nextSortOrder(List<MenuItemOption> options, String group) {
        return options.stream()
            .filter(option -> group.equalsIgnoreCase(blankToEmpty(option.option_group)))
            .map(option -> option.sort_order == null ? 0 : option.sort_order)
            .max(Integer::compareTo)
            .orElse(0) + 10;
    }

    private MenuItemOptionAdminResponse toResponse(MenuItemOption option) {
        MenuItemOptionAdminResponse response = new MenuItemOptionAdminResponse();
        response.id = option.id;
        response.menu_item_id = option.menu_item_id;
        response.option_type = option.option_type;
        response.option_code = option.option_code;
        response.option_group = option.option_group;
        response.parent_option_id = option.parent_option_id;
        response.sort_order = option.sort_order;
        response.name_zh = option.name_zh;
        response.name_en = option.name_en;
        response.price_delta = option.price_delta;
        response.is_active = option.is_active;
        response.created_at = option.created_at;
        response.updated_at = option.updated_at;
        return response;
    }

    private StorePricingPolicyResponse toResponse(StorePricingPolicy policy) {
        StorePricingPolicyResponse response = new StorePricingPolicyResponse();
        response.store_id = policy.store_id;
        response.policy_revision = policy.policy_revision;
        response.size_small_delta = policy.size_small_delta;
        response.size_regular_delta = policy.size_regular_delta;
        response.size_large_delta = policy.size_large_delta;
        response.combo_delta = policy.combo_delta;
        return response;
    }

    private BigDecimal money(BigDecimal value) {
        return defaultMoney(value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value;
    }

    private boolean moneyEquals(BigDecimal left, BigDecimal right) {
        return defaultMoney(left).compareTo(defaultMoney(right)) == 0;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }
}
