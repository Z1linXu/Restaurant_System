package com.restaurant.system.common.config;

import static org.mockito.Mockito.verifyNoInteractions;

import com.restaurant.system.auth.repository.UserCredentialRepository;
import com.restaurant.system.auth.service.PasswordService;
import com.restaurant.system.dev.DevRoleSwitcherAccess;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.platform.repository.OrganizationRepository;
import com.restaurant.system.platform.repository.RestaurantTemplateRepository;
import com.restaurant.system.platform.repository.StoreKdsDisplayConfigRepository;
import com.restaurant.system.printing.repository.PrinterAssignmentRepository;
import com.restaurant.system.printing.repository.PrinterConfigRepository;
import com.restaurant.system.station.repository.DiningTableRepository;
import com.restaurant.system.station.repository.StationRepository;
import com.restaurant.system.user.repository.OrganizationMembershipRepository;
import com.restaurant.system.user.repository.RoleRepository;
import com.restaurant.system.user.repository.StoreMembershipRepository;
import com.restaurant.system.user.repository.StoreRepository;
import com.restaurant.system.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RuntimeDataSeederPolicyTest {

    @Mock
    private StoreRepository storeRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrganizationMembershipRepository organizationMembershipRepository;
    @Mock
    private StoreMembershipRepository storeMembershipRepository;
    @Mock
    private StationRepository stationRepository;
    @Mock
    private DiningTableRepository diningTableRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private RestaurantTemplateRepository restaurantTemplateRepository;
    @Mock
    private StoreKdsDisplayConfigRepository storeKdsDisplayConfigRepository;
    @Mock
    private MenuCategoryRepository menuCategoryRepository;
    @Mock
    private MenuItemRepository menuItemRepository;
    @Mock
    private MenuItemOptionRepository menuItemOptionRepository;
    @Mock
    private PrinterConfigRepository printerConfigRepository;
    @Mock
    private PrinterAssignmentRepository printerAssignmentRepository;
    @Mock
    private UserCredentialRepository userCredentialRepository;
    @Mock
    private PasswordService passwordService;
    @Mock
    private DevRoleSwitcherAccess devRoleSwitcherAccess;

    @Test
    void doesNotSeedDefaultCredentialsWhenDefaultUsersDisabled() {
        RuntimeDataSeeder seeder = seeder(false, false, false, false, false);

        seeder.run(null);

        verifyNoInteractions(userRepository, userCredentialRepository, passwordService, devRoleSwitcherAccess);
    }

    @Test
    void doesNotSeedDemoDataWhenDemoDataDisabled() {
        RuntimeDataSeeder seeder = seeder(false, false, false, false, false);

        seeder.run(null);

        verifyNoInteractions(
            storeRepository,
            stationRepository,
            menuCategoryRepository,
            menuItemRepository,
            menuItemOptionRepository,
            diningTableRepository,
            printerConfigRepository,
            printerAssignmentRepository,
            organizationRepository,
            restaurantTemplateRepository,
            storeKdsDisplayConfigRepository
        );
    }

    private RuntimeDataSeeder seeder(
        boolean safeMetadataEnabled,
        boolean defaultUsersEnabled,
        boolean demoDataEnabled,
        boolean membershipSupplementEnabled,
        boolean productionBootstrapEnabled
    ) {
        return new RuntimeDataSeeder(
            storeRepository,
            roleRepository,
            userRepository,
            organizationMembershipRepository,
            storeMembershipRepository,
            stationRepository,
            diningTableRepository,
            organizationRepository,
            restaurantTemplateRepository,
            storeKdsDisplayConfigRepository,
            menuCategoryRepository,
            menuItemRepository,
            menuItemOptionRepository,
            printerConfigRepository,
            printerAssignmentRepository,
            userCredentialRepository,
            passwordService,
            devRoleSwitcherAccess,
            true,
            false,
            safeMetadataEnabled,
            defaultUsersEnabled,
            demoDataEnabled,
            membershipSupplementEnabled,
            productionBootstrapEnabled
        );
    }
}
