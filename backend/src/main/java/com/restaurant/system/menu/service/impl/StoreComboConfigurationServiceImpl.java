package com.restaurant.system.menu.service.impl;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.combo.StoreComboComponent;
import com.restaurant.system.menu.combo.StoreComboComponentRepository;
import com.restaurant.system.menu.combo.StoreComboGroup;
import com.restaurant.system.menu.combo.StoreComboGroupRepository;
import com.restaurant.system.menu.dto.MenuRevisionResponse;
import com.restaurant.system.menu.dto.StoreComboConfigurationResponse;
import com.restaurant.system.menu.dto.StoreComboConfigurationUpdateRequest;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.menu.service.StoreComboConfigurationService;
import com.restaurant.system.user.repository.StoreRepository;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreComboConfigurationServiceImpl implements StoreComboConfigurationService {

    private static final String GROUP_COMBO = "COMBO";
    private static final String SELECTION_EXACTLY_ONE = "EXACTLY_ONE";
    private static final String SELECTION_OPTIONAL_ONE = "OPTIONAL_ONE";
    private static final String BEHAVIOR_NO_KITCHEN_TASK = "NO_KITCHEN_TASK";
    private static final String BEHAVIOR_LINKED_MENU_ITEM = "LINKED_MENU_ITEM";
    private static final String BEHAVIOR_LEGACY_COMBO_SIDE_TASK = "LEGACY_COMBO_SIDE_TASK";
    private static final Set<String> SELECTION_RULES = Set.of(SELECTION_EXACTLY_ONE, SELECTION_OPTIONAL_ONE);
    private static final Set<String> BUSINESS_BEHAVIORS = Set.of(
        BEHAVIOR_NO_KITCHEN_TASK,
        BEHAVIOR_LINKED_MENU_ITEM,
        BEHAVIOR_LEGACY_COMBO_SIDE_TASK
    );

    private final StoreComboGroupRepository storeComboGroupRepository;
    private final StoreComboComponentRepository storeComboComponentRepository;
    private final StoreRepository storeRepository;
    private final MenuItemOptionRepository menuItemOptionRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuRevisionService menuRevisionService;

    public StoreComboConfigurationServiceImpl(
        StoreComboGroupRepository storeComboGroupRepository,
        StoreComboComponentRepository storeComboComponentRepository,
        StoreRepository storeRepository,
        MenuItemOptionRepository menuItemOptionRepository,
        MenuItemRepository menuItemRepository,
        MenuRevisionService menuRevisionService
    ) {
        this.storeComboGroupRepository = storeComboGroupRepository;
        this.storeComboComponentRepository = storeComboComponentRepository;
        this.storeRepository = storeRepository;
        this.menuItemOptionRepository = menuItemOptionRepository;
        this.menuItemRepository = menuItemRepository;
        this.menuRevisionService = menuRevisionService;
    }

    @Override
    @Transactional(readOnly = true)
    public StoreComboConfigurationResponse getConfiguration(Long storeId) {
        requireStore(storeId);
        return toResponse(storeId, loadState(storeId), menuRevisionService.getRevision(storeId));
    }

    @Override
    @Transactional
    public StoreComboConfigurationResponse updateConfiguration(Long storeId, StoreComboConfigurationUpdateRequest request) {
        requireStore(storeId);
        if (request == null) {
            throw new BusinessException("COMBO_CONFIGURATION_PAYLOAD_REQUIRED");
        }
        if (request.store_id != null && !request.store_id.equals(storeId)) {
            throw new BusinessException("COMBO_CONFIGURATION_STORE_MISMATCH");
        }

        menuRevisionService.lockStoresInOrder(List.of(storeId));
        ComboState nextState = loadStateForUpdate(storeId);
        LocalDateTime now = now();
        if (request.groups != null && !request.groups.isEmpty()) {
            updateGroups(storeId, request.groups, nextState, now);
        } else if (request.components != null && !request.components.isEmpty()) {
            updateLegacyComponentToggles(storeId, request.components, nextState, now);
        } else {
            throw new BusinessException("COMBO_CONFIGURATION_EMPTY");
        }

        validateConfiguration(storeId, menuItemOptionRepository.findActiveByStoreIdOrdered(storeId), nextState);
        storeComboGroupRepository.saveAll(nextState.groups());
        storeComboComponentRepository.saveAll(nextState.components());
        menuRevisionService.incrementRevision(storeId);
        return toResponse(storeId, loadState(storeId), menuRevisionService.getRevision(storeId));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCatalogOptionEnabled(Long storeId, MenuItemOption option) {
        if (option == null || option.option_group == null) {
            return true;
        }
        String groupCode = normalizeGroup(option.option_group);
        if (GROUP_COMBO.equals(groupCode)) {
            return true;
        }
        return storeComboGroupRepository.findByStoreIdAndGroupCode(storeId, groupCode).isEmpty();
    }

    @Override
    @Transactional(readOnly = true)
    public void requireOptionEnabledForNewSelection(Long storeId, MenuItemOption option) {
        if (option == null || option.option_group == null) {
            return;
        }
        requireSnapshotEnabledForNewSelection(storeId, option.option_group, option.option_code);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireSnapshotEnabledForNewSelection(Long storeId, String optionGroup, String optionCode) {
        String groupCode = normalizeGroup(optionGroup);
        if (groupCode.isBlank() || GROUP_COMBO.equals(groupCode)) {
            return;
        }
        StoreComboGroup group = storeComboGroupRepository.findByStoreIdAndGroupCode(storeId, groupCode).orElse(null);
        if (group == null) {
            return;
        }
        if (!Boolean.TRUE.equals(group.enabled)) {
            throw new BusinessException("COMBO_GROUP_DISABLED: " + groupCode);
        }
        String componentCode = normalizeCode(optionCode);
        StoreComboComponent component = storeComboComponentRepository
            .findByStoreIdAndGroupAndCode(storeId, groupCode, componentCode)
            .orElseThrow(() -> new BusinessException("COMBO_COMPONENT_UNSUPPORTED"));
        if (!Boolean.TRUE.equals(component.enabled)) {
            throw new BusinessException("COMBO_COMPONENT_DISABLED: " + componentCode);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void validateRequiredComponentsForCatalog(Long storeId, List<MenuItemOption> activeOptions) {
        validateConfiguration(storeId, activeOptions, loadState(storeId));
    }

    private void updateGroups(
        Long storeId,
        List<StoreComboConfigurationUpdateRequest.GroupUpdate> updates,
        ComboState state,
        LocalDateTime now
    ) {
        Map<Long, StoreComboGroup> groupsById = state.groups().stream()
            .filter(group -> group.id != null)
            .collect(Collectors.toMap(group -> group.id, Function.identity()));
        Map<String, StoreComboGroup> groupsByCode = state.groups().stream()
            .collect(Collectors.toMap(group -> normalizeGroup(group.group_code), Function.identity(), (left, right) -> left));
        Set<Long> retainedGroupIds = new HashSet<>();
        Set<String> seenGroupCodes = new HashSet<>();
        int fallbackGroupOrder = 10;

        for (StoreComboConfigurationUpdateRequest.GroupUpdate update : updates) {
            if (update == null) {
                throw new BusinessException("COMBO_GROUP_REQUIRED");
            }
            StoreComboGroup group = resolveGroup(storeId, update, groupsById, groupsByCode, now);
            String groupCode = normalizeGroup(group.group_code);
            if (!seenGroupCodes.add(groupCode)) {
                throw new BusinessException("COMBO_GROUP_DUPLICATE_CODE: " + groupCode);
            }

            String selectionRule = normalizeSelectionRule(update.selection_rule);
            group.name_zh = cleanRequired(update.name_zh, "COMBO_GROUP_NAME_ZH_REQUIRED");
            group.name_en = cleanRequired(update.name_en, "COMBO_GROUP_NAME_EN_REQUIRED");
            group.selection_rule = selectionRule;
            group.required = SELECTION_EXACTLY_ONE.equals(selectionRule)
                ? true
                : bool(update.required, false);
            group.enabled = bool(update.enabled, true);
            group.display_order = update.display_order == null ? fallbackGroupOrder : update.display_order;
            group.archived_at = null;
            group.updated_at = now;
            fallbackGroupOrder += 10;
            if (group.id == null) {
                group = storeComboGroupRepository.save(group);
                state.groups().add(group);
                groupsById.put(group.id, group);
                groupsByCode.put(normalizeGroup(group.group_code), group);
            }
            retainedGroupIds.add(group.id);

            updateComponentsForGroup(storeId, group, update, state, now);
        }

        for (StoreComboGroup group : state.groups()) {
            if (group.id != null && !retainedGroupIds.contains(group.id) && group.archived_at == null) {
                group.enabled = false;
                group.archived_at = now;
                group.updated_at = now;
                for (StoreComboComponent component : state.components()) {
                    if (group.id.equals(component.group_id) && component.archived_at == null) {
                        component.enabled = false;
                        component.archived_at = now;
                        component.updated_at = now;
                    }
                }
            }
        }
    }

    private StoreComboGroup resolveGroup(
        Long storeId,
        StoreComboConfigurationUpdateRequest.GroupUpdate update,
        Map<Long, StoreComboGroup> groupsById,
        Map<String, StoreComboGroup> groupsByCode,
        LocalDateTime now
    ) {
        StoreComboGroup group = update.group_id == null ? null : groupsById.get(update.group_id);
        if (group == null && update.group_code != null && !update.group_code.isBlank()) {
            group = groupsByCode.get(normalizeGroup(update.group_code));
        }
        if (group != null) {
            if (!storeId.equals(group.store_id)) {
                throw new BusinessException("COMBO_GROUP_STORE_MISMATCH");
            }
            return group;
        }

        StoreComboGroup created = new StoreComboGroup();
        created.store_id = storeId;
        created.group_code = nextGroupCode(storeId, firstNonBlank(update.name_en, update.name_zh));
        created.created_at = now;
        created.updated_at = now;
        return created;
    }

    private void updateComponentsForGroup(
        Long storeId,
        StoreComboGroup group,
        StoreComboConfigurationUpdateRequest.GroupUpdate groupUpdate,
        ComboState state,
        LocalDateTime now
    ) {
        List<StoreComboComponent> existing = state.components().stream()
            .filter(component -> group.id.equals(component.group_id))
            .toList();
        Map<Long, StoreComboComponent> componentsById = existing.stream()
            .filter(component -> component.id != null)
            .collect(Collectors.toMap(component -> component.id, Function.identity()));
        Map<String, StoreComboComponent> componentsByCode = existing.stream()
            .collect(Collectors.toMap(component -> normalizeCode(component.component_code), Function.identity(), (left, right) -> left));
        Set<Long> retainedComponentIds = new HashSet<>();
        Set<String> seenComponentCodes = new HashSet<>();
        int fallbackOrder = 10;

        List<StoreComboConfigurationUpdateRequest.ComponentUpdate> componentUpdates =
            groupUpdate.components == null ? List.of() : groupUpdate.components;
        for (StoreComboConfigurationUpdateRequest.ComponentUpdate update : componentUpdates) {
            if (update == null) {
                throw new BusinessException("COMBO_COMPONENT_REQUIRED");
            }
            StoreComboComponent component = resolveComponent(storeId, group, update, componentsById, componentsByCode, state, now);
            String componentCode = normalizeCode(component.component_code);
            if (!seenComponentCodes.add(componentCode)) {
                throw new BusinessException("COMBO_COMPONENT_DUPLICATE_CODE: " + group.group_code + "/" + componentCode);
            }

            component.store_id = storeId;
            component.group_id = group.id;
            component.component_group = normalizeGroup(group.group_code);
            component.name_zh = cleanRequired(update.name_zh, "COMBO_COMPONENT_NAME_ZH_REQUIRED");
            component.name_en = cleanRequired(update.name_en, "COMBO_COMPONENT_NAME_EN_REQUIRED");
            component.enabled = bool(update.enabled, true);
            component.display_order = update.display_order == null ? fallbackOrder : update.display_order;
            String behavior = normalizeBusinessBehavior(
                firstNonBlank(update.business_behavior, component.business_behavior)
            );
            component.business_behavior = behavior;
            component.linked_menu_item_id = behaviorMayUseLinkedItem(behavior)
                ? update.linked_menu_item_id == null ? component.linked_menu_item_id : update.linked_menu_item_id
                : null;
            component.archived_at = null;
            component.updated_at = now;
            validateComponentMapping(storeId, group, component);
            if (component.id == null) {
                component = storeComboComponentRepository.save(component);
                componentsById.put(component.id, component);
                componentsByCode.put(normalizeCode(component.component_code), component);
                state.components().add(component);
            }
            retainedComponentIds.add(component.id);
            fallbackOrder += 10;
        }

        for (StoreComboComponent component : existing) {
            if (component.id != null && !retainedComponentIds.contains(component.id) && component.archived_at == null) {
                component.enabled = false;
                component.archived_at = now;
                component.updated_at = now;
            }
        }

        String requestedDefault = normalizeCode(groupUpdate.default_component_code);
        if (requestedDefault.isBlank()) {
            requestedDefault = componentUpdates.stream()
                .filter(update -> Boolean.TRUE.equals(update.is_default))
                .map(update -> normalizeCode(update.component_code))
                .filter(code -> !code.isBlank())
                .findFirst()
                .orElse(normalizeCode(group.default_component_code));
        }
        group.default_component_code = validEnabledDefault(group.id, requestedDefault, state.components())
            ? requestedDefault
            : firstEnabledComponentCode(group.id, state.components());
    }

    private StoreComboComponent resolveComponent(
        Long storeId,
        StoreComboGroup group,
        StoreComboConfigurationUpdateRequest.ComponentUpdate update,
        Map<Long, StoreComboComponent> componentsById,
        Map<String, StoreComboComponent> componentsByCode,
        ComboState state,
        LocalDateTime now
    ) {
        StoreComboComponent component = update.id == null ? null : componentsById.get(update.id);
        if (component == null && update.component_code != null && !update.component_code.isBlank()) {
            component = componentsByCode.get(normalizeCode(update.component_code));
        }
        if (component != null) {
            if (!storeId.equals(component.store_id) || !group.id.equals(component.group_id)) {
                throw new BusinessException("COMBO_COMPONENT_STORE_OR_GROUP_MISMATCH");
            }
            return component;
        }

        StoreComboComponent created = new StoreComboComponent();
        created.store_id = storeId;
        created.group_id = group.id;
        created.component_group = normalizeGroup(group.group_code);
        created.component_code = nextComponentCode(storeId, group, firstNonBlank(update.name_en, update.name_zh), state.components());
        created.business_behavior = BEHAVIOR_NO_KITCHEN_TASK;
        created.created_at = now;
        created.updated_at = now;
        return created;
    }

    private void updateLegacyComponentToggles(
        Long storeId,
        List<StoreComboConfigurationUpdateRequest.ComponentUpdate> updates,
        ComboState state,
        LocalDateTime now
    ) {
        Map<String, StoreComboComponent> componentsByKey = state.components().stream()
            .filter(component -> component.archived_at == null)
            .collect(Collectors.toMap(
                component -> normalizeGroup(component.component_group) + ":" + normalizeCode(component.component_code),
                Function.identity(),
                (left, right) -> left
            ));
        Set<String> seen = new HashSet<>();
        for (StoreComboConfigurationUpdateRequest.ComponentUpdate update : updates) {
            if (update == null || update.enabled == null) {
                throw new BusinessException("COMBO_COMPONENT_ENABLED_REQUIRED");
            }
            String key = normalizeGroup(update.component_group) + ":" + normalizeCode(update.component_code);
            if (!seen.add(key)) {
                throw new BusinessException("COMBO_COMPONENT_DUPLICATE_CODE: " + key);
            }
            StoreComboComponent component = componentsByKey.get(key);
            if (component == null) {
                throw new BusinessException("COMBO_COMPONENT_UNSUPPORTED");
            }
            component.enabled = update.enabled;
            component.updated_at = now;
        }

        for (StoreComboGroup group : state.groups()) {
            String defaultCode = normalizeCode(group.default_component_code);
            group.default_component_code = validEnabledDefault(group.id, defaultCode, state.components())
                ? defaultCode
                : firstEnabledComponentCode(group.id, state.components());
            group.updated_at = now;
        }
    }

    private void validateConfiguration(Long storeId, List<MenuItemOption> activeOptions, ComboState state) {
        boolean anyComboAllowedItem = (activeOptions == null ? List.<MenuItemOption>of() : activeOptions)
            .stream()
            .anyMatch(this::isComboAllowedOption);
        Set<String> seenGroupCodes = new HashSet<>();
        for (StoreComboGroup group : state.groups()) {
            if (group.archived_at != null) {
                continue;
            }
            String groupCode = normalizeGroup(group.group_code);
            if (!seenGroupCodes.add(groupCode)) {
                throw new BusinessException("COMBO_GROUP_DUPLICATE_CODE: " + groupCode);
            }
            if (!Boolean.TRUE.equals(group.enabled)) {
                continue;
            }
            List<StoreComboComponent> enabledComponents = enabledComponentsForGroup(group.id, state.components());
            if (Boolean.TRUE.equals(group.required) && enabledComponents.isEmpty()) {
                throw new BusinessException(groupCode + "_CONFIGURATION_MISSING");
            }
            if (Boolean.TRUE.equals(group.required) && !validEnabledDefault(group.id, group.default_component_code, state.components())) {
                throw new BusinessException("COMBO_DEFAULT_COMPONENT_INVALID: " + groupCode);
            }
            if (!anyComboAllowedItem) {
                continue;
            }
            if (Boolean.TRUE.equals(group.required) && enabledComponents.isEmpty()) {
                throw new BusinessException(groupCode + "_CONFIGURATION_MISSING");
            }
        }
    }

    private List<StoreComboComponent> enabledComponentsForGroup(Long groupId, List<StoreComboComponent> components) {
        return components.stream()
            .filter(component -> groupId.equals(component.group_id))
            .filter(component -> component.archived_at == null)
            .filter(component -> Boolean.TRUE.equals(component.enabled))
            .sorted(Comparator
                .comparing((StoreComboComponent component) -> component.display_order == null ? Integer.MAX_VALUE : component.display_order)
                .thenComparing(component -> component.id == null ? Long.MAX_VALUE : component.id))
            .toList();
    }

    private String firstEnabledComponentCode(Long groupId, List<StoreComboComponent> components) {
        return enabledComponentsForGroup(groupId, components).stream()
            .map(component -> normalizeCode(component.component_code))
            .findFirst()
            .orElse(null);
    }

    private boolean validEnabledDefault(Long groupId, String componentCode, List<StoreComboComponent> components) {
        String normalizedCode = normalizeCode(componentCode);
        if (normalizedCode.isBlank()) {
            return false;
        }
        return enabledComponentsForGroup(groupId, components).stream()
            .anyMatch(component -> normalizedCode.equals(normalizeCode(component.component_code)));
    }

    private void validateComponentMapping(Long storeId, StoreComboGroup group, StoreComboComponent component) {
        String behavior = normalizeBusinessBehavior(component.business_behavior);
        MenuItem linkedItem = null;
        if (component.linked_menu_item_id != null) {
            linkedItem = menuItemRepository.findById(component.linked_menu_item_id)
                .orElseThrow(() -> new BusinessException("COMBO_COMPONENT_MAPPING_INVALID"));
            if (!storeId.equals(linkedItem.store_id) || !Boolean.TRUE.equals(linkedItem.is_active)) {
                throw new BusinessException("COMBO_COMPONENT_MAPPING_INVALID");
            }
        }
        if (BEHAVIOR_LINKED_MENU_ITEM.equals(behavior)) {
            if (linkedItem == null) {
                throw new BusinessException("COMBO_COMPONENT_MAPPING_INVALID");
            }
            return;
        }
        if (BEHAVIOR_LEGACY_COMBO_SIDE_TASK.equals(behavior)) {
            if (!"COMBO_SIDE".equalsIgnoreCase(group.group_code) || !isLegacyComboSideCode(component.component_code)) {
                throw new BusinessException("COMBO_COMPONENT_MAPPING_INVALID");
            }
        }
    }

    private boolean behaviorMayUseLinkedItem(String behavior) {
        return BEHAVIOR_LINKED_MENU_ITEM.equals(behavior) || BEHAVIOR_LEGACY_COMBO_SIDE_TASK.equals(behavior);
    }

    private boolean isLegacyComboSideCode(String componentCode) {
        String code = normalizeCode(componentCode);
        return "combo_edamame".equals(code)
            || "combo_shredded_potato".equals(code)
            || "combo_cucumber_salad".equals(code);
    }

    private ComboState loadState(Long storeId) {
        return new ComboState(
            new ArrayList<>(storeComboGroupRepository.findAllByStoreIdOrdered(storeId)),
            new ArrayList<>(storeComboComponentRepository.findActiveByStoreIdOrdered(storeId))
        );
    }

    private ComboState loadStateForUpdate(Long storeId) {
        return new ComboState(
            new ArrayList<>(storeComboGroupRepository.findAllByStoreIdForUpdateOrdered(storeId)),
            new ArrayList<>(storeComboComponentRepository.findAllByStoreIdOrdered(storeId))
        );
    }

    private StoreComboConfigurationResponse toResponse(Long storeId, ComboState state, MenuRevisionResponse revision) {
        StoreComboConfigurationResponse response = new StoreComboConfigurationResponse();
        response.store_id = storeId;
        response.menu_revision = revision == null ? null : revision.menu_revision;
        Map<Long, List<StoreComboComponent>> componentsByGroupId = state.components().stream()
            .filter(component -> component.archived_at == null)
            .collect(Collectors.groupingBy(component -> component.group_id, LinkedHashMap::new, Collectors.toList()));
        Map<Long, MenuItem> linkedItems = loadLinkedItems(state.components());

        for (StoreComboGroup group : state.groups().stream()
            .filter(group -> group.archived_at == null)
            .sorted(Comparator
                .comparing((StoreComboGroup group) -> group.display_order == null ? Integer.MAX_VALUE : group.display_order)
                .thenComparing(group -> group.id == null ? Long.MAX_VALUE : group.id))
            .toList()) {
            StoreComboConfigurationResponse.GroupResponse groupResponse = new StoreComboConfigurationResponse.GroupResponse();
            groupResponse.group_id = group.id;
            groupResponse.group_code = normalizeGroup(group.group_code);
            groupResponse.component_group = groupResponse.group_code;
            groupResponse.name_zh = group.name_zh;
            groupResponse.name_en = group.name_en;
            groupResponse.selection_rule = normalizeSelectionRule(group.selection_rule);
            groupResponse.required = Boolean.TRUE.equals(group.required);
            groupResponse.enabled = Boolean.TRUE.equals(group.enabled);
            groupResponse.display_order = group.display_order;
            groupResponse.default_component_code = validEnabledDefault(group.id, group.default_component_code, state.components())
                ? normalizeCode(group.default_component_code)
                : firstEnabledComponentCode(group.id, state.components());
            List<StoreComboComponent> components = componentsByGroupId.getOrDefault(group.id, List.of()).stream()
                .sorted(Comparator
                    .comparing((StoreComboComponent component) -> component.display_order == null ? Integer.MAX_VALUE : component.display_order)
                    .thenComparing(component -> component.id == null ? Long.MAX_VALUE : component.id))
                .toList();
            for (StoreComboComponent component : components) {
                MenuItem linkedItem = component.linked_menu_item_id == null ? null : linkedItems.get(component.linked_menu_item_id);
                groupResponse.components.add(toComponentResponse(group, component, groupResponse.default_component_code, linkedItem));
            }
            response.groups.add(groupResponse);
        }
        return response;
    }

    private StoreComboConfigurationResponse.ComponentResponse toComponentResponse(
        StoreComboGroup group,
        StoreComboComponent component,
        String defaultComponentCode,
        MenuItem linkedItem
    ) {
        StoreComboConfigurationResponse.ComponentResponse response = new StoreComboConfigurationResponse.ComponentResponse();
        response.id = component.id;
        response.group_id = group.id;
        response.component_group = normalizeGroup(group.group_code);
        response.component_code = normalizeCode(component.component_code);
        response.name_zh = component.name_zh;
        response.name_en = component.name_en;
        response.enabled = Boolean.TRUE.equals(component.enabled);
        response.display_order = component.display_order;
        response.is_default = response.component_code.equals(defaultComponentCode);
        response.linked_menu_item_id = component.linked_menu_item_id;
        response.linked_menu_item_sku = linkedItem == null ? null : linkedItem.sku;
        response.linked_menu_item_name_zh = linkedItem == null ? null : linkedItem.name_zh;
        response.linked_menu_item_name_en = linkedItem == null ? null : linkedItem.name_en;
        response.business_behavior = normalizeBusinessBehavior(component.business_behavior);
        return response;
    }

    private Map<Long, MenuItem> loadLinkedItems(List<StoreComboComponent> components) {
        List<Long> itemIds = components.stream()
            .map(component -> component.linked_menu_item_id)
            .filter(id -> id != null)
            .distinct()
            .toList();
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        return menuItemRepository.findAllById(itemIds).stream()
            .collect(Collectors.toMap(item -> item.id, Function.identity(), (left, right) -> left));
    }

    private String nextGroupCode(Long storeId, String source) {
        List<String> existing = storeComboGroupRepository.findAllByStoreIdIncludingArchivedOrdered(storeId).stream()
            .map(group -> normalizeGroup(group.group_code))
            .toList();
        String base = "COMBO_" + slug(source, true);
        return nextAvailable(base, existing);
    }

    private String nextComponentCode(
        Long storeId,
        StoreComboGroup group,
        String source,
        List<StoreComboComponent> components
    ) {
        String groupCode = normalizeGroup(group.group_code);
        List<String> existing = components.stream()
            .filter(component -> storeId.equals(component.store_id))
            .filter(component -> groupCode.equals(normalizeGroup(component.component_group)))
            .map(component -> normalizeCode(component.component_code))
            .toList();
        String base = "combo_" + slug(source, false);
        return nextAvailable(base, existing);
    }

    private String nextAvailable(String base, List<String> existing) {
        Set<String> existingSet = new HashSet<>(existing);
        String candidate = base;
        int suffix = 2;
        while (existingSet.contains(candidate)) {
            candidate = base + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private String slug(String source, boolean uppercase) {
        String normalized = Normalizer.normalize(blankToEmpty(source), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replaceAll("[^A-Za-z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) {
            normalized = "custom_" + Integer.toUnsignedString(fnv1a32(source), 36);
        }
        return uppercase
            ? normalized.toUpperCase(Locale.ROOT)
            : normalized.toLowerCase(Locale.ROOT);
    }

    private int fnv1a32(String value) {
        int hash = 0x811c9dc5;
        for (byte current : blankToEmpty(value).getBytes(StandardCharsets.UTF_8)) {
            hash ^= current & 0xff;
            hash *= 0x01000193;
        }
        return hash;
    }

    private boolean isComboAllowedOption(MenuItemOption option) {
        if (option == null) {
            return false;
        }
        if (GROUP_COMBO.equals(normalizeGroup(option.option_group))) {
            return true;
        }
        if ("combo".equals(normalizeCode(option.option_code))) {
            return true;
        }
        return "addon".equals(normalizeOptionType(option.option_type))
            && ("套餐".equals(option.name_zh) || "combo".equalsIgnoreCase(blankToEmpty(option.name_en)));
    }

    private String normalizeSelectionRule(String value) {
        String normalized = blankToEmpty(value).isBlank()
            ? SELECTION_EXACTLY_ONE
            : value.trim().toUpperCase(Locale.ROOT);
        if (!SELECTION_RULES.contains(normalized)) {
            throw new BusinessException("COMBO_SELECTION_RULE_UNSUPPORTED");
        }
        return normalized;
    }

    private String normalizeBusinessBehavior(String value) {
        String normalized = blankToEmpty(value).isBlank()
            ? BEHAVIOR_NO_KITCHEN_TASK
            : value.trim().toUpperCase(Locale.ROOT);
        if (!BUSINESS_BEHAVIORS.contains(normalized)) {
            throw new BusinessException("COMBO_COMPONENT_BEHAVIOR_UNSUPPORTED");
        }
        return normalized;
    }

    private void requireStore(Long storeId) {
        if (storeId == null) {
            throw new BusinessException("STORE_ID_REQUIRED");
        }
        if (!storeRepository.existsById(storeId)) {
            throw new BusinessException("STORE_NOT_FOUND: " + storeId);
        }
    }

    private String cleanRequired(String value, String code) {
        String cleaned = blankToEmpty(value);
        if (cleaned.isBlank()) {
            throw new BusinessException(code);
        }
        return cleaned;
    }

    private Boolean bool(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    private String normalizeOptionType(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeGroup(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String first, String second) {
        String cleanedFirst = blankToEmpty(first);
        return cleanedFirst.isBlank() ? blankToEmpty(second) : cleanedFirst;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }

    private record ComboState(List<StoreComboGroup> groups, List<StoreComboComponent> components) {
    }
}
