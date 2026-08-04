package com.restaurant.system.owner.service.impl;

import com.restaurant.system.menu.entity.MenuCategory;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.owner.exception.OwnerStoreMenuCloneException;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.CategorySelection;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.CategorySourcePolicy;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.ItemRole;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.ItemSelection;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.SourcePolicy;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.StationSelection;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.StationSourcePolicy;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphResult;
import com.restaurant.system.owner.menu.StoreMenuCloneCompositionContext;
import com.restaurant.system.owner.menu.StoreMenuCloneGraphComposer;
import com.restaurant.system.owner.menu.StoreMenuClonePlannedOption;
import com.restaurant.system.owner.menu.StoreMenuCloneProfileDescriptor;
import com.restaurant.system.owner.menu.StoreMenuCloneProfileRegistry;
import com.restaurant.system.owner.menu.StoreMenuCloneSnapshot;
import com.restaurant.system.owner.menu.StoreMenuCloneSnapshot.SourceCategory;
import com.restaurant.system.owner.menu.StoreMenuCloneSnapshot.SourceItem;
import com.restaurant.system.owner.menu.StoreMenuCloneSnapshot.SourceOption;
import com.restaurant.system.owner.menu.StoreMenuCloneSnapshot.SourceStation;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneRequestCoordinator;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneSuccessEvidence;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneTransactionCommand;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneTransactionResult;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneValidationCommand;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneValidationResult;
import com.restaurant.system.owner.service.StoreMenuCloneTransactionService;
import com.restaurant.system.station.entity.Station;
import com.restaurant.system.station.repository.StationRepository;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreMenuCloneTransactionServiceImpl implements StoreMenuCloneTransactionService {

    private static final String STORE_STATUS_INACTIVE = "inactive";
    private static final String PRINTING_MODE_DISABLED = "DISABLED";
    private static final String RESULT_CODE = "MENU_CLONE_COMPLETED";

    private final StoreRepository storeRepository;
    private final MenuCategoryRepository categoryRepository;
    private final StationRepository stationRepository;
    private final MenuItemRepository itemRepository;
    private final MenuItemOptionRepository optionRepository;
    private final MenuRevisionService menuRevisionService;
    private final OwnerStoreMenuCloneRequestCoordinator requestCoordinator;
    private final StoreMenuCloneProfileRegistry profileRegistry;
    private final List<StoreMenuCloneGraphComposer> graphComposers;

    public StoreMenuCloneTransactionServiceImpl(
        StoreRepository storeRepository,
        MenuCategoryRepository categoryRepository,
        StationRepository stationRepository,
        MenuItemRepository itemRepository,
        MenuItemOptionRepository optionRepository,
        MenuRevisionService menuRevisionService,
        OwnerStoreMenuCloneRequestCoordinator requestCoordinator,
        StoreMenuCloneProfileRegistry profileRegistry,
        List<StoreMenuCloneGraphComposer> graphComposers
    ) {
        this.storeRepository = storeRepository;
        this.categoryRepository = categoryRepository;
        this.stationRepository = stationRepository;
        this.itemRepository = itemRepository;
        this.optionRepository = optionRepository;
        this.menuRevisionService = menuRevisionService;
        this.requestCoordinator = requestCoordinator;
        this.profileRegistry = profileRegistry;
        this.graphComposers = graphComposers == null ? List.of() : List.copyOf(graphComposers);
    }

    @Override
    @Transactional(readOnly = true)
    public OwnerStoreMenuCloneValidationResult validate(OwnerStoreMenuCloneValidationCommand command) {
        validateValidationCommand(command);
        StoreMenuCloneBaseGraphProfile profile = requireBaseGraphProfile(
            command.sourceStoreId(),
            command.profileCode()
        );
        validateProfile(profile);

        LockedStores stores = loadStores(command.sourceStoreId(), command.targetStoreId());
        validateStoreScope(command.organizationId(), stores);
        requireTargetSafeAndEmpty(stores.target());

        long sourceRevision = requireRevision(stores.source(), "source");
        long targetRevision = requireRevision(stores.target(), "target");
        ResolvedBaseGraph resolved = resolveBaseGraph(stores.source(), profile);
        StoreMenuCloneBaseGraphResult virtualBaseGraph = virtualBaseGraph(resolved);
        List<StoreMenuClonePlannedOption> optionPlan = composeGraph(profile, stores, virtualBaseGraph);
        requireTargetSafeAndEmpty(stores.target());
        requireUnchangedRevision(
            storeRepository.findMenuRevisionById(command.sourceStoreId()),
            sourceRevision,
            "SOURCE_MENU_CHANGED",
            "Source menu revision changed during validation"
        );
        requireUnchangedRevision(
            storeRepository.findMenuRevisionById(command.targetStoreId()),
            targetRevision,
            "TARGET_MENU_CHANGED",
            "Target menu revision changed during validation"
        );

        return new OwnerStoreMenuCloneValidationResult(
            true,
            profile.profileCode(),
            sourceRevision,
            targetRevision,
            profile.stations().size(),
            profile.categories().size(),
            profile.items().size(),
            optionPlan.size(),
            List.of(),
            List.of(),
            List.of()
        );
    }

    @Override
    @Transactional
    public OwnerStoreMenuCloneTransactionResult execute(OwnerStoreMenuCloneTransactionCommand command) {
        validateCommand(command);
        StoreMenuCloneBaseGraphProfile profile = requireBaseGraphProfile(command.sourceStoreId(), command.profileCode());
        validateProfile(profile);

        LockedStores stores = lockStores(command.sourceStoreId(), command.targetStoreId());
        validateStoreScope(command.organizationId(), stores);
        requireTargetSafeAndEmpty(stores.target());

        long sourceRevision = requireRevision(stores.source(), "source");
        long targetRevisionBefore = requireRevision(stores.target(), "target");
        ResolvedBaseGraph resolved = resolveBaseGraph(stores.source(), profile);
        StoreMenuCloneBaseGraphResult baseGraph = persistBaseGraph(stores.target().id, resolved);
        List<StoreMenuClonePlannedOption> optionPlan = composeGraph(profile, stores, baseGraph);
        persistOptionPlan(optionPlan);
        int createdOptionCount = optionPlan.size();

        validatePersistedGraph(stores.target().id, resolved, baseGraph, optionPlan);
        recheckSourceRevision(resolved.snapshot());
        menuRevisionService.incrementRevision(stores.target().id);

        long expectedTargetRevisionAfter = Math.addExact(targetRevisionBefore, 1L);
        Long targetRevisionAfter = storeRepository.findMenuRevisionById(stores.target().id);
        if (!Objects.equals(expectedTargetRevisionAfter, targetRevisionAfter)) {
            throw invalidTarget("Target menu revision did not increment exactly once");
        }

        OwnerStoreMenuCloneSuccessEvidence evidence = new OwnerStoreMenuCloneSuccessEvidence(
            command.requestId(),
            command.organizationId(),
            command.sourceStoreId(),
            command.targetStoreId(),
            command.profileCode(),
            sourceRevision,
            targetRevisionBefore,
            targetRevisionAfter,
            profile.stations().size(),
            profile.categories().size(),
            profile.items().size(),
            createdOptionCount,
            RESULT_CODE
        );
        requestCoordinator.complete(evidence);
        return new OwnerStoreMenuCloneTransactionResult(evidence);
    }

    private void validateValidationCommand(OwnerStoreMenuCloneValidationCommand command) {
        if (command == null
            || command.organizationId() == null
            || command.sourceStoreId() == null
            || command.targetStoreId() == null
            || !isExactNonBlank(command.profileCode())) {
            throw badRequest("MENU_CLONE_REQUEST_INVALID", "Complete menu clone validation scope is required");
        }
        if (command.sourceStoreId().equals(command.targetStoreId())) {
            throw conflict("SOURCE_TARGET_SAME_STORE", "Source and target Stores must differ");
        }
    }

    private void validateCommand(OwnerStoreMenuCloneTransactionCommand command) {
        if (command == null
            || command.requestId() == null
            || command.organizationId() == null
            || command.sourceStoreId() == null
            || command.targetStoreId() == null
            || command.actorUserId() == null
            || !isExactNonBlank(command.profileCode())) {
            throw badRequest("MENU_CLONE_REQUEST_INVALID", "Complete menu clone transaction scope is required");
        }
        if (command.sourceStoreId().equals(command.targetStoreId())) {
            throw conflict("SOURCE_TARGET_SAME_STORE", "Source and target Stores must differ");
        }
    }

    private StoreMenuCloneBaseGraphProfile requireBaseGraphProfile(Long sourceStoreId, String profileCode) {
        StoreMenuCloneProfileDescriptor descriptor = profileRegistry.find(profileCode)
            .orElseThrow(() -> badRequest("MENU_CLONE_REQUEST_INVALID", "Unsupported menu clone profile"));
        if (!(descriptor instanceof StoreMenuCloneBaseGraphProfile profile)
            || !Objects.equals(profile.sourceStoreId(), sourceStoreId)) {
            throw badRequest("MENU_CLONE_REQUEST_INVALID", "Unsupported menu clone profile");
        }
        return profile;
    }

    private void validateProfile(StoreMenuCloneBaseGraphProfile profile) {
        if (!isExactNonBlank(profile.profileCode())
            || !isExactNonBlank(profile.profileFingerprint())
            || profile.sourceStoreId() == null) {
            throw invalidProfile("Store menu clone profile identity is incomplete");
        }
        List<CategorySelection> categories = requireSelections(profile.categories(), "category");
        List<StationSelection> stations = requireSelections(profile.stations(), "station");
        List<ItemSelection> items = requireSelections(profile.items(), "item");

        Set<String> targetCategoryCodes = requireUniqueNormalized(
            categories.stream().map(CategorySelection::targetCode).toList(),
            "target category code",
            true
        );
        requireUniqueNormalized(
            categories.stream()
                .filter(category -> category.sourcePolicy() == CategorySourcePolicy.REQUIRED_SOURCE_CODE)
                .map(CategorySelection::sourceCode)
                .toList(),
            "source category code",
            true
        );
        Set<String> targetStationCodes = requireUniqueNormalized(
            stations.stream().map(StationSelection::targetCode).toList(),
            "target station code",
            true
        );
        requireUniqueNormalized(
            stations.stream()
                .filter(station -> station.sourcePolicy() == StationSourcePolicy.REQUIRED_SOURCE_CODE)
                .map(StationSelection::sourceCode)
                .toList(),
            "fixed source station code",
            true
        );
        requireUniqueNormalized(
            items.stream()
                .filter(item -> item.sourcePolicy() != SourcePolicy.CREATE_ONLY)
                .map(ItemSelection::sourceSku)
                .toList(),
            "source item SKU",
            false
        );
        requireUniqueNormalized(
            items.stream().map(ItemSelection::targetSku).toList(),
            "target item SKU",
            false
        );
        requireUniquePositiveSortOrders(
            categories.stream().map(CategorySelection::targetSortOrder).toList(),
            "category"
        );
        requireUniquePositiveSortOrders(
            stations.stream().map(StationSelection::targetSortOrder).toList(),
            "station"
        );

        for (CategorySelection category : categories) {
            validateCategorySelection(category);
        }
        for (StationSelection station : stations) {
            validateStationSelection(station);
        }

        Map<String, List<ItemSelection>> itemsByCategory = items.stream().collect(Collectors.groupingBy(
            item -> normalizeCode(item.targetCategoryCode())
        ));
        for (Map.Entry<String, List<ItemSelection>> entry : itemsByCategory.entrySet()) {
            if (!targetCategoryCodes.contains(entry.getKey())) {
                throw invalidProfile("Item references an unknown target category");
            }
            requireUniquePositiveSortOrders(
                entry.getValue().stream().map(ItemSelection::targetSortOrder).toList(),
                "item"
            );
        }
        for (ItemSelection item : items) {
            if (!targetStationCodes.contains(normalizeCode(item.targetStationCode()))) {
                throw invalidProfile("Item references an unknown target station");
            }
            validateItemSelection(item);
        }
        for (StationSelection station : stations) {
            if (station.sourcePolicy() == StationSourcePolicy.UNIQUE_ACTIVE_STATION_FROM_SELECTED_ITEMS
                && items.stream().noneMatch(item -> item.sourcePolicy() != SourcePolicy.CREATE_ONLY
                    && Objects.equals(normalizeCode(item.targetStationCode()), normalizeCode(station.targetCode())))) {
                throw invalidProfile("Dynamic source station requires selected source-backed items");
            }
        }
    }

    private void validateCategorySelection(CategorySelection category) {
        if (category.sourcePolicy() == null
            || isBlank(category.targetNameZh())
            || isBlank(category.targetNameEn())) {
            throw invalidProfile("Target category names and source policy are required");
        }
        if (category.sourcePolicy() == CategorySourcePolicy.REQUIRED_SOURCE_CODE) {
            if (isBlank(category.sourceCode())) {
                throw invalidProfile("Source-backed category requires a source code");
            }
        } else if (!isBlank(category.sourceCode())) {
            throw invalidProfile("Create-only category must not declare a source code");
        }
    }

    private void validateStationSelection(StationSelection station) {
        if (station.sourcePolicy() == null || isBlank(station.targetName())) {
            throw invalidProfile("Target station values and source policy are required");
        }
        if (station.sourcePolicy() == StationSourcePolicy.REQUIRED_SOURCE_CODE) {
            if (isBlank(station.sourceCode())) {
                throw invalidProfile("Fixed source station code is required");
            }
        } else if (!isBlank(station.sourceCode())) {
            throw invalidProfile("Dynamic source station must not declare a fixed source code");
        }
    }

    private void validateItemSelection(ItemSelection item) {
        if (item.sourcePolicy() == null
            || isBlank(item.targetNameZh())
            || isBlank(item.targetNameEn())
            || item.targetBasePrice() == null
            || item.targetBasePrice().signum() < 0
            || !item.targetActive()
            || item.targetSoldOut()
            || item.roles().isEmpty()) {
            throw invalidProfile("Target item values, source policy, and roles are invalid");
        }
        if (item.sourcePolicy() == SourcePolicy.CREATE_ONLY) {
            if (!isBlank(item.sourceSku()) || isBlank(item.profileCreatedItemType())) {
                throw invalidProfile("Create-only items require a profile item type and no source SKU");
            }
            return;
        }
        if (isBlank(item.sourceSku())) {
            throw invalidProfile("Source-backed items require a source SKU");
        }
        if (item.sourcePolicy() == SourcePolicy.CLONE_IF_ACTIVE_OR_CREATE
            && isBlank(item.profileCreatedItemType())) {
            throw invalidProfile("Clone-or-create items require a profile item type");
        }
    }

    private LockedStores lockStores(Long sourceStoreId, Long targetStoreId) {
        List<Store> locked = menuRevisionService.lockStoresInOrder(List.of(sourceStoreId, targetStoreId));
        Map<Long, Store> byId = locked.stream().collect(Collectors.toMap(store -> store.id, Function.identity()));
        Store source = byId.get(sourceStoreId);
        Store target = byId.get(targetStoreId);
        if (source == null || target == null) {
            throw forbidden("MENU_CLONE_FORBIDDEN", "Source or target Store is outside the clone scope");
        }
        return new LockedStores(source, target);
    }

    private LockedStores loadStores(Long sourceStoreId, Long targetStoreId) {
        Store source = storeRepository.findById(sourceStoreId).orElse(null);
        Store target = storeRepository.findById(targetStoreId).orElse(null);
        if (source == null || target == null) {
            throw forbidden("MENU_CLONE_FORBIDDEN", "Source or target Store is outside the clone scope");
        }
        return new LockedStores(source, target);
    }

    private void validateStoreScope(Long organizationId, LockedStores stores) {
        if (!Objects.equals(organizationId, stores.source().organization_id)
            || !Objects.equals(organizationId, stores.target().organization_id)) {
            throw forbidden("MENU_CLONE_FORBIDDEN", "Source and target Stores must belong to the Organization");
        }
    }

    private void requireTargetSafeAndEmpty(Store target) {
        boolean inactive = target.status != null && STORE_STATUS_INACTIVE.equalsIgnoreCase(target.status.trim());
        boolean printingDisabled = Boolean.FALSE.equals(target.printing_enabled)
            && target.printing_mode != null
            && PRINTING_MODE_DISABLED.equalsIgnoreCase(target.printing_mode.trim());
        if (!inactive || !printingDisabled) {
            throw conflict("TARGET_STORE_NOT_READY", "Target Store must be inactive with printing disabled");
        }
        if (categoryRepository.countAllByStoreId(target.id) != 0L
            || stationRepository.countAllByStoreId(target.id) != 0L
            || itemRepository.countAllByStoreId(target.id) != 0L) {
            throw conflict("TARGET_MENU_NOT_EMPTY", "Target Store menu must be empty");
        }
    }

    private long requireRevision(Store store, String label) {
        if (store.menu_revision == null) {
            throw conflict("SOURCE_MENU_CHANGED", label + " menu revision is unavailable");
        }
        return store.menu_revision;
    }

    private void requireUnchangedRevision(Long actual, long expected, String code, String message) {
        if (!Objects.equals(expected, actual)) {
            throw conflict(code, message);
        }
    }

    private ResolvedBaseGraph resolveBaseGraph(Store source, StoreMenuCloneBaseGraphProfile profile) {
        List<MenuCategory> allCategories = categoryRepository.findAllByStoreIdOrderByIdAsc(source.id);
        List<Station> allStations = stationRepository.findAllByStoreIdOrderByIdAsc(source.id);
        List<MenuItem> allItems = itemRepository.findAllByStoreIdOrderByIdAsc(source.id);

        Map<String, List<MenuCategory>> categoriesByCode = groupByNormalized(
            allCategories,
            category -> category.code,
            true
        );
        Map<String, SourceCategory> sourceCategoryByTargetCode = new LinkedHashMap<>();
        for (CategorySelection selection : profile.categories()) {
            SourceCategory category = null;
            if (selection.sourcePolicy() == CategorySourcePolicy.REQUIRED_SOURCE_CODE) {
                category = sourceCategory(requireUniqueActive(
                    categoriesByCode.get(normalizeCode(selection.sourceCode())),
                    value -> value.is_active,
                    "SOURCE_MENU_CHANGED",
                    "Required source category is missing, inactive, or ambiguous"
                ));
            }
            sourceCategoryByTargetCode.put(normalizeCode(selection.targetCode()), category);
        }

        Map<String, List<MenuItem>> itemsBySku = groupByNormalized(allItems, item -> item.sku, false);
        Map<String, SourceItem> sourceItemByTargetSku = new LinkedHashMap<>();
        for (ItemSelection selection : profile.items()) {
            SourceItem sourceItem = resolveItem(selection, itemsBySku.get(normalizeSku(selection.sourceSku())));
            sourceItemByTargetSku.put(normalizeSku(selection.targetSku()), sourceItem);
        }

        Map<String, List<Station>> stationsByCode = groupByNormalized(allStations, station -> station.code, true);
        Map<Long, Station> stationsById = allStations.stream().collect(Collectors.toMap(
            station -> station.id,
            Function.identity()
        ));
        Map<String, SourceStation> sourceStationByTargetCode = new LinkedHashMap<>();
        for (StationSelection selection : profile.stations()) {
            Station station = selection.sourcePolicy() == StationSourcePolicy.REQUIRED_SOURCE_CODE
                ? requireUniqueActive(
                    stationsByCode.get(normalizeCode(selection.sourceCode())),
                    value -> value.is_active,
                    "SOURCE_MENU_CHANGED",
                    "Required source station is missing, inactive, or ambiguous"
                )
                : resolveDynamicStation(selection, profile.items(), sourceItemByTargetSku, stationsById);
            sourceStationByTargetCode.put(normalizeCode(selection.targetCode()), sourceStation(station));
        }

        requireUniqueResolvedIds(
            sourceCategoryByTargetCode.values().stream().filter(Objects::nonNull).map(SourceCategory::id).toList(),
            "category"
        );
        requireUniqueResolvedIds(sourceStationByTargetCode.values().stream().map(SourceStation::id).toList(), "station");
        validateSourceItemOwnership(profile, sourceCategoryByTargetCode, sourceStationByTargetCode, sourceItemByTargetSku);

        List<SourceCategory> selectedCategories = profile.categories().stream()
            .map(selection -> sourceCategoryByTargetCode.get(normalizeCode(selection.targetCode())))
            .filter(Objects::nonNull)
            .toList();
        List<SourceStation> selectedStations = profile.stations().stream()
            .map(selection -> sourceStationByTargetCode.get(normalizeCode(selection.targetCode())))
            .toList();
        List<SourceItem> selectedItems = profile.items().stream()
            .map(selection -> sourceItemByTargetSku.get(normalizeSku(selection.targetSku())))
            .filter(Objects::nonNull)
            .toList();
        StoreMenuCloneSnapshot snapshot = new StoreMenuCloneSnapshot(
            source.id,
            source.organization_id,
            source.menu_revision,
            source.menu_updated_at,
            selectedCategories,
            selectedStations,
            selectedItems,
            readOptionClosure(source.id, selectedItems, allItems)
        );
        return new ResolvedBaseGraph(
            snapshot,
            profile,
            sourceCategoryByTargetCode,
            sourceStationByTargetCode,
            sourceItemByTargetSku
        );
    }

    private SourceItem resolveItem(ItemSelection selection, List<MenuItem> matches) {
        if (selection.sourcePolicy() == SourcePolicy.CREATE_ONLY) {
            return null;
        }
        List<MenuItem> candidates = matches == null ? List.of() : matches;
        if (candidates.size() > 1) {
            throw conflict("SOURCE_SKU_DUPLICATE", "Required source item SKU is ambiguous");
        }
        if (candidates.isEmpty() || !Boolean.TRUE.equals(candidates.get(0).is_active)) {
            if (selection.sourcePolicy() == SourcePolicy.CLONE_IF_ACTIVE_OR_CREATE) {
                return null;
            }
            throw conflict("SOURCE_SKU_MISSING", "Required source item SKU is missing or inactive");
        }
        return sourceItem(candidates.get(0));
    }

    private Station resolveDynamicStation(
        StationSelection stationSelection,
        List<ItemSelection> itemSelections,
        Map<String, SourceItem> sourceItemByTargetSku,
        Map<Long, Station> stationsById
    ) {
        Set<Long> stationIds = new LinkedHashSet<>();
        for (ItemSelection itemSelection : itemSelections) {
            if (!Objects.equals(
                normalizeCode(itemSelection.targetStationCode()),
                normalizeCode(stationSelection.targetCode())
            )) {
                continue;
            }
            SourceItem sourceItem = sourceItemByTargetSku.get(normalizeSku(itemSelection.targetSku()));
            if (sourceItem != null) {
                if (sourceItem.stationId() == null) {
                    throw conflict("SOURCE_DRINK_STATION_AMBIGUOUS", "Selected source item has no station");
                }
                stationIds.add(sourceItem.stationId());
            }
        }
        if (stationIds.size() != 1) {
            throw conflict(
                "SOURCE_DRINK_STATION_AMBIGUOUS",
                "Selected source items must resolve to exactly one active source station"
            );
        }
        Station station = stationsById.get(stationIds.iterator().next());
        if (station == null || !Boolean.TRUE.equals(station.is_active)) {
            throw conflict("SOURCE_DRINK_STATION_AMBIGUOUS", "Resolved source station is missing or inactive");
        }
        return station;
    }

    private void validateSourceItemOwnership(
        StoreMenuCloneBaseGraphProfile profile,
        Map<String, SourceCategory> categories,
        Map<String, SourceStation> stations,
        Map<String, SourceItem> items
    ) {
        for (ItemSelection selection : profile.items()) {
            SourceItem item = items.get(normalizeSku(selection.targetSku()));
            if (item == null) {
                continue;
            }
            SourceCategory category = categories.get(normalizeCode(selection.targetCategoryCode()));
            SourceStation station = stations.get(normalizeCode(selection.targetStationCode()));
            if ((category != null && !Objects.equals(item.categoryId(), category.id()))
                || !Objects.equals(item.stationId(), station.id())) {
                throw conflict("SOURCE_MENU_CHANGED", "Selected source item ownership does not match its profile mapping");
            }
        }
    }

    private List<SourceOption> readOptionClosure(
        Long sourceStoreId,
        List<SourceItem> selectedItems,
        List<MenuItem> allSourceItems
    ) {
        if (selectedItems.isEmpty()) {
            return List.of();
        }
        List<Long> selectedItemIds = selectedItems.stream().map(SourceItem::id).toList();
        Map<Long, MenuItemOption> optionsById = new LinkedHashMap<>();
        ArrayDeque<Long> parentQueue = new ArrayDeque<>();
        for (MenuItemOption option : optionRepository.findAllByStoreIdAndMenuItemIdsOrdered(
            sourceStoreId,
            selectedItemIds
        )) {
            addOptionToClosure(option, optionsById, parentQueue);
        }
        while (!parentQueue.isEmpty()) {
            Set<Long> parentIds = new LinkedHashSet<>();
            while (!parentQueue.isEmpty()) {
                Long parentId = parentQueue.removeFirst();
                if (!optionsById.containsKey(parentId)) {
                    parentIds.add(parentId);
                }
            }
            if (parentIds.isEmpty()) {
                continue;
            }
            List<MenuItemOption> parents = optionRepository.findAllByStoreIdAndIdInOrderByIdAsc(
                sourceStoreId,
                List.copyOf(parentIds)
            );
            if (!parents.stream().map(parent -> parent.id).collect(Collectors.toSet()).equals(parentIds)) {
                throw conflict("SOURCE_OPTION_AMBIGUOUS", "Source option parent is missing or outside the Store");
            }
            parents.forEach(parent -> addOptionToClosure(parent, optionsById, parentQueue));
        }

        Map<Long, MenuItem> sourceItemsById = allSourceItems.stream().collect(Collectors.toMap(
            item -> item.id,
            Function.identity()
        ));
        List<SourceOption> snapshot = new ArrayList<>();
        for (MenuItemOption option : optionsById.values()) {
            if (option.id == null || option.menu_item_id == null) {
                throw conflict("SOURCE_OPTION_AMBIGUOUS", "Source option ownership evidence is incomplete");
            }
            MenuItem owner = sourceItemsById.get(option.menu_item_id);
            if (owner == null || !Objects.equals(sourceStoreId, owner.store_id)) {
                throw conflict("SOURCE_OPTION_AMBIGUOUS", "Source option owner evidence is invalid");
            }
            snapshot.add(new SourceOption(
                option.id,
                option.menu_item_id,
                owner.store_id,
                option.option_type,
                option.option_code,
                option.option_group,
                option.parent_option_id,
                option.sort_order,
                option.name_zh,
                option.name_en,
                option.price_delta,
                option.is_active
            ));
        }
        return List.copyOf(snapshot);
    }

    private void addOptionToClosure(
        MenuItemOption option,
        Map<Long, MenuItemOption> optionsById,
        ArrayDeque<Long> parentQueue
    ) {
        if (option == null || option.id == null) {
            throw conflict("SOURCE_OPTION_AMBIGUOUS", "Source option identity is incomplete");
        }
        if (optionsById.putIfAbsent(option.id, option) == null && option.parent_option_id != null) {
            parentQueue.addLast(option.parent_option_id);
        }
    }

    private StoreMenuCloneBaseGraphResult virtualBaseGraph(ResolvedBaseGraph graph) {
        long nextVirtualId = -1L;
        Map<Long, Long> categoriesBySourceId = new LinkedHashMap<>();
        for (CategorySelection selection : graph.profile().categories()) {
            SourceCategory source = graph.sourceCategoryByTargetCode().get(normalizeCode(selection.targetCode()));
            long targetId = nextVirtualId--;
            if (source != null) {
                categoriesBySourceId.put(source.id(), targetId);
            }
        }

        Map<Long, Long> stationsBySourceId = new LinkedHashMap<>();
        for (StationSelection selection : graph.profile().stations()) {
            SourceStation source = graph.sourceStationByTargetCode().get(normalizeCode(selection.targetCode()));
            stationsBySourceId.put(source.id(), nextVirtualId--);
        }

        Map<Long, Long> itemsBySourceId = new LinkedHashMap<>();
        Map<String, Long> itemsByTargetSku = new LinkedHashMap<>();
        Map<Long, Set<ItemRole>> rolesByTargetId = new LinkedHashMap<>();
        for (ItemSelection selection : graph.profile().items()) {
            long targetId = nextVirtualId--;
            SourceItem source = graph.sourceItemByTargetSku().get(normalizeSku(selection.targetSku()));
            if (source != null) {
                itemsBySourceId.put(source.id(), targetId);
            }
            itemsByTargetSku.put(normalizeSku(selection.targetSku()), targetId);
            rolesByTargetId.put(targetId, selection.roles());
        }

        return new StoreMenuCloneBaseGraphResult(
            graph.snapshot(),
            categoriesBySourceId,
            stationsBySourceId,
            itemsBySourceId,
            itemsByTargetSku,
            rolesByTargetId
        );
    }

    private StoreMenuCloneBaseGraphResult persistBaseGraph(Long targetStoreId, ResolvedBaseGraph graph) {
        LocalDateTime now = LocalDateTime.now();
        PersistedCategories categories = createCategories(targetStoreId, graph, now);
        PersistedStations stations = createStations(targetStoreId, graph, now);
        PersistedItems items = createItems(targetStoreId, graph, categories.byTargetCode(), stations.byTargetCode(), now);
        return new StoreMenuCloneBaseGraphResult(
            graph.snapshot(),
            categories.targetIdBySourceId(),
            stations.targetIdBySourceId(),
            items.targetIdBySourceId(),
            items.targetIdByTargetSku(),
            items.rolesByTargetItemId()
        );
    }

    private PersistedCategories createCategories(Long targetStoreId, ResolvedBaseGraph graph, LocalDateTime now) {
        Map<String, MenuCategory> byTargetCode = new LinkedHashMap<>();
        Map<Long, Long> targetIdBySourceId = new LinkedHashMap<>();
        for (CategorySelection selection : graph.profile().categories()) {
            SourceCategory source = graph.sourceCategoryByTargetCode().get(normalizeCode(selection.targetCode()));
            MenuCategory target = new MenuCategory();
            target.store_id = targetStoreId;
            target.code = selection.targetCode().trim();
            target.name_zh = selection.targetNameZh().trim();
            target.name_en = selection.targetNameEn().trim();
            target.sort_order = selection.targetSortOrder();
            target.is_active = selection.targetActive();
            target.created_at = now;
            target.updated_at = now;
            target = categoryRepository.saveAndFlush(target);
            if (source == null) {
                if (target.id == null) {
                    throw invalidTarget("Target category did not receive a fresh ID");
                }
            } else {
                assertFreshId(target.id, source.id(), "category");
                targetIdBySourceId.put(source.id(), target.id);
            }
            byTargetCode.put(normalizeCode(selection.targetCode()), target);
        }
        return new PersistedCategories(byTargetCode, targetIdBySourceId);
    }

    private PersistedStations createStations(Long targetStoreId, ResolvedBaseGraph graph, LocalDateTime now) {
        Map<String, Station> byTargetCode = new LinkedHashMap<>();
        Map<Long, Long> targetIdBySourceId = new LinkedHashMap<>();
        for (StationSelection selection : graph.profile().stations()) {
            SourceStation source = graph.sourceStationByTargetCode().get(normalizeCode(selection.targetCode()));
            Station target = new Station();
            target.store_id = targetStoreId;
            target.code = selection.targetCode().trim();
            target.name = selection.targetName().trim();
            target.sort_order = selection.targetSortOrder();
            target.is_active = selection.targetActive();
            target.created_at = now;
            target.updated_at = now;
            target = stationRepository.saveAndFlush(target);
            assertFreshId(target.id, source.id(), "station");
            byTargetCode.put(normalizeCode(selection.targetCode()), target);
            targetIdBySourceId.put(source.id(), target.id);
        }
        return new PersistedStations(byTargetCode, targetIdBySourceId);
    }

    private PersistedItems createItems(
        Long targetStoreId,
        ResolvedBaseGraph graph,
        Map<String, MenuCategory> targetCategories,
        Map<String, Station> targetStations,
        LocalDateTime now
    ) {
        Map<Long, Long> targetIdBySourceId = new LinkedHashMap<>();
        Map<String, Long> targetIdByTargetSku = new LinkedHashMap<>();
        Map<Long, Set<ItemRole>> rolesByTargetItemId = new LinkedHashMap<>();
        for (ItemSelection selection : graph.profile().items()) {
            SourceItem source = graph.sourceItemByTargetSku().get(normalizeSku(selection.targetSku()));
            MenuItem target = new MenuItem();
            target.store_id = targetStoreId;
            target.category_id = targetCategories.get(normalizeCode(selection.targetCategoryCode())).id;
            target.station_id = targetStations.get(normalizeCode(selection.targetStationCode())).id;
            target.sku = selection.targetSku().trim();
            target.name_zh = selection.targetNameZh().trim();
            target.name_en = selection.targetNameEn().trim();
            target.item_type = source == null ? selection.profileCreatedItemType().trim() : source.itemType();
            target.base_price = selection.targetBasePrice();
            target.cost_per_item = source == null ? null : source.costPerItem();
            target.is_active = selection.targetActive();
            target.is_sold_out = selection.targetSoldOut();
            target.sort_order = selection.targetSortOrder();
            target.created_at = now;
            target.updated_at = now;
            target = itemRepository.saveAndFlush(target);
            if (source == null) {
                if (target.id == null) {
                    throw invalidTarget("Target item did not receive a fresh ID");
                }
            } else {
                assertFreshId(target.id, source.id(), "item");
                targetIdBySourceId.put(source.id(), target.id);
            }
            targetIdByTargetSku.put(normalizeSku(selection.targetSku()), target.id);
            rolesByTargetItemId.put(target.id, selection.roles());
        }
        return new PersistedItems(targetIdBySourceId, targetIdByTargetSku, rolesByTargetItemId);
    }

    private List<StoreMenuClonePlannedOption> composeGraph(
        StoreMenuCloneBaseGraphProfile profile,
        LockedStores stores,
        StoreMenuCloneBaseGraphResult baseGraph
    ) {
        List<StoreMenuCloneGraphComposer> matching = graphComposers.stream()
            .filter(composer -> composer.supports(profile.profileCode()))
            .toList();
        Set<String> identities = new LinkedHashSet<>();
        Set<String> slots = new LinkedHashSet<>();
        for (StoreMenuCloneGraphComposer composer : matching) {
            if (!isExactNonBlank(composer.identity()) || composer.phase() == null || composer.order() < 0) {
                throw invalidProfile("Store menu clone composer identity, phase, and order are required");
            }
            if (!identities.add(composer.identity())) {
                throw invalidProfile("Store menu clone composer identities must be unique");
            }
            String slot = composer.phase().name() + ":" + composer.order();
            if (!slots.add(slot)) {
                throw invalidProfile("Store menu clone composer phase and order must be unambiguous");
            }
        }
        matching = matching.stream()
            .sorted(Comparator
                .comparing(StoreMenuCloneGraphComposer::phase)
                .thenComparingInt(StoreMenuCloneGraphComposer::order)
                .thenComparing(StoreMenuCloneGraphComposer::identity))
            .toList();
        StoreMenuCloneCompositionContext context = new StoreMenuCloneCompositionContext(
            profile,
            stores.source().id,
            stores.target().id,
            baseGraph
        );
        for (StoreMenuCloneGraphComposer composer : matching) {
            int optionCountBefore = context.options().size();
            int contribution = composer.compose(context);
            if (contribution < 0) {
                throw invalidProfile("Store menu clone composer returned a negative created count");
            }
            if (contribution != context.options().size() - optionCountBefore) {
                throw invalidProfile("Store menu clone composer count does not match its planned options");
            }
        }
        List<StoreMenuClonePlannedOption> plan = context.options();
        Set<Long> allowedTargetItemIds = Set.copyOf(baseGraph.targetItemIdByTargetSku().values());
        if (plan.stream().anyMatch(option -> !allowedTargetItemIds.contains(option.targetItemId()))) {
            throw invalidTarget("Target option plan references an item outside the target graph");
        }
        validatePlannedParentGraph(plan);
        return plan;
    }

    private void validatePlannedParentGraph(List<StoreMenuClonePlannedOption> plan) {
        Map<OptionKey, StoreMenuClonePlannedOption> byKey = new LinkedHashMap<>();
        for (StoreMenuClonePlannedOption option : plan) {
            OptionKey key = new OptionKey(option.targetItemId(), normalizeSku(option.optionCode()));
            if (key.optionCode() == null || byKey.putIfAbsent(key, option) != null) {
                throw invalidTarget("Target option plan codes are incomplete or duplicated");
            }
        }

        Set<OptionKey> visiting = new LinkedHashSet<>();
        Set<OptionKey> visited = new LinkedHashSet<>();
        for (OptionKey key : byKey.keySet()) {
            visitPlannedParent(key, byKey, visiting, visited);
        }
    }

    private void visitPlannedParent(
        OptionKey key,
        Map<OptionKey, StoreMenuClonePlannedOption> byKey,
        Set<OptionKey> visiting,
        Set<OptionKey> visited
    ) {
        if (visited.contains(key)) {
            return;
        }
        if (!visiting.add(key)) {
            throw invalidTarget("Target option plan parent graph contains a cycle");
        }
        StoreMenuClonePlannedOption option = byKey.get(key);
        if (option.parentOptionCode() != null) {
            OptionKey parentKey = new OptionKey(
                option.targetItemId(),
                normalizeSku(option.parentOptionCode())
            );
            if (parentKey.optionCode() == null || !byKey.containsKey(parentKey)) {
                throw invalidTarget("Target option plan parent mapping is incomplete");
            }
            visitPlannedParent(parentKey, byKey, visiting, visited);
        }
        visiting.remove(key);
        visited.add(key);
    }

    private void persistOptionPlan(List<StoreMenuClonePlannedOption> plan) {
        if (plan.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<MenuItemOption> entities = new ArrayList<>();
        Map<OptionKey, MenuItemOption> entitiesByKey = new LinkedHashMap<>();
        for (StoreMenuClonePlannedOption planned : plan) {
            if (!isExactNonBlank(planned.optionType())
                || !isExactNonBlank(planned.optionCode())
                || !isExactNonBlank(planned.optionGroup())
                || planned.sortOrder() == null
                || planned.sortOrder() < 1
                || planned.priceDelta() == null
                || planned.active() == null) {
                throw invalidTarget("Target option plan is incomplete");
            }
            MenuItemOption entity = new MenuItemOption();
            entity.menu_item_id = planned.targetItemId();
            entity.option_type = planned.optionType();
            entity.option_code = planned.optionCode();
            entity.option_group = planned.optionGroup();
            entity.parent_option_id = null;
            entity.sort_order = planned.sortOrder();
            entity.name_zh = planned.nameZh();
            entity.name_en = planned.nameEn();
            entity.price_delta = planned.priceDelta();
            entity.is_active = planned.active();
            entity.created_at = now;
            entity.updated_at = now;
            OptionKey key = new OptionKey(planned.targetItemId(), normalizeSku(planned.optionCode()));
            if (entitiesByKey.putIfAbsent(key, entity) != null) {
                throw invalidTarget("Target option plan contains duplicate option codes");
            }
            entities.add(entity);
        }

        optionRepository.saveAllAndFlush(entities);
        for (int index = 0; index < plan.size(); index++) {
            StoreMenuClonePlannedOption planned = plan.get(index);
            MenuItemOption entity = entities.get(index);
            if (entity.id == null || Objects.equals(entity.id, planned.sourceOptionId())) {
                throw invalidTarget("Target option did not receive a fresh ID");
            }
        }

        boolean hasParents = false;
        for (int index = 0; index < plan.size(); index++) {
            StoreMenuClonePlannedOption planned = plan.get(index);
            if (planned.parentOptionCode() == null) {
                continue;
            }
            MenuItemOption parent = entitiesByKey.get(new OptionKey(
                planned.targetItemId(),
                normalizeSku(planned.parentOptionCode())
            ));
            if (parent == null || parent.id == null) {
                throw invalidTarget("Target option parent mapping is incomplete");
            }
            entities.get(index).parent_option_id = parent.id;
            hasParents = true;
        }
        if (hasParents) {
            optionRepository.saveAllAndFlush(entities);
        }
    }

    private void validatePersistedGraph(
        Long targetStoreId,
        ResolvedBaseGraph resolved,
        StoreMenuCloneBaseGraphResult result,
        List<StoreMenuClonePlannedOption> optionPlan
    ) {
        List<MenuCategory> categories = categoryRepository.findAllByStoreIdOrderByIdAsc(targetStoreId);
        List<Station> stations = stationRepository.findAllByStoreIdOrderByIdAsc(targetStoreId);
        List<MenuItem> items = itemRepository.findAllByStoreIdOrderByIdAsc(targetStoreId);
        if (categories.size() != resolved.profile().categories().size()
            || stations.size() != resolved.profile().stations().size()
            || items.size() != resolved.profile().items().size()
            || result.targetCategoryIdBySourceId().size() > categories.size()
            || result.targetStationIdBySourceId().size() != stations.size()
            || result.targetItemIdBySourceId().size() > items.size()
            || result.targetItemIdByTargetSku().size() != items.size()
            || result.rolesByTargetItemId().size() != items.size()) {
            throw invalidTarget("Target base graph row counts are invalid");
        }

        Map<String, MenuCategory> categoryByCode = uniqueByNormalizedCode(categories, value -> value.code);
        Map<String, Station> stationByCode = uniqueByNormalizedCode(stations, value -> value.code);
        Map<String, MenuItem> itemBySku = uniqueByNormalizedSku(items, value -> value.sku);
        for (CategorySelection selection : resolved.profile().categories()) {
            MenuCategory category = categoryByCode.get(normalizeCode(selection.targetCode()));
            if (category == null
                || !Objects.equals(selection.targetNameZh().trim(), category.name_zh)
                || !Objects.equals(selection.targetNameEn().trim(), category.name_en)
                || !Objects.equals(selection.targetActive(), category.is_active)
                || !Objects.equals(selection.targetSortOrder(), category.sort_order)) {
                throw invalidTarget("Target category mapping is invalid");
            }
        }
        for (StationSelection selection : resolved.profile().stations()) {
            Station station = stationByCode.get(normalizeCode(selection.targetCode()));
            if (station == null
                || !Objects.equals(selection.targetName().trim(), station.name)
                || !Objects.equals(selection.targetActive(), station.is_active)
                || !Objects.equals(selection.targetSortOrder(), station.sort_order)) {
                throw invalidTarget("Target station mapping is invalid");
            }
        }
        for (ItemSelection selection : resolved.profile().items()) {
            MenuItem item = itemBySku.get(normalizeSku(selection.targetSku()));
            MenuCategory category = categoryByCode.get(normalizeCode(selection.targetCategoryCode()));
            Station station = stationByCode.get(normalizeCode(selection.targetStationCode()));
            SourceItem source = resolved.sourceItemByTargetSku().get(normalizeSku(selection.targetSku()));
            String expectedType = source == null ? selection.profileCreatedItemType().trim() : source.itemType();
            Object expectedCost = source == null ? null : source.costPerItem();
            if (item == null
                || !Objects.equals(selection.targetNameZh().trim(), item.name_zh)
                || !Objects.equals(selection.targetNameEn().trim(), item.name_en)
                || !Objects.equals(selection.targetBasePrice(), item.base_price)
                || !Objects.equals(selection.targetActive(), item.is_active)
                || !Objects.equals(selection.targetSoldOut(), item.is_sold_out)
                || !Objects.equals(selection.targetSortOrder(), item.sort_order)
                || !Objects.equals(expectedType, item.item_type)
                || !Objects.equals(expectedCost, item.cost_per_item)
                || !Objects.equals(category.id, item.category_id)
                || !Objects.equals(station.id, item.station_id)) {
                throw invalidTarget("Target item mapping is invalid");
            }
        }

        List<Long> targetItemIds = items.stream().map(item -> item.id).toList();
        List<MenuItemOption> targetOptions = optionRepository.findAllByStoreIdAndMenuItemIdsOrdered(
            targetStoreId,
            targetItemIds
        );
        if (targetOptions.size() != optionPlan.size()) {
            throw invalidTarget("Target option count does not match composer evidence");
        }
        Set<Long> targetIds = Set.copyOf(targetItemIds);
        Map<Long, MenuItemOption> optionById = targetOptions.stream().collect(Collectors.toMap(
            option -> option.id,
            Function.identity()
        ));
        Map<OptionKey, MenuItemOption> optionByKey = new LinkedHashMap<>();
        for (MenuItemOption option : targetOptions) {
            MenuItemOption parent = option.parent_option_id == null ? null : optionById.get(option.parent_option_id);
            if (!targetIds.contains(option.menu_item_id)
                || (option.parent_option_id != null
                    && (parent == null || !Objects.equals(option.menu_item_id, parent.menu_item_id)))) {
                throw invalidTarget("Target option ownership or parent mapping is invalid");
            }
            OptionKey key = new OptionKey(option.menu_item_id, normalizeSku(option.option_code));
            if (key.optionCode() == null || optionByKey.putIfAbsent(key, option) != null) {
                throw invalidTarget("Target option codes are incomplete or duplicated");
            }
        }
        for (StoreMenuClonePlannedOption planned : optionPlan) {
            OptionKey key = new OptionKey(planned.targetItemId(), normalizeSku(planned.optionCode()));
            MenuItemOption option = optionByKey.get(key);
            MenuItemOption expectedParent = planned.parentOptionCode() == null
                ? null
                : optionByKey.get(new OptionKey(
                    planned.targetItemId(),
                    normalizeSku(planned.parentOptionCode())
                ));
            if (planned.parentOptionCode() != null && expectedParent == null) {
                throw invalidTarget("Persisted target option parent does not match its logical plan");
            }
            Long expectedParentId = expectedParent == null ? null : expectedParent.id;
            if (option == null
                || !Objects.equals(planned.optionType(), option.option_type)
                || !Objects.equals(planned.optionCode(), option.option_code)
                || !Objects.equals(planned.optionGroup(), option.option_group)
                || !Objects.equals(expectedParentId, option.parent_option_id)
                || !Objects.equals(planned.sortOrder(), option.sort_order)
                || !Objects.equals(planned.nameZh(), option.name_zh)
                || !Objects.equals(planned.nameEn(), option.name_en)
                || !sameAmount(planned.priceDelta(), option.price_delta)
                || !Objects.equals(planned.active(), option.is_active)) {
                throw invalidTarget("Persisted target option does not match its logical plan");
            }
        }
    }

    private boolean sameAmount(BigDecimal expected, BigDecimal actual) {
        return expected == null ? actual == null : actual != null && expected.compareTo(actual) == 0;
    }

    private void recheckSourceRevision(StoreMenuCloneSnapshot snapshot) {
        Long currentRevision = storeRepository.findMenuRevisionById(snapshot.storeId());
        if (!Objects.equals(snapshot.menuRevision(), currentRevision)) {
            throw conflict("SOURCE_MENU_CHANGED", "Source menu revision changed during clone");
        }
    }

    private <T> List<T> requireSelections(List<T> values, String label) {
        if (values == null || values.isEmpty() || values.stream().anyMatch(Objects::isNull)) {
            throw invalidProfile("Store profile requires at least one valid " + label + " selection");
        }
        return values;
    }

    private Set<String> requireUniqueNormalized(List<String> values, String label, boolean code) {
        List<String> normalized = values.stream()
            .map(value -> code ? normalizeCode(value) : normalizeSku(value))
            .toList();
        if (normalized.stream().anyMatch(Objects::isNull) || Set.copyOf(normalized).size() != normalized.size()) {
            throw invalidProfile("Store profile " + label + " values must be nonblank and unique");
        }
        return Set.copyOf(normalized);
    }

    private void requireUniquePositiveSortOrders(List<Integer> values, String label) {
        if (values.stream().anyMatch(value -> value == null || value <= 0)
            || Set.copyOf(values).size() != values.size()) {
            throw invalidProfile("Store profile " + label + " sort orders must be positive and unique");
        }
    }

    private void requireUniqueResolvedIds(List<Long> ids, String label) {
        if (ids.stream().anyMatch(Objects::isNull) || Set.copyOf(ids).size() != ids.size()) {
            throw invalidProfile("Source " + label + " selections must resolve uniquely");
        }
    }

    private <T> Map<String, List<T>> groupByNormalized(
        List<T> values,
        Function<T, String> extractor,
        boolean code
    ) {
        return values.stream()
            .filter(value -> extractor.apply(value) != null && !extractor.apply(value).isBlank())
            .collect(Collectors.groupingBy(
                value -> code ? normalizeCode(extractor.apply(value)) : normalizeSku(extractor.apply(value)),
                LinkedHashMap::new,
                Collectors.toList()
            ));
    }

    private <T> T requireUniqueActive(
        List<T> matches,
        Function<T, Boolean> activeExtractor,
        String errorCode,
        String message
    ) {
        if (matches == null || matches.size() != 1 || !Boolean.TRUE.equals(activeExtractor.apply(matches.get(0)))) {
            throw conflict(errorCode, message);
        }
        return matches.get(0);
    }

    private <T> Map<String, T> uniqueByNormalizedCode(List<T> values, Function<T, String> extractor) {
        try {
            return values.stream().collect(Collectors.toMap(
                value -> normalizeCode(extractor.apply(value)),
                Function.identity()
            ));
        } catch (IllegalStateException exception) {
            throw invalidTarget("Target category or station codes are not unique");
        }
    }

    private <T> Map<String, T> uniqueByNormalizedSku(List<T> values, Function<T, String> extractor) {
        try {
            return values.stream().collect(Collectors.toMap(
                value -> normalizeSku(extractor.apply(value)),
                Function.identity()
            ));
        } catch (IllegalStateException exception) {
            throw invalidTarget("Target item SKUs are not unique");
        }
    }

    private void assertFreshId(Long targetId, Long sourceId, String rowType) {
        if (targetId == null || Objects.equals(targetId, sourceId)) {
            throw invalidTarget("Target " + rowType + " did not receive a fresh ID");
        }
    }

    private SourceCategory sourceCategory(MenuCategory value) {
        return new SourceCategory(
            value.id,
            value.code,
            value.name_zh,
            value.name_en,
            value.sort_order,
            value.is_active
        );
    }

    private SourceStation sourceStation(Station value) {
        return new SourceStation(value.id, value.code, value.name, value.sort_order, value.is_active);
    }

    private SourceItem sourceItem(MenuItem value) {
        return new SourceItem(
            value.id,
            value.store_id,
            value.category_id,
            value.station_id,
            value.sku,
            value.name_zh,
            value.name_en,
            value.item_type,
            value.base_price,
            value.cost_per_item,
            value.is_active,
            value.is_sold_out,
            value.sort_order
        );
    }

    private boolean isExactNonBlank(String value) {
        return value != null && !value.isBlank() && value.equals(value.trim());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeCode(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeSku(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private OwnerStoreMenuCloneException invalidProfile(String message) {
        return invalidTarget(message);
    }

    private OwnerStoreMenuCloneException invalidTarget(String message) {
        return new OwnerStoreMenuCloneException(
            "TARGET_MENU_VALIDATION_FAILED",
            HttpStatus.UNPROCESSABLE_ENTITY,
            message
        );
    }

    private OwnerStoreMenuCloneException badRequest(String code, String message) {
        return new OwnerStoreMenuCloneException(code, HttpStatus.BAD_REQUEST, message);
    }

    private OwnerStoreMenuCloneException forbidden(String code, String message) {
        return new OwnerStoreMenuCloneException(code, HttpStatus.FORBIDDEN, message);
    }

    private OwnerStoreMenuCloneException conflict(String code, String message) {
        return new OwnerStoreMenuCloneException(code, HttpStatus.CONFLICT, message);
    }

    private record LockedStores(Store source, Store target) {
    }

    private record ResolvedBaseGraph(
        StoreMenuCloneSnapshot snapshot,
        StoreMenuCloneBaseGraphProfile profile,
        Map<String, SourceCategory> sourceCategoryByTargetCode,
        Map<String, SourceStation> sourceStationByTargetCode,
        Map<String, SourceItem> sourceItemByTargetSku
    ) {
    }

    private record PersistedCategories(
        Map<String, MenuCategory> byTargetCode,
        Map<Long, Long> targetIdBySourceId
    ) {
    }

    private record PersistedStations(
        Map<String, Station> byTargetCode,
        Map<Long, Long> targetIdBySourceId
    ) {
    }

    private record PersistedItems(
        Map<Long, Long> targetIdBySourceId,
        Map<String, Long> targetIdByTargetSku,
        Map<Long, Set<ItemRole>> rolesByTargetItemId
    ) {
    }

    private record OptionKey(Long targetItemId, String optionCode) {
    }
}
