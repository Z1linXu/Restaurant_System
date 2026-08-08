package com.restaurant.system.staging.menu;

import com.restaurant.system.menu.entity.MenuCategory;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.staging.bootstrap.entity.StagingSyntheticBootstrapRequest;
import com.restaurant.system.staging.bootstrap.repository.StagingSyntheticBootstrapRequestRepository;
import com.restaurant.system.station.entity.Station;
import com.restaurant.system.station.repository.StationRepository;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("staging-synthetic-bootstrap")
public class StagingSyntheticSourceMenuServiceImpl implements StagingSyntheticSourceMenuService {

    private static final String RESULT_READY_TO_CREATE = "STG005_SOURCE_MENU_READY_TO_CREATE";
    private static final String RESULT_CREATED = "STG005_SOURCE_MENU_READY";
    private static final String RESULT_REPLAYED = "STG005_SOURCE_MENU_REPLAYED";
    private static final String BOOTSTRAP_COMPLETED = "COMPLETED";
    private static final String BOOTSTRAP_READY = "STG005_SYNTHETIC_BOOTSTRAP_READY";

    private final StagingSyntheticSourceMenuGuard guard;
    private final StagingSyntheticSourceMenuManifestFactory manifestFactory;
    private final StagingSyntheticSourceMenuPlanner planner;
    private final StoreRepository storeRepository;
    private final StagingSyntheticBootstrapRequestRepository bootstrapRequestRepository;
    private final MenuCategoryRepository categoryRepository;
    private final StationRepository stationRepository;
    private final MenuItemRepository itemRepository;
    private final MenuItemOptionRepository optionRepository;
    private final MenuRevisionService menuRevisionService;
    private final StagingSyntheticSourceMenuGraphWriter graphWriter;
    private final StagingSyntheticSourceMenuCommitHook commitHook;

    public StagingSyntheticSourceMenuServiceImpl(
        StagingSyntheticSourceMenuGuard guard,
        StagingSyntheticSourceMenuManifestFactory manifestFactory,
        StagingSyntheticSourceMenuPlanner planner,
        StoreRepository storeRepository,
        StagingSyntheticBootstrapRequestRepository bootstrapRequestRepository,
        MenuCategoryRepository categoryRepository,
        StationRepository stationRepository,
        MenuItemRepository itemRepository,
        MenuItemOptionRepository optionRepository,
        MenuRevisionService menuRevisionService,
        StagingSyntheticSourceMenuGraphWriter graphWriter,
        StagingSyntheticSourceMenuCommitHook commitHook
    ) {
        this.guard = guard;
        this.manifestFactory = manifestFactory;
        this.planner = planner;
        this.storeRepository = storeRepository;
        this.bootstrapRequestRepository = bootstrapRequestRepository;
        this.categoryRepository = categoryRepository;
        this.stationRepository = stationRepository;
        this.itemRepository = itemRepository;
        this.optionRepository = optionRepository;
        this.menuRevisionService = menuRevisionService;
        this.graphWriter = graphWriter;
        this.commitHook = commitHook;
    }

    @Override
    @Transactional(readOnly = true)
    public StagingSyntheticSourceMenuResult plan(StagingSyntheticSourceMenuSpec spec) {
        guard.validateSpec(spec);
        StagingSyntheticSourceMenuPlan expected = expectedPlan();
        Store store = requireSourceStore(spec, storeRepository.findById(spec.sourceStoreId()).orElse(null));
        StagingSyntheticBootstrapRequest bootstrapRequest = requireCompletedBootstrap(spec, store);
        ExistingGraph graph = readGraph(store.id);
        if (graph.empty()) {
            return result(bootstrapRequest, store.id, expected, store.menu_revision, store.menu_revision,
                RESULT_READY_TO_CREATE, false);
        }
        requireExactGraph(expected, graph);
        return result(bootstrapRequest, store.id, expected, store.menu_revision, store.menu_revision,
            RESULT_REPLAYED, true);
    }

    @Override
    @Transactional
    public StagingSyntheticSourceMenuResult apply(StagingSyntheticSourceMenuSpec spec) {
        guard.validateSpec(spec);
        StagingSyntheticSourceMenuManifest manifest = manifestFactory.create();
        StagingSyntheticSourceMenuPlan expected = planner.plan(manifest);
        Store store = requireSourceStore(
            spec,
            menuRevisionService.lockStoresInOrder(List.of(spec.sourceStoreId())).get(0)
        );
        StagingSyntheticBootstrapRequest bootstrapRequest = requireCompletedBootstrap(spec, store);
        Long revisionBefore = store.menu_revision;
        ExistingGraph graph = readGraph(store.id);
        if (!graph.empty()) {
            requireExactGraph(expected, graph);
            return result(bootstrapRequest, store.id, expected, revisionBefore, revisionBefore,
                RESULT_REPLAYED, true);
        }

        graphWriter.persist(store.id, manifest);
        commitHook.beforeRevisionIncrement();
        menuRevisionService.incrementRevision(store.id);
        Long revisionAfter = storeRepository.findMenuRevisionById(store.id);
        requireExactGraph(expected, readGraph(store.id));
        return result(bootstrapRequest, store.id, expected, revisionBefore, revisionAfter, RESULT_CREATED, false);
    }

