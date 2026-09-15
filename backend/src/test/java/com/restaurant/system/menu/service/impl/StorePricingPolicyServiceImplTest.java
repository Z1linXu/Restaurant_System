package com.restaurant.system.menu.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.restaurant.system.menu.dto.MenuItemSizeConfigurationRequest;
import com.restaurant.system.menu.dto.MenuItemComboPolicyRequest;
import com.restaurant.system.menu.dto.StorePricingPolicyUpdateRequest;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.pricing.StorePricingPolicy;
import com.restaurant.system.menu.pricing.StorePricingPolicyRepository;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.user.repository.StoreRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StorePricingPolicyServiceImplTest {

    @Mock
    private StorePricingPolicyRepository storePricingPolicyRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private MenuItemRepository menuItemRepository;
    @Mock
    private MenuItemOptionRepository menuItemOptionRepository;
    @Mock
    private MenuRevisionService menuRevisionService;

    private StorePricingPolicyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StorePricingPolicyServiceImpl(
            storePricingPolicyRepository,
            storeRepository,
            menuItemRepository,
            menuItemOptionRepository,
            menuRevisionService
        );
    }

    @Test
    void centralAddonRetainsCatalogPriceAndIdentityDespiteLegacyComboOrSizeLabels() {
        for (MenuItemOption addon : List.of(
            option(81L, "addon", "combo", "ADD_ON", "套餐", "Combo", true, 10, new BigDecimal("9.50")),
            option(82L, "addon", "custom_bundle", "ADD_ON", "套餐", "Combo", true, 10, new BigDecimal("9.50")),
            option(83L, "size", "size_large", "SIZE", "大碗", "Large", true, 10, new BigDecimal("9.50"))
        )) {
            addon.store_addon_id = 200L;

            MenuItemOption projected = service.applyEffectiveCatalogPricing(addon, policy(3L));

            assertSame(addon, projected);
            assertEquals(new BigDecimal("9.50"), projected.price_delta);
            assertEquals(200L, projected.store_addon_id);
        }
    }

    @Test
    void explicitGroupsPreventLegacyCodeNameAndTypeFromReclassifyingUnresolvedOptions() {
        for (MenuItemOption option : List.of(
            option(81L, "addon", "combo", "ADD_ON", "套餐", "Combo", true, 10, new BigDecimal("9.50")),
            option(82L, "addon", "custom_bundle", " add_on ", "套餐", "Combo", true, 10, new BigDecimal("9.50")),
            option(83L, "size", "size_large", "ADD_ON", "大碗", "Large", true, 10, new BigDecimal("9.50")),
            option(84L, "size", "size_large", "REMOVE", "大碗", "Large", true, 10, new BigDecimal("9.50")),
            option(85L, "addon", "combo", "COMBO_EGG", "套餐", "Combo", true, 10, new BigDecimal("9.50"))
        )) {
            assertSame(option, service.applyEffectiveCatalogPricing(option, policy(3L)));
            assertEquals(new BigDecimal("9.50"), option.price_delta);
        }
    }

    @Test
    void explicitComboAndSizeGroupsOverrideContradictingLegacyLabelsAndTypes() {
        MenuItemOption combo = option(81L, "size", "size_large", "COMBO", "大碗", "Large", true, 10,
            new BigDecimal("9.50"));
        MenuItemOption size = option(82L, "addon", "combo", "SIZE", "套餐", "Combo", true, 10,
            new BigDecimal("9.50"));

        assertEquals(new BigDecimal("5.00"), service.applyEffectiveCatalogPricing(combo, policy(3L)).price_delta);
        assertSame(size, service.applyEffectiveCatalogPricing(size, policy(3L)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void comboPolicyCreatesItsOwnRowWithoutTakingOverExplicitAddon(boolean linked) {
        MenuItem item = new MenuItem();
        item.id = 14L;
        item.store_id = 3L;
        MenuItemOption addon = option(81L, "addon", "combo", "ADD_ON", "套餐", "Combo", true, 10,
            new BigDecimal("9.50"));
        addon.store_addon_id = linked ? 200L : null;
        List<MenuItemOption> stored = new ArrayList<>(List.of(addon));
        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(item));
        when(storeRepository.existsById(3L)).thenReturn(true);
        when(storePricingPolicyRepository.findByStoreId(3L)).thenReturn(Optional.of(policy(3L)));
        when(menuItemOptionRepository.findAllByMenuItemIdOrdered(14L)).thenAnswer(invocation -> List.copyOf(stored));
        when(menuItemRepository.updateItemComboEggDefault(eq(14L), eq(3L), isNull(), any())).thenReturn(1);
        when(menuItemOptionRepository.save(any())).thenAnswer(invocation -> {
            MenuItemOption saved = invocation.getArgument(0);
            if (saved.id == null) {
                saved.id = 82L;
                stored.add(saved);
            }
            return saved;
        });
        MenuItemComboPolicyRequest request = new MenuItemComboPolicyRequest();
        request.combo_allowed = true;

        service.updateComboPolicy(14L, request);
        request.combo_allowed = false;
        service.updateComboPolicy(14L, request);

        assertEquals(2, stored.size());
        assertEquals("ADD_ON", addon.option_group);
        assertEquals("combo", addon.option_code);
        assertEquals(new BigDecimal("9.50"), addon.price_delta);
        assertTrue(addon.is_active);
        assertEquals(linked ? 200L : null, addon.store_addon_id);
        assertEquals("COMBO", stored.get(1).option_group);
        assertFalse(stored.get(1).is_active);
        verify(menuItemOptionRepository, never()).save(addon);
    }

    @Test
    void unresolvedLegacyComboStillUsesEstablishedFallbackPricing() {
        MenuItemOption legacy = option(81L, "addon", null, null, "套餐", "Combo", true, 10,
            new BigDecimal("9.50"));

        MenuItemOption projected = service.applyEffectiveCatalogPricing(legacy, policy(3L));

        assertEquals("COMBO", projected.option_group);
        assertEquals("combo", projected.option_code);
        assertEquals(new BigDecimal("5.00"), projected.price_delta);
        assertEquals(new BigDecimal("9.50"), legacy.price_delta);
    }

    @Test
    void disablingComboClearsThePersistedItemEggOverrideUnderStoreLock() {
        MenuItem item = new MenuItem();
        item.id = 14L;
        item.store_id = 3L;
        // The controller may have read a null value before another writer acquired the lock.
        item.default_combo_egg_component_code = null;
        MenuItemOption combo = option(80L, "addon", "combo", "COMBO", "套餐", "Combo", true, 10,
            new BigDecimal("5.00"));
        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(item));
        when(storeRepository.existsById(3L)).thenReturn(true);
        when(storePricingPolicyRepository.findByStoreId(3L)).thenReturn(Optional.of(policy(3L)));
        when(menuItemOptionRepository.findAllByMenuItemIdOrdered(14L)).thenReturn(List.of(combo));
        when(menuItemOptionRepository.save(combo)).thenReturn(combo);
        when(menuItemRepository.updateItemComboEggDefault(eq(14L), eq(3L), isNull(), any())).thenReturn(1);
        MenuItemComboPolicyRequest request = new MenuItemComboPolicyRequest();
        request.combo_allowed = false;

        service.updateComboPolicy(14L, request);

        assertFalse(combo.is_active);
        verify(menuRevisionService).lockStoresInOrder(List.of(3L));
        verify(menuItemRepository).updateItemComboEggDefault(eq(14L), eq(3L), isNull(), any());
        verify(menuRevisionService).incrementRevision(3L);
    }

    @Test
    void updatingPricingPolicyMirrorsCompatibilityBridgeAndBumpsMenuRevision() {
        StorePricingPolicy policy = policy(3L);
        when(storeRepository.existsById(3L)).thenReturn(true);
        when(storePricingPolicyRepository.findByStoreId(3L)).thenReturn(Optional.of(policy));
        when(storePricingPolicyRepository.save(any(StorePricingPolicy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StorePricingPolicyUpdateRequest request = new StorePricingPolicyUpdateRequest();
        request.store_id = 3L;
        request.size_small_delta = new BigDecimal("-1.50");
        request.size_regular_delta = BigDecimal.ZERO;
        request.size_large_delta = new BigDecimal("3.25");
        request.combo_delta = new BigDecimal("6.00");

        var response = service.updatePolicy(3L, request);

        assertEquals(new BigDecimal("3.25").setScale(2), response.size_large_delta);
        assertEquals(new BigDecimal("6.00").setScale(2), response.combo_delta);
        assertEquals(8L, response.policy_revision);
        verify(menuRevisionService).lockStoresInOrder(List.of(3L));
        verify(storePricingPolicyRepository).mirrorPolicyToSizeAndComboOptions(3L);
        verify(menuRevisionService).incrementRevision(3L);
    }

    @Test
    void sizeConfigurationCreatesCanonicalRowsAndDeactivatesLegacyRows() {
        MenuItem item = new MenuItem();
        item.id = 14L;
        item.store_id = 3L;
        List<MenuItemOption> stored = new ArrayList<>();
        stored.add(option(81L, "size", "legacy_size", "SIZE", "迷你碗", "Mini", true, 5, BigDecimal.ZERO));
        stored.add(option(82L, "size", "size_large", "SIZE", "大碗", "Large", true, 20, new BigDecimal("2.00")));
        MenuItemOption unresolvedAddon = option(83L, "size", "extra_large", "ADD_ON", "大碗", "Large", true, 30,
            new BigDecimal("9.50"));
        stored.add(unresolvedAddon);

        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(item));
        when(storeRepository.existsById(3L)).thenReturn(true);
        when(storePricingPolicyRepository.findByStoreId(3L)).thenReturn(Optional.of(policy(3L)));
        when(menuItemOptionRepository.findAllByMenuItemIdOrdered(14L)).thenAnswer(invocation -> stored.stream()
            .sorted(Comparator.comparing((MenuItemOption option) -> option.sort_order == null ? Integer.MAX_VALUE : option.sort_order)
                .thenComparing(option -> option.id == null ? Long.MAX_VALUE : option.id))
            .toList());
        AtomicLong ids = new AtomicLong(90);
        when(menuItemOptionRepository.save(any(MenuItemOption.class))).thenAnswer(invocation -> {
            MenuItemOption option = invocation.getArgument(0);
            if (option.id == null) {
                option.id = ids.getAndIncrement();
            }
            stored.removeIf(candidate -> option.id.equals(candidate.id));
            stored.add(option);
            return option;
        });

        MenuItemSizeConfigurationRequest request = new MenuItemSizeConfigurationRequest();
        request.enabled_size_codes = List.of("size_small", "size_large");
        request.default_size_code = "size_large";

        service.updateSizeConfiguration(14L, request);

        MenuItemOption small = findByCode(stored, "size_small");
        MenuItemOption regular = findByCode(stored, "size_regular");
        MenuItemOption large = findByCode(stored, "size_large");
        MenuItemOption legacy = findByCode(stored, "legacy_size");

        assertTrue(small.is_active);
        assertEquals(new BigDecimal("-2.00"), small.price_delta);
        assertFalse(regular.is_active);
        assertEquals(BigDecimal.ZERO.setScale(2), regular.price_delta);
        assertTrue(large.is_active);
        assertEquals(10, large.sort_order);
        assertEquals(new BigDecimal("2.00"), large.price_delta);
        assertFalse(legacy.is_active);
        assertEquals("ADD_ON", unresolvedAddon.option_group);
        assertEquals("size", unresolvedAddon.option_type);
        assertEquals(new BigDecimal("9.50"), unresolvedAddon.price_delta);
        assertTrue(unresolvedAddon.is_active);
        verify(menuItemOptionRepository, never()).save(unresolvedAddon);
        verify(menuRevisionService).lockStoresInOrder(List.of(3L));
        verify(storePricingPolicyRepository).mirrorPolicyToSizeAndComboOptions(3L);
        verify(menuRevisionService).incrementRevision(3L);
    }

    @Test
    void sizeConfigurationPrefersExistingCanonicalIdentityOverEarlierLegacyRow() {
        MenuItem item = new MenuItem();
        item.id = 14L;
        item.store_id = 3L;
        List<MenuItemOption> stored = new ArrayList<>();
        stored.add(option(81L, "size", null, null, "标准份", "Regular", false, 5, BigDecimal.ZERO));
        stored.add(option(82L, "size", "size_regular", "SIZE", "中碗", "Regular", true, 70, BigDecimal.ZERO));
        stored.add(option(83L, "size", "size_large", "SIZE", "大碗", "Large", true, 80, new BigDecimal("2.00")));

        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(item));
        when(storeRepository.existsById(3L)).thenReturn(true);
        when(storePricingPolicyRepository.findByStoreId(3L)).thenReturn(Optional.of(policy(3L)));
        when(menuItemOptionRepository.findAllByMenuItemIdOrdered(14L)).thenAnswer(invocation -> stored.stream()
            .sorted(Comparator.comparing((MenuItemOption option) -> option.sort_order == null ? Integer.MAX_VALUE : option.sort_order)
                .thenComparing(option -> option.id))
            .toList());
        when(menuItemOptionRepository.save(any(MenuItemOption.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MenuItemSizeConfigurationRequest request = new MenuItemSizeConfigurationRequest();
        request.enabled_size_codes = List.of("size_regular", "size_large");

        service.updateSizeConfiguration(14L, request);

        assertEquals("size_regular", stored.stream().filter(option -> option.id.equals(82L)).findFirst().orElseThrow().option_code);
        assertTrue(stored.stream().filter(option -> option.id.equals(82L)).findFirst().orElseThrow().is_active);
        assertFalse(stored.stream().filter(option -> option.id.equals(81L)).findFirst().orElseThrow().is_active);
        verify(menuRevisionService).incrementRevision(3L);
    }

    private StorePricingPolicy policy(Long storeId) {
        StorePricingPolicy policy = new StorePricingPolicy();
        policy.id = 7L;
        policy.store_id = storeId;
        policy.size_small_delta = new BigDecimal("-2.00");
        policy.size_regular_delta = BigDecimal.ZERO.setScale(2);
        policy.size_large_delta = new BigDecimal("2.00");
        policy.combo_delta = new BigDecimal("5.00");
        policy.policy_revision = 7L;
        return policy;
    }

    private MenuItemOption option(
        Long id,
        String optionType,
        String optionCode,
        String optionGroup,
        String nameZh,
        String nameEn,
        boolean active,
        Integer sortOrder,
        BigDecimal priceDelta
    ) {
        MenuItemOption option = new MenuItemOption();
        option.id = id;
        option.menu_item_id = 14L;
        option.option_type = optionType;
        option.option_code = optionCode;
        option.option_group = optionGroup;
        option.name_zh = nameZh;
        option.name_en = nameEn;
        option.is_active = active;
        option.sort_order = sortOrder;
        option.price_delta = priceDelta;
        return option;
    }

    private MenuItemOption findByCode(List<MenuItemOption> options, String code) {
        return options.stream()
            .filter(option -> code.equals(option.option_code))
            .findFirst()
            .orElseThrow();
    }
}
