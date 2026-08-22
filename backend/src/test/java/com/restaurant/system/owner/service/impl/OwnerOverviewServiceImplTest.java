package com.restaurant.system.owner.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.RequestUserContextService;
import com.restaurant.system.common.auth.StoreAccessService;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.order.repository.OrderRepository;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import com.restaurant.system.owner.dto.OwnerOverviewResponse;
import com.restaurant.system.platform.entity.Organization;
import com.restaurant.system.platform.repository.OrganizationRepository;
import com.restaurant.system.printing.repository.PrintJobRepository;
import com.restaurant.system.printing.service.PrinterConfigService;
import com.restaurant.system.station.repository.DiningTableRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OwnerOverviewServiceImplTest {

    @Mock private RequestUserContextService requestUserContextService;
    @Mock private StoreAccessService storeAccessService;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private DiningTableRepository diningTableRepository;
    @Mock private PrintJobRepository printJobRepository;
    @Mock private PrinterConfigService printerConfigService;
    @Mock private FeatureFlagService featureFlagService;
    @Mock private StoreModuleAccessEvaluator moduleAccessEvaluator;

    private OwnerOverviewServiceImpl service;
    private AuthenticatedUser owner;

    @BeforeEach
    void setUp() {
        service = new OwnerOverviewServiceImpl(
            requestUserContextService,
            storeAccessService,
            organizationRepository,
            orderRepository,
            diningTableRepository,
            printJobRepository,
            printerConfigService,
            featureFlagService,
            moduleAccessEvaluator
        );
        owner = new AuthenticatedUser(20L, null, 1L, "owner", "Owner", "OWNER");
    }

    @Test
    void ownerOverviewIncludesOrganizationMembershipWithoutExistingStores() {
        Organization organization = new Organization();
        organization.id = 100L;
        organization.name = "Lanzhou Group";
        organization.code = "LANZHOU_GROUP";
        organization.status = "active";

        when(requestUserContextService.getRequiredUser()).thenReturn(owner);
        when(storeAccessService.accessibleOrganizations(owner)).thenReturn(List.of(organization));
        when(storeAccessService.accessibleStores(owner)).thenReturn(List.of());
        when(storeAccessService.roleCodeForOrganization(owner, 100L)).thenReturn("OWNER");

        OwnerOverviewResponse response = service.getOverview();

        assertThat(response.organizations).hasSize(1);
        assertThat(response.organizations.get(0).id).isEqualTo(100L);
        assertThat(response.organizations.get(0).role_code).isEqualTo("OWNER");
        assertThat(response.organizations.get(0).stores).isEmpty();
    }
}