    private StagingSyntheticSourceMenuPlan expectedPlan() {
        return planner.plan(manifestFactory.create());
    }

    private Store requireSourceStore(StagingSyntheticSourceMenuSpec spec, Store store) {
        if (store == null
            || !Objects.equals(store.id, spec.sourceStoreId())
            || !Objects.equals(store.code, spec.sourceStoreCode())
            || !"active".equalsIgnoreCase(store.status)
            || store.organization_id == null
            || !Boolean.FALSE.equals(store.enable_bar_kitchen_tasks)
            || !Boolean.FALSE.equals(store.printing_enabled)
            || !"DISABLED".equalsIgnoreCase(store.printing_mode)) {
            throw invalid(
                "STG005_SOURCE_MENU_STORE_STATE_REJECTED",
                "Synthetic source Store identity or safety state does not match the reviewed request"
            );
        }
        return store;
    }

    private StagingSyntheticBootstrapRequest requireCompletedBootstrap(
        StagingSyntheticSourceMenuSpec spec,
        Store store
    ) {
        List<StagingSyntheticBootstrapRequest> requests = bootstrapRequestRepository
            .findAllBySourceStoreIdAndStatusOrderByIdAsc(store.id, BOOTSTRAP_COMPLETED);
        if (requests.size() != 1) {
            throw bootstrapUnavailable();
        }
        StagingSyntheticBootstrapRequest request = requests.get(0);
        if (request.id == null
            || request.organizationId == null
            || request.ownerUserId == null
            || !Objects.equals(request.sourceStoreId, store.id)
            || !Objects.equals(request.organizationId, store.organization_id)
            || !Objects.equals(request.runtimeSha, spec.runtimeSha())
            || !Objects.equals(request.toolSha, spec.toolSha())
            || !BOOTSTRAP_READY.equals(request.resultCode)
            || request.completedAt == null) {
            throw bootstrapUnavailable();
        }
        return request;
    }

    private ExistingGraph readGraph(Long storeId) {
        List<MenuCategory> categories = categoryRepository.findAllByStoreIdOrderByIdAsc(storeId);
        List<Station> stations = stationRepository.findAllByStoreIdOrderByIdAsc(storeId);
        List<MenuItem> items = itemRepository.findAllByStoreIdOrderByIdAsc(storeId);
        List<MenuItemOption> options = items.isEmpty()
            ? List.of()
            : optionRepository.findAllByStoreIdAndMenuItemIdsOrdered(
                storeId,
                items.stream().map(item -> item.id).toList()
            );
        return new ExistingGraph(categories, stations, items, options);
    }

    private void requireExactGraph(StagingSyntheticSourceMenuPlan expected, ExistingGraph graph) {
        try {
            StagingSyntheticSourceMenuPlan actual = planner.plan(toManifest(graph));
            if (!Objects.equals(expected.fingerprint(), actual.fingerprint())
                || expected.categoryCount() != actual.categoryCount()
                || expected.stationCount() != actual.stationCount()
                || expected.itemCount() != actual.itemCount()
                || expected.optionCount() != actual.optionCount()) {
                throw graphConflict();
            }
        } catch (StagingSyntheticSourceMenuManifestValidator.ValidationException exception) {
            throw graphConflict();
        }
    }

