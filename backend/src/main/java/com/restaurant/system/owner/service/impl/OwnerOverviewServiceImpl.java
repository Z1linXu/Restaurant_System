package com.restaurant.system.owner.service.impl;

import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.RequestUserContextService;
import com.restaurant.system.common.auth.StoreAccessService;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
import com.restaurant.system.order.repository.OrderRepository;
import com.restaurant.system.modules.ModuleKeys;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import com.restaurant.system.owner.dto.OwnerOverviewResponse;
import com.restaurant.system.owner.service.OwnerOverviewService;
import com.restaurant.system.platform.entity.Organization;
import com.restaurant.system.platform.repository.OrganizationRepository;
import com.restaurant.system.printing.repository.PrintJobRepository;
import com.restaurant.system.printing.service.PrinterConfigService;
import com.restaurant.system.station.repository.DiningTableRepository;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.StoreOperationalState;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OwnerOverviewServiceImpl implements OwnerOverviewService {

    private final RequestUserContextService requestUserContextService;
    private final StoreAccessService storeAccessService;
    private final OrganizationRepository organizationRepository;
    private final OrderRepository orderRepository;
    private final DiningTableRepository diningTableRepository;
    private final PrintJobRepository printJobRepository;
    private final PrinterConfigService printerConfigService;
    private final FeatureFlagService featureFlagService;
    private final StoreModuleAccessEvaluator moduleAccessEvaluator;

    public OwnerOverviewServiceImpl(
        RequestUserContextService requestUserContextService,
        StoreAccessService storeAccessService,
        OrganizationRepository organizationRepository,
        OrderRepository orderRepository,
        DiningTableRepository diningTableRepository,
        PrintJobRepository printJobRepository,
        PrinterConfigService printerConfigService,
        FeatureFlagService featureFlagService,
        StoreModuleAccessEvaluator moduleAccessEvaluator
    ) {
        this.requestUserContextService = requestUserContextService;
        this.storeAccessService = storeAccessService;
        this.organizationRepository = organizationRepository;
        this.orderRepository = orderRepository;
        this.diningTableRepository = diningTableRepository;
        this.printJobRepository = printJobRepository;
        this.printerConfigService = printerConfigService;
        this.featureFlagService = featureFlagService;
        this.moduleAccessEvaluator = moduleAccessEvaluator;
    }

    @Override
    public OwnerOverviewResponse getOverview() {
        AuthenticatedUser user = requestUserContextService.getRequiredUser();
        LocalDateTime generatedAt = LocalDateTime.now();
        LocalDateTime startAt = generatedAt.toLocalDate().atStartOfDay();
        LocalDateTime endAt = startAt.plusDays(1);

        List<Store> stores = storeAccessService.accessibleStores(user).stream()
            .filter(store -> store != null && store.id != null)
            .sorted(Comparator.comparing(store -> store.id))
            .toList();

        Map<Long, OwnerOverviewResponse.OrganizationOverview> organizationsById = new LinkedHashMap<>();
        for (Organization organization : storeAccessService.accessibleOrganizations(user)) {
            if (organization == null || organization.id == null) {
                continue;
            }
            organizationsById.put(organization.id, buildOrganization(user, organization));
        }
        for (Store store : stores) {
            Long organizationId = store.organization_id == null ? 0L : store.organization_id;
            OwnerOverviewResponse.OrganizationOverview organization = organizationsById.computeIfAbsent(
                organizationId,
                id -> buildOrganization(user, id)
            );
            organization.stores.add(buildStore(user, store, startAt, endAt, generatedAt));
        }

        OwnerOverviewResponse response = new OwnerOverviewResponse();
        response.organizations = new ArrayList<>(organizationsById.values());
        response.generated_at = generatedAt;
        return response;
    }

    private OwnerOverviewResponse.OrganizationOverview buildOrganization(AuthenticatedUser user, Long organizationId) {
        OwnerOverviewResponse.OrganizationOverview response = new OwnerOverviewResponse.OrganizationOverview();
        if (organizationId != null && organizationId > 0) {
            Organization organization = organizationRepository.findById(organizationId).orElse(null);
            if (organization != null) {
                return buildOrganization(user, organization);
            } else {
                response.id = organizationId;
                response.name = "Organization " + organizationId;
                response.status = "unknown";
            }
            response.role_code = storeAccessService.roleCodeForOrganization(user, organizationId);
        } else {
            response.id = null;
            response.name = "Unassigned Organization";
            response.status = "unknown";
            response.role_code = user.roleCode();
        }
        if (response.role_code == null || response.role_code.isBlank()) {
            response.role_code = user.roleCode();
        }
        response.can_create_store = canCreateStore(user, organizationId);
        response.stores = new ArrayList<>();
        return response;
    }

    private OwnerOverviewResponse.OrganizationOverview buildOrganization(AuthenticatedUser user, Organization organization) {
        OwnerOverviewResponse.OrganizationOverview response = new OwnerOverviewResponse.OrganizationOverview();
        response.id = organization.id;
        response.name = organization.name;
        response.code = organization.code;
        response.status = organization.status;
        response.role_code = storeAccessService.roleCodeForOrganization(user, organization.id);
        response.can_create_store = "OWNER".equalsIgnoreCase(user.roleCode())
            && "OWNER".equalsIgnoreCase(response.role_code);
        if (response.role_code == null || response.role_code.isBlank()) {
            response.role_code = user.roleCode();
        }
        response.stores = new ArrayList<>();
        return response;
    }

    private boolean canCreateStore(AuthenticatedUser user, Long organizationId) {
        return user != null
            && "OWNER".equalsIgnoreCase(user.roleCode())
            && "OWNER".equalsIgnoreCase(storeAccessService.roleCodeForOrganization(user, organizationId));
    }

    private OwnerOverviewResponse.StoreOverview buildStore(
        AuthenticatedUser user,
        Store store,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime generatedAt
    ) {
        OwnerOverviewResponse.StoreOverview response = new OwnerOverviewResponse.StoreOverview();
        response.id = store.id;
        response.name = store.name;
        response.code = store.code;
        response.status = store.status;
        response.store_kind = store.store_kind;
        response.lifecycle_status = store.lifecycle_status;
        response.operational_state = StoreOperationalState.value(store);
        response.is_live = StoreOperationalState.isLive(store);
        response.provisioning_source = store.provisioning_source;
        response.provisioned_profile_code = store.provisioned_profile_code;
        response.provisioned_profile_version = store.provisioned_profile_version;
        response.provisioned_master_menu_key = store.provisioned_master_menu_key;
        response.provisioned_master_menu_version = store.provisioned_master_menu_version;
        response.role_code = storeAccessService.roleCodeForStore(user, store);
        response.features = featureMap(store.id);
        response.summary = buildSummary(store, startAt, endAt, generatedAt);
        return response;
    }

    private OwnerOverviewResponse.StoreSummary buildSummary(
        Store store,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime generatedAt
    ) {
        Long storeId = store.id;
        long activeTables = diningTableRepository.countActiveByStoreId(storeId);
        long occupiedTables = orderRepository.countOccupiedDineInTablesByStoreId(storeId);
        long boundedOccupiedTables = Math.min(activeTables, occupiedTables);

        OwnerOverviewResponse.StoreSummary summary = new OwnerOverviewResponse.StoreSummary();
        summary.today_orders = orderRepository.countTodayByStoreId(storeId, startAt, endAt);
        summary.today_sales = safeMoney(orderRepository.sumCompletedTotalByStoreIdAndCompletedAtBetween(storeId, startAt, endAt));
        summary.active_orders = orderRepository.countActiveByStoreId(storeId);
        summary.occupied_tables = boundedOccupiedTables;
        summary.open_tables = Math.max(0, activeTables - boundedOccupiedTables);
        summary.failed_print_jobs = printJobRepository.countFailedByStoreIdAndCreatedAtBetween(storeId, startAt, endAt);
        summary.printing_mode = printerConfigService.getStorePrintingMode(storeId);
        summary.last_failed_print_at = printJobRepository.findLastFailedAtByStoreId(storeId);
        summary.kds_active_count = featureFlagService.isEnabled(FeaturePackage.KDS) ? summary.active_orders : null;
        summary.last_updated_at = generatedAt;
        return summary;
    }

    private Map<String, Boolean> featureMap(Long storeId) {
        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put("core_pos", featureFlagService.isEnabled(FeaturePackage.CORE_POS));
        features.put("printing", featureFlagService.isEnabled(FeaturePackage.PRINTING)
            && moduleAccessEvaluator.isModuleEnabled(storeId, ModuleKeys.PRINTING));
        features.put("kds", featureFlagService.isEnabled(FeaturePackage.KDS));
        features.put("admin", featureFlagService.isEnabled(FeaturePackage.ADMIN));
        features.put("analytics", featureFlagService.isEnabled(FeaturePackage.ANALYTICS));
        return features;
    }

    private BigDecimal safeMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
