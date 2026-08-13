package com.restaurant.system.menu.service.impl;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.combo.StoreComboComponent;
import com.restaurant.system.menu.combo.StoreComboComponentDefinition;
import com.restaurant.system.menu.combo.StoreComboComponentRepository;
import com.restaurant.system.menu.dto.MenuRevisionResponse;
import com.restaurant.system.menu.dto.StoreComboConfigurationResponse;
import com.restaurant.system.menu.dto.StoreComboConfigurationUpdateRequest;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.menu.service.StoreComboConfigurationService;
import com.restaurant.system.user.repository.StoreRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreComboConfigurationServiceImpl implements StoreComboConfigurationService {

    private static final String GROUP_COMBO = "COMBO";
    private static final String GROUP_COMBO_EGG = "COMBO_EGG";
    private static final String GROUP_COMBO_SIDE = "COMBO_SIDE";

    private final StoreComboComponentRepository storeComboComponentRepository;
    private final StoreRepository storeRepository;
    private final MenuItemOptionRepository menuItemOptionRepository;
    private final MenuRevisionService menuRevisionService;

    public StoreComboConfigurationServiceImpl(
        StoreComboComponentRepository storeComboComponentRepository,
        StoreRepository storeRepository,
        MenuItemOptionRepository menuItemOptionRepository,
        MenuRevisionService menuRevisionService
    ) {
        this.storeComboComponentRepository = storeComboComponentRepository;
        this.storeRepository = storeRepository;
        this.menuItemOptionRepository = menuItemOptionRepository;
        this.menuRevisionService = menuRevisionService;
    }

    @Override
    @Transactional(readOnly = true)
    public StoreComboConfigurationResponse getConfiguration(Long storeId) {
        requireStore(storeId);
        return toResponse(storeId, currentState(storeId), menuRevisionService.getRevision(storeId));
    }

    @Override
    @Transactional
    public StoreComboConfigurationResponse updateConfiguration(Long storeId, StoreComboConfigurationUpdateRequest request) {
        requireStore(storeId);
        if (request == null) {
            throw new BusinessException("Combo configuration payload is required");
        }
        if (request.store_id != null && !request.store_id.equals(storeId)) {
            throw new BusinessException("Combo configuration store_id does not match request store");
        }
        if (request.components == null || request.components.isEmpty()) {
            throw new BusinessException("At least one combo component update is required");
        }

        menuRevisionService.lockStoresInOrder(List.of(storeId));
        Map<StoreComboComponentDefinition, StoreComboComponent> nextState = currentState(storeId);
        Set<String> seen = new HashSet<>();
        LocalDateTime now = now();
        for (StoreComboConfigurationUpdateRequest.ComponentUpdate update : request.components) {
            if (update == null || update.enabled == null) {
                throw new BusinessException("Combo component enabled value is required");
            }
            StoreComboComponentDefinition definition = StoreComboComponentDefinition
                .fromGroupAndCode(update.component_group, update.component_code)
                .orElseThrow(() -> new BusinessException("Unsupported combo component: " + update.component_group + "/" + update.component_code));
            String key = definition.componentGroup + ":" + definition.componentCode;
            if (!seen.add(key)) {
                throw new BusinessException("Duplicate combo component update: " + key);
            }
            StoreComboComponent component = nextState.computeIfAbsent(definition, ignored -> newComponent(storeId, definition, now));
            component.enabled = update.enabled;
            component.updated_at = now;
        }

        validateRequiredComponents(storeId, menuItemOptionRepository.findActiveByStoreIdOrdered(storeId), nextState);
        storeComboComponentRepository.saveAll(nextState.values());
        menuRevisionService.incrementRevision(storeId);
        return toResponse(storeId, nextState, menuRevisionService.getRevision(storeId));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCatalogOptionEnabled(Long storeId, MenuItemOption option) {
        if (StoreComboComponentDefinition.isStoreConfiguredGroup(option == null ? null : option.option_group)) {
            return false;
        }
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public void requireOptionEnabledForNewSelection(Long storeId, MenuItemOption option) {
        var definition = StoreComboComponentDefinition.fromOption(option);
        if (definition.isEmpty()) {
            if (StoreComboComponentDefinition.isStoreConfiguredGroup(option == null ? null : option.option_group)) {
                throw new BusinessException("COMBO_COMPONENT_UNSUPPORTED");
            }
            return;
        }
        if (!isKnownEnabledComboComponent(storeId, option)) {
            throw new BusinessException("COMBO_COMPONENT_DISABLED: " + definition.get().componentCode);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void requireSnapshotEnabledForNewSelection(Long storeId, String optionGroup, String optionCode) {
        if (!StoreComboComponentDefinition.isStoreConfiguredGroup(optionGroup)) {
            return;
        }
        var definition = StoreComboComponentDefinition.fromGroupAndCode(optionGroup, optionCode);
        if (definition.isEmpty()) {
            throw new BusinessException("COMBO_COMPONENT_UNSUPPORTED");
        }
        if (!isKnownEnabledComboComponent(storeId, definition.get())) {
            throw new BusinessException("COMBO_COMPONENT_DISABLED: " + definition.get().componentCode);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void validateRequiredComponentsForCatalog(Long storeId, List<MenuItemOption> activeOptions) {
        validateRequiredComponents(storeId, activeOptions, currentState(storeId));
    }

    private void validateRequiredComponents(
        Long storeId,
        List<MenuItemOption> activeOptions,
        Map<StoreComboComponentDefinition, StoreComboComponent> componentState
    ) {
        boolean anyComboAllowedItem = (activeOptions == null ? List.<MenuItemOption>of() : activeOptions)
            .stream()
            .anyMatch(this::isComboAllowedOption);
        if (!anyComboAllowedItem) {
            return;
        }
        if (!hasEnabledComponent(GROUP_COMBO_EGG, componentState)) {
            throw new BusinessException("COMBO_EGG_CONFIGURATION_MISSING");
        }
        if (!hasEnabledComponent(GROUP_COMBO_SIDE, componentState)) {
            throw new BusinessException("COMBO_SIDE_CONFIGURATION_MISSING");
        }
    }

    private boolean hasEnabledComponent(String requiredGroup, Map<StoreComboComponentDefinition, StoreComboComponent> componentState) {
        return StoreComboComponentDefinition.valuesForGroup(requiredGroup).stream()
            .filter(definition -> requiredGroup.equals(definition.componentGroup))
            .anyMatch(definition -> enabled(componentState.get(definition)));
    }

    private boolean isKnownEnabledComboComponent(Long storeId, MenuItemOption option) {
        var definition = StoreComboComponentDefinition.fromOption(option);
        if (definition.isEmpty()) {
            if (StoreComboComponentDefinition.isStoreConfiguredGroup(option == null ? null : option.option_group)) {
                return false;
            }
            return true;
        }
        return storeComboComponentRepository
            .findByStoreIdAndGroupAndCode(storeId, definition.get().componentGroup, definition.get().componentCode)
            .map(this::enabled)
            .orElse(false);
    }

    private boolean isKnownEnabledComboComponent(Long storeId, StoreComboComponentDefinition definition) {
        return storeComboComponentRepository
            .findByStoreIdAndGroupAndCode(storeId, definition.componentGroup, definition.componentCode)
            .map(this::enabled)
            .orElse(false);
    }

    private Map<StoreComboComponentDefinition, StoreComboComponent> currentState(Long storeId) {
        Map<StoreComboComponentDefinition, StoreComboComponent> components = new LinkedHashMap<>();
        for (StoreComboComponent component : storeComboComponentRepository.findAllByStoreIdOrdered(storeId)) {
            StoreComboComponentDefinition.fromGroupAndCode(component.component_group, component.component_code)
                .ifPresent(definition -> components.put(definition, normalize(component, definition)));
        }
        LocalDateTime now = now();
        for (StoreComboComponentDefinition definition : StoreComboComponentDefinition.values()) {
            components.computeIfAbsent(definition, ignored -> newComponent(storeId, definition, now, false));
        }
        return components;
    }

    private StoreComboComponent normalize(StoreComboComponent component, StoreComboComponentDefinition definition) {
        component.component_group = definition.componentGroup;
        component.component_code = definition.componentCode;
        component.name_zh = definition.nameZh;
        component.name_en = definition.nameEn;
        component.display_order = definition.displayOrder;
        component.enabled = Boolean.TRUE.equals(component.enabled);
        return component;
    }

    private StoreComboComponent newComponent(Long storeId, StoreComboComponentDefinition definition, LocalDateTime now) {
        return newComponent(storeId, definition, now, true);
    }

    private StoreComboComponent newComponent(Long storeId, StoreComboComponentDefinition definition, LocalDateTime now, boolean enabled) {
        StoreComboComponent component = new StoreComboComponent();
        component.store_id = storeId;
        component.component_group = definition.componentGroup;
        component.component_code = definition.componentCode;
        component.name_zh = definition.nameZh;
        component.name_en = definition.nameEn;
        component.enabled = enabled;
        component.display_order = definition.displayOrder;
        component.created_at = now;
        component.updated_at = now;
        return component;
    }

    private StoreComboConfigurationResponse toResponse(
        Long storeId,
        Map<StoreComboComponentDefinition, StoreComboComponent> componentState,
        MenuRevisionResponse revision
    ) {
        StoreComboConfigurationResponse response = new StoreComboConfigurationResponse();
        response.store_id = storeId;
        response.menu_revision = revision == null ? null : revision.menu_revision;

        for (String group : List.of(GROUP_COMBO_EGG, GROUP_COMBO_SIDE)) {
            StoreComboConfigurationResponse.GroupResponse groupResponse = new StoreComboConfigurationResponse.GroupResponse();
            groupResponse.component_group = group;
            groupResponse.name_zh = GROUP_COMBO_EGG.equals(group) ? "蛋类" : "小菜";
            groupResponse.name_en = GROUP_COMBO_EGG.equals(group) ? "Egg" : "Side";
            List<StoreComboConfigurationResponse.ComponentResponse> components = StoreComboComponentDefinition.valuesForGroup(group)
                .stream()
                .sorted(Comparator.comparingInt(definition -> definition.displayOrder))
                .map(definition -> toComponentResponse(definition, componentState.get(definition)))
                .toList();
            components.stream()
                .filter(component -> Boolean.TRUE.equals(component.enabled))
                .findFirst()
                .ifPresent(component -> groupResponse.default_component_code = component.component_code);
            for (StoreComboConfigurationResponse.ComponentResponse component : components) {
                component.is_default = component.component_code.equals(groupResponse.default_component_code);
                groupResponse.components.add(component);
            }
            response.groups.add(groupResponse);
        }
        return response;
    }

    private StoreComboConfigurationResponse.ComponentResponse toComponentResponse(
        StoreComboComponentDefinition definition,
        StoreComboComponent component
    ) {
        StoreComboConfigurationResponse.ComponentResponse response = new StoreComboConfigurationResponse.ComponentResponse();
        response.component_group = definition.componentGroup;
        response.component_code = definition.componentCode;
        response.name_zh = definition.nameZh;
        response.name_en = definition.nameEn;
        response.enabled = enabled(component);
        response.display_order = definition.displayOrder;
        response.is_default = false;
        return response;
    }

    private boolean enabled(StoreComboComponent component) {
        return component != null && Boolean.TRUE.equals(component.enabled);
    }

    private boolean isComboAllowedOption(MenuItemOption option) {
        if (option == null) {
            return false;
        }
        if (GROUP_COMBO.equals(StoreComboComponentDefinition.normalizeGroup(option.option_group))) {
            return true;
        }
        if ("combo".equals(StoreComboComponentDefinition.normalizeCode(option.option_code))) {
            return true;
        }
        return "addon".equals(normalizeOptionType(option.option_type))
            && ("套餐".equals(option.name_zh) || "combo".equalsIgnoreCase(blankToEmpty(option.name_en)));
    }

    private void requireStore(Long storeId) {
        if (storeId == null) {
            throw new BusinessException("Store id is required");
        }
        if (!storeRepository.existsById(storeId)) {
            throw new BusinessException("Store not found: " + storeId);
        }
    }

    private String normalizeOptionType(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }
}