    private StagingSyntheticSourceMenuManifest toManifest(ExistingGraph graph) {
        Map<Long, String> categoryCodes = uniqueIdMap(graph.categories(), category -> category.id, category -> category.code);
        Map<Long, String> stationCodes = uniqueIdMap(graph.stations(), station -> station.id, station -> station.code);
        Map<Long, String> itemSkus = uniqueIdMap(graph.items(), item -> item.id, item -> item.sku);
        Map<Long, MenuItemOption> optionsById = uniqueEntityMap(graph.options(), option -> option.id);

        List<StagingSyntheticSourceMenuManifest.Category> categories = graph.categories().stream()
            .map(category -> new StagingSyntheticSourceMenuManifest.Category(
                category.code,
                category.name_zh,
                category.name_en,
                Boolean.TRUE.equals(category.is_active),
                requiredInt(category.sort_order)
            ))
            .toList();
        List<StagingSyntheticSourceMenuManifest.Station> stations = graph.stations().stream()
            .map(station -> new StagingSyntheticSourceMenuManifest.Station(
                station.code,
                station.name,
                Boolean.TRUE.equals(station.is_active),
                requiredInt(station.sort_order)
            ))
            .toList();
        List<StagingSyntheticSourceMenuManifest.Item> items = graph.items().stream()
            .map(item -> new StagingSyntheticSourceMenuManifest.Item(
                item.sku,
                categoryCodes.get(item.category_id),
                stationCodes.get(item.station_id),
                item.item_type,
                item.name_zh,
                item.name_en,
                item.base_price,
                item.cost_per_item,
                Boolean.TRUE.equals(item.is_active),
                Boolean.TRUE.equals(item.is_sold_out),
                requiredInt(item.sort_order)
            ))
            .toList();
        List<StagingSyntheticSourceMenuManifest.Option> options = graph.options().stream()
            .map(option -> {
                MenuItemOption parent = option.parent_option_id == null
                    ? null
                    : optionsById.get(option.parent_option_id);
                if (option.parent_option_id != null && parent == null) {
                    throw graphConflict();
                }
                if (parent != null && !Objects.equals(parent.menu_item_id, option.menu_item_id)) {
                    throw graphConflict();
                }
                return new StagingSyntheticSourceMenuManifest.Option(
                    itemSkus.get(option.menu_item_id),
                    option.option_type,
                    option.option_group,
                    option.option_code,
                    parent == null ? null : parent.option_code,
                    option.name_zh,
                    option.name_en,
                    option.price_delta,
                    Boolean.TRUE.equals(option.is_active),
                    requiredInt(option.sort_order)
                );
            })
            .toList();
        return new StagingSyntheticSourceMenuManifest(
            StagingSyntheticSourceMenuManifestFactory.MANIFEST_CODE,
            StagingSyntheticSourceMenuManifestFactory.MANIFEST_VERSION,
            StagingSyntheticSourceMenuManifestFactory.TOPOLOGY_NAMESPACE,
            categories,
            stations,
            items,
            options
        );
    }

    private int requiredInt(Integer value) {
        return value == null ? Integer.MIN_VALUE : value;
    }

    private <T> Map<Long, String> uniqueIdMap(
        List<T> values,
        java.util.function.Function<T, Long> idReader,
        java.util.function.Function<T, String> valueReader
    ) {
        Map<Long, String> result = new LinkedHashMap<>();
        for (T value : values) {
            Long id = idReader.apply(value);
            if (id == null || result.putIfAbsent(id, valueReader.apply(value)) != null) {
                throw graphConflict();
            }
        }
        return result;
    }

    private <T> Map<Long, T> uniqueEntityMap(
        List<T> values,
        java.util.function.Function<T, Long> idReader
    ) {
        Map<Long, T> result = new LinkedHashMap<>();
        for (T value : values) {
            Long id = idReader.apply(value);
            if (id == null || result.putIfAbsent(id, value) != null) {
                throw graphConflict();
            }
        }
        return result;
    }

    private StagingSyntheticSourceMenuResult result(
        StagingSyntheticBootstrapRequest bootstrapRequest,
        Long sourceStoreId,
        StagingSyntheticSourceMenuPlan plan,
        Long revisionBefore,
        Long revisionAfter,
        String resultCode,
        boolean replayed
    ) {
        return new StagingSyntheticSourceMenuResult(
            bootstrapRequest.id,
            sourceStoreId,
            bootstrapRequest.runtimeSha,
            bootstrapRequest.toolSha,
            plan.manifestCode(),
            plan.manifestVersion(),
            plan.fingerprint(),
            revisionBefore,
            revisionAfter,
            plan.categoryCount(),
            plan.stationCount(),
            plan.itemCount(),
            plan.optionCount(),
            resultCode,
            replayed
        );
    }

    private StagingSyntheticSourceMenuException graphConflict() {
        return invalid(
            "STG005_SOURCE_MENU_GRAPH_CONFLICT",
            "Synthetic source Store menu is partial, extra, or different from the reviewed manifest"
        );
    }

    private StagingSyntheticSourceMenuException bootstrapUnavailable() {
        return invalid(
            "STG005_SOURCE_MENU_BOOTSTRAP_UNAVAILABLE",
            "Synthetic source Store does not have one matching completed STG-005A bootstrap record"
        );
    }

    private StagingSyntheticSourceMenuException invalid(String errorCode, String message) {
        return new StagingSyntheticSourceMenuException(errorCode, message);
    }

    private record ExistingGraph(
        List<MenuCategory> categories,
        List<Station> stations,
        List<MenuItem> items,
        List<MenuItemOption> options
    ) {
        private boolean empty() {
            return categories.isEmpty() && stations.isEmpty() && items.isEmpty() && options.isEmpty();
        }
    }
}
