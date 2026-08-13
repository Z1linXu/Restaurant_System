package com.restaurant.system.menu.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.dto.MenuItemOptionUpsertRequest;
import com.restaurant.system.menu.dto.MenuItemOptionReorderRequest;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.MenuRevisionService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OwnerMenuOptionServiceImplTest {

    @Mock
    private MenuItemRepository menuItemRepository;
    @Mock
    private MenuItemOptionRepository menuItemOptionRepository;
    @Mock
    private MenuRevisionService menuRevisionService;

    private OwnerMenuOptionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OwnerMenuOptionServiceImpl(
            menuItemRepository,
            menuItemOptionRepository,
            menuRevisionService
        );
    }

    @Test
    void creatingOptionBumpsOwningStoreMenuRevision() {
        MenuItem item = new MenuItem();
        item.id = 14L;
        item.store_id = 3L;
        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(item));
        when(menuItemOptionRepository.findAllByMenuItemIdOrdered(14L)).thenReturn(List.of());
        when(menuItemOptionRepository.save(any(MenuItemOption.class))).thenAnswer(invocation -> {
            MenuItemOption option = invocation.getArgument(0);
            option.id = 90L;
            return option;
        });
        MenuItemOptionUpsertRequest request = new MenuItemOptionUpsertRequest();
        request.option_type = "addon";
        request.option_code = "fried_egg";
        request.option_group = "ADD_ON";
        request.name_zh = "加煎蛋";
        request.name_en = "Fried Egg";
        request.price_delta = new BigDecimal("2.00");

        var response = service.createOption(14L, request);

        assertEquals(90L, response.id);
        verify(menuRevisionService).incrementRevision(3L);
    }

    @Test
    void creatingSizeOptionThroughGenericEndpointIsRejected() {
        MenuItem item = menuItem(14L, 3L);
        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(item));
        when(menuItemOptionRepository.findAllByMenuItemIdOrdered(14L)).thenReturn(List.of());
        MenuItemOptionUpsertRequest request = new MenuItemOptionUpsertRequest();
        request.option_group = "SIZE";
        request.option_code = "size_small";
        request.name_zh = "小碗";
        request.name_en = "Small";
        request.sort_order = 10;
        request.price_delta = new BigDecimal("-1.00");

        assertThrows(BusinessException.class, () -> service.createOption(14L, request));
    }

    @Test
    void creatingComboUpchargeThroughGenericEndpointIsRejected() {
        MenuItem item = menuItem(14L, 3L);
        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(item));
        when(menuItemOptionRepository.findAllByMenuItemIdOrdered(14L)).thenReturn(List.of());
        MenuItemOptionUpsertRequest request = new MenuItemOptionUpsertRequest();
        request.option_group = "COMBO";
        request.option_code = "combo";
        request.name_zh = "套餐";
        request.name_en = "Combo";
        request.sort_order = 100;
        request.price_delta = new BigDecimal("5.00");

        assertThrows(BusinessException.class, () -> service.createOption(14L, request));
    }

    @Test
    void updatingExistingComboUpchargeThroughGenericEndpointIsRejected() {
        MenuItem item = menuItem(14L, 3L);
        MenuItemOption existing = comboOption(91L);
        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(item));
        when(menuItemOptionRepository.findAllByMenuItemIdOrdered(14L)).thenReturn(List.of(existing));
        MenuItemOptionUpsertRequest request = new MenuItemOptionUpsertRequest();
        request.option_group = "ADD_ON";
        request.option_code = "combo_custom";
        request.name_zh = "套餐";
        request.name_en = "Combo";
        request.price_delta = new BigDecimal("7.00");

        assertThrows(BusinessException.class, () -> service.updateOption(14L, 91L, request));
    }

    @Test
    void creatingDuplicateSizeCodeIsRejected() {
        MenuItem item = menuItem(14L, 3L);
        MenuItemOption existing = sizeOption(81L, "size_small", true, 10);
        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(item));
        when(menuItemOptionRepository.findAllByMenuItemIdOrdered(14L)).thenReturn(List.of(existing));
        MenuItemOptionUpsertRequest request = new MenuItemOptionUpsertRequest();
        request.option_group = "SIZE";
        request.option_code = "SIZE_SMALL";
        request.name_zh = "小";
        request.name_en = "Small";
        request.sort_order = 20;
        request.price_delta = BigDecimal.ZERO;

        assertThrows(BusinessException.class, () -> service.createOption(14L, request));
    }

    @Test
    void creatingTypeOnlySizeWithParentIsRejected() {
        MenuItem item = menuItem(14L, 3L);
        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(item));
        when(menuItemOptionRepository.findAllByMenuItemIdOrdered(14L)).thenReturn(List.of());
        MenuItemOptionUpsertRequest request = new MenuItemOptionUpsertRequest();
        request.option_type = "size";
        request.option_group = null;
        request.option_code = "size_legacy";
        request.parent_option_id = 80L;
        request.name_zh = "旧规格";
        request.name_en = "Legacy Size";
        request.sort_order = 10;
        request.price_delta = BigDecimal.ZERO;

        assertThrows(BusinessException.class, () -> service.createOption(14L, request));
    }

    @Test
    void deactivatingLastActiveSizeIsRejected() {
        MenuItem item = menuItem(14L, 3L);
        MenuItemOption existing = sizeOption(81L, "size_regular", true, 10);
        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(item));
        when(menuItemOptionRepository.findAllByMenuItemIdOrdered(14L)).thenReturn(List.of(existing));

        assertThrows(BusinessException.class, () -> service.deactivateOption(14L, 81L));
    }

    @Test
    void deactivatingComboUpchargeThroughGenericEndpointIsRejected() {
        MenuItem item = menuItem(14L, 3L);
        MenuItemOption existing = comboOption(91L);
        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(item));
        when(menuItemOptionRepository.findAllByMenuItemIdOrdered(14L)).thenReturn(List.of(existing));

        assertThrows(BusinessException.class, () -> service.deactivateOption(14L, 91L));
    }

    @Test
    void reorderingSizesThroughGenericEndpointIsRejected() {
        MenuItem item = menuItem(14L, 3L);
        MenuItemOption small = sizeOption(81L, "size_small", true, 20);
        MenuItemOption regular = sizeOption(82L, "size_regular", true, 10);
        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(item));
        when(menuItemOptionRepository.findAllByMenuItemIdOrdered(14L))
            .thenReturn(List.of(regular, small));
        MenuItemOptionReorderRequest request = new MenuItemOptionReorderRequest();
        MenuItemOptionReorderRequest.OptionOrder smallFirst = new MenuItemOptionReorderRequest.OptionOrder();
        smallFirst.id = 81L;
        smallFirst.sort_order = 10;
        MenuItemOptionReorderRequest.OptionOrder regularSecond = new MenuItemOptionReorderRequest.OptionOrder();
        regularSecond.id = 82L;
        regularSecond.sort_order = 20;
        request.options = List.of(smallFirst, regularSecond);

        assertThrows(BusinessException.class, () -> service.reorderOptions(14L, request));
    }

    @Test
    void reorderingComboUpchargeThroughGenericEndpointIsRejected() {
        MenuItem item = menuItem(14L, 3L);
        MenuItemOption combo = comboOption(91L);
        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(item));
        when(menuItemOptionRepository.findAllByMenuItemIdOrdered(14L))
            .thenReturn(List.of(combo));
        MenuItemOptionReorderRequest request = new MenuItemOptionReorderRequest();
        MenuItemOptionReorderRequest.OptionOrder comboOrder = new MenuItemOptionReorderRequest.OptionOrder();
        comboOrder.id = 91L;
        comboOrder.sort_order = 120;
        request.options = List.of(comboOrder);

        assertThrows(BusinessException.class, () -> service.reorderOptions(14L, request));
    }

    private MenuItem menuItem(Long id, Long storeId) {
        MenuItem item = new MenuItem();
        item.id = id;
        item.store_id = storeId;
        return item;
    }

    private MenuItemOption sizeOption(Long id, String code, boolean active, Integer sortOrder) {
        MenuItemOption option = new MenuItemOption();
        option.id = id;
        option.menu_item_id = 14L;
        option.option_group = "SIZE";
        option.option_type = "size";
        option.option_code = code;
        option.name_zh = switch (code) {
            case "size_small" -> "小碗";
            case "size_regular" -> "中碗";
            default -> "大碗";
        };
        option.name_en = switch (code) {
            case "size_small" -> "Small";
            case "size_regular" -> "Regular";
            default -> "Large";
        };
        option.price_delta = BigDecimal.ZERO;
        option.is_active = active;
        option.sort_order = sortOrder;
        return option;
    }

    private MenuItemOption comboOption(Long id) {
        MenuItemOption option = new MenuItemOption();
        option.id = id;
        option.menu_item_id = 14L;
        option.option_group = "COMBO";
        option.option_type = "addon";
        option.option_code = "combo";
        option.name_zh = "套餐";
        option.name_en = "Combo";
        option.price_delta = new BigDecimal("5.00");
        option.is_active = true;
        option.sort_order = 100;
        return option;
    }
}
