package com.restaurant.system.menu.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurant.system.menu.dto.MenuItemSizeConfigurationRequest;
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
        verify(menuRevisionService).lockStoresInOrder(List.of(3L));
        verify(storePricingPolicyRepository).mirrorPolicyToSizeAndComboOptions(3L);
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
