package com.restaurant.system.menu.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

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
        request.option_type = "remove";
        request.option_code = "no_cilantro";
        request.option_group = "REMOVE";
        request.name_zh = "走香菜";
        request.name_en = "No Cilantro";
        request.price_delta = BigDecimal.ZERO;

        var response = service.createOption(14L, request);

        assertEquals(90L, response.id);
        verify(menuRevisionService).incrementRevision(3L);
    }

    @Test
    void legacyTypeOnlySizeWithoutCodeDoesNotBlockRemoveRoundTrip() {
        MenuItem item = menuItem(14L, 3L);
        MenuItemOption legacySize = sizeOption(81L, null, true, 10);
        legacySize.option_group = null;
        legacySize.name_zh = "标准份";
        legacySize.name_en = "Regular";
        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(item));
        when(menuItemOptionRepository.findAllByMenuItemIdOrdered(14L)).thenReturn(List.of(legacySize));
        when(menuItemOptionRepository.save(any(MenuItemOption.class))).thenAnswer(invocation -> {
            MenuItemOption option = invocation.getArgument(0);
            option.id = 90L;
            return option;
        });
        MenuItemOptionUpsertRequest request = new MenuItemOptionUpsertRequest();
        request.option_type = "remove";
        request.option_group = "REMOVE";
        request.option_code = "no_cilantro";
        request.name_zh = "走香菜";
        request.name_en = "No Cilantro";

        var response = service.createOption(14L, request);

        assertEquals("no_cilantro", response.option_code);
        verify(menuRevisionService).incrementRevision(3L);
    }

    @Test
    void canonicalSizeWithoutCodeStillRejectsUnrelatedOptionMutation() {
        MenuItem item = menuItem(14L, 3L);
        MenuItemOption invalidSize = sizeOption(81L, null, true, 10);
        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(item));
        when(menuItemOptionRepository.findAllByMenuItemIdOrdered(14L)).thenReturn(List.of(invalidSize));
        MenuItemOptionUpsertRequest request = new MenuItemOptionUpsertRequest();
        request.option_type = "remove";
        request.option_group = "REMOVE";
        request.option_code = "no_cilantro";
        request.name_zh = "走香菜";
        request.name_en = "No Cilantro";

        BusinessException error = assertThrows(
            BusinessException.class,
            () -> service.createOption(14L, request)
        );

        assertEquals("SIZE option code is required", error.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ADD_ON", " add_on ", " "})
    void creatingCanonicalOrLegacyAddonRequiresCatalog(String group) {
        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(menuItem(14L, 3L)));
        when(menuItemOptionRepository.findAllByMenuItemIdOrdered(14L)).thenReturn(List.of());

        BusinessException error = assertThrows(BusinessException.class,
            () -> service.createOption(14L, addonRequest(group)));

        assertTrue(error.getMessage().contains("Store Add-on catalog"));
        verify(menuItemOptionRepository, never()).save(any());
        verify(menuRevisionService, never()).incrementRevision(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ADD_ON", " add_on ", " "})
    void sourceAddonCannotBeRenamedRepricedRegroupedOrDeactivated(String group) {
        MenuItemOption existing = addonOption(group);
        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(menuItem(14L, 3L)));
        when(menuItemOptionRepository.findAllByMenuItemIdOrdered(14L)).thenReturn(List.of(existing));
        MenuItemOptionUpsertRequest replacement = addonRequest("REMOVE");
        replacement.option_type = "remove";
        replacement.name_zh = "替换";
        replacement.price_delta = new BigDecimal("99.00");
        replacement.option_code = "replacement";

        assertThrows(BusinessException.class, () -> service.updateOption(14L, 90L, replacement));
        assertThrows(BusinessException.class, () -> service.deactivateOption(14L, 90L));

        assertEquals("加煎蛋", existing.name_zh);
        assertEquals("fried_egg", existing.option_code);
        assertEquals(new BigDecimal("2.00"), existing.price_delta);
        assertEquals(true, existing.is_active);
        verify(menuItemOptionRepository, never()).save(any());
        verify(menuRevisionService, never()).incrementRevision(any());
    }

    @Test
    void nonAddonCannotBeConvertedIntoAddonAndRejectedWriteLeavesSourceIntact() {
        MenuItemOption existing = addonOption("REMOVE");
        existing.option_type = "remove";
        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(menuItem(14L, 3L)));
        when(menuItemOptionRepository.findAllByMenuItemIdOrdered(14L)).thenReturn(List.of(existing));

        assertThrows(BusinessException.class, () -> service.updateOption(14L, 90L, addonRequest("ADD_ON")));

        assertEquals("REMOVE", existing.option_group);
        verify(menuItemOptionRepository, never()).save(any());
    }

    @Test
    void genericAddonReorderCannotSaveCatalogOwnedRows() {
        MenuItemOption existing = addonOption("ADD_ON");
        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(menuItem(14L, 3L)));
        when(menuItemOptionRepository.findAllByMenuItemIdOrdered(14L)).thenReturn(List.of(existing));
        MenuItemOptionReorderRequest request = new MenuItemOptionReorderRequest();
        MenuItemOptionReorderRequest.OptionOrder order = new MenuItemOptionReorderRequest.OptionOrder();
        order.id = existing.id;
        order.sort_order = 10;
        request.options = List.of(order);

        assertThrows(BusinessException.class, () -> service.reorderOptions(14L, request));

        verify(menuItemOptionRepository, never()).save(any());
    }

    @Test
    void explicitComboComponentGroupRetainsExistingGenericEditingBehavior() {
        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(menuItem(14L, 3L)));
        when(menuItemOptionRepository.findAllByMenuItemIdOrdered(14L)).thenReturn(List.of());
        when(menuItemOptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        MenuItemOptionUpsertRequest request = addonRequest("COMBO_EGG");
        request.option_code = "combo_fried_egg";

        assertEquals("COMBO_EGG", service.createOption(14L, request).option_group);
        verify(menuRevisionService).incrementRevision(3L);
    }

    @Test
    void reorderingOtherGroupsDoesNotResaveUnselectedAddonRows() {
        MenuItemOption addon = addonOption("ADD_ON");
        MenuItemOption remove = addonOption("REMOVE");
        remove.id = 91L;
        remove.option_type = "remove";
        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(menuItem(14L, 3L)));
        when(menuItemOptionRepository.findAllByMenuItemIdOrdered(14L)).thenReturn(List.of(addon, remove));
        MenuItemOptionReorderRequest request = new MenuItemOptionReorderRequest();
        MenuItemOptionReorderRequest.OptionOrder order = new MenuItemOptionReorderRequest.OptionOrder();
        order.id = remove.id;
        order.sort_order = 30;
        request.options = List.of(order);

        service.reorderOptions(14L, request);

        verify(menuItemOptionRepository).save(remove);
        verify(menuItemOptionRepository, never()).save(addon);
        verify(menuRevisionService).incrementRevision(3L);
    }

    private MenuItemOptionUpsertRequest addonRequest(String group) {
        MenuItemOptionUpsertRequest request = new MenuItemOptionUpsertRequest();
        request.option_type = "addon";
        request.option_group = group;
        request.option_code = "fried_egg";
        request.name_zh = "加煎蛋";
        request.name_en = "Fried Egg";
        request.price_delta = new BigDecimal("2.00");
        return request;
    }

    private MenuItemOption addonOption(String group) {
        MenuItemOption option = new MenuItemOption();
        option.id = 90L;
        option.menu_item_id = 14L;
        option.option_type = "addon";
        option.option_group = group;
        option.option_code = "fried_egg";
        option.name_zh = "加煎蛋";
        option.name_en = "Fried Egg";
        option.price_delta = new BigDecimal("2.00");
        option.is_active = true;
        return option;
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

    @Test
    void sourceComboDeactivationAndRegroupingPreserveOptionAndItemOverride() {
        MenuItem item = menuItem(14L, 3L);
        item.default_combo_egg_component_code = "combo_fried_egg";
        MenuItemOption existing = comboOption(91L);
        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(item));
        when(menuItemOptionRepository.findAllByMenuItemIdOrdered(14L)).thenReturn(List.of(existing));
        MenuItemOptionUpsertRequest request = addonRequest("REMOVE");
        request.option_type = "remove";
        request.option_code = "no_cilantro";

        assertThrows(BusinessException.class, () -> service.deactivateOption(14L, 91L));
        assertThrows(BusinessException.class, () -> service.updateOption(14L, 91L, request));

        assertEquals("COMBO", existing.option_group);
        assertEquals("combo", existing.option_code);
        assertEquals(true, existing.is_active);
        assertEquals(new BigDecimal("5.00"), existing.price_delta);
        assertEquals("combo_fried_egg", item.default_combo_egg_component_code);
        verify(menuItemOptionRepository, never()).save(any());
        verify(menuItemRepository, never()).save(any());
        verify(menuRevisionService, never()).incrementRevision(any());
    }

    @Test
    void destinationComboIsRejectedBeforeChangingManagedOption() {
        MenuItem item = menuItem(14L, 3L);
        item.default_combo_egg_component_code = "combo_fried_egg";
        MenuItemOption existing = addonOption("REMOVE");
        existing.option_type = "remove";
        existing.option_code = "no_cilantro";
        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(item));
        when(menuItemOptionRepository.findAllByMenuItemIdOrdered(14L)).thenReturn(List.of(existing));
        MenuItemOptionUpsertRequest request = addonRequest("COMBO");
        request.option_code = "combo";
        request.name_zh = "套餐";
        request.name_en = "Combo";

        BusinessException error = assertThrows(BusinessException.class,
            () -> service.updateOption(14L, 90L, request));

        assertTrue(error.getMessage().contains("Combo Policy"));
        assertEquals("REMOVE", existing.option_group);
        assertEquals("no_cilantro", existing.option_code);
        assertEquals(true, existing.is_active);
        assertEquals("combo_fried_egg", item.default_combo_egg_component_code);
        verify(menuItemOptionRepository, never()).save(any());
        verify(menuRevisionService, never()).incrementRevision(any());
    }

    @Test
    void explicitRemoveWithComboCodeAndNamesRemainsEditableAndCanBeDeactivated() {
        MenuItem item = menuItem(14L, 3L);
        item.default_combo_egg_component_code = "combo_fried_egg";
        MenuItemOption existing = addonOption("REMOVE");
        existing.option_code = "combo";
        existing.name_zh = "套餐";
        existing.name_en = "Combo";
        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(item));
        when(menuItemOptionRepository.findAllByMenuItemIdOrdered(14L)).thenReturn(List.of(existing));
        when(menuItemOptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        MenuItemOptionUpsertRequest request = addonRequest("REMOVE");
        request.option_code = "combo";
        request.name_zh = "套餐";
        request.name_en = "Combo";

        assertEquals("REMOVE", service.updateOption(14L, 90L, request).option_group);
        assertEquals(false, service.deactivateOption(14L, 90L).is_active);

        assertEquals("combo_fried_egg", item.default_combo_egg_component_code);
        assertEquals("combo", existing.option_code);
        verify(menuItemRepository, never()).save(any());
    }

    @Test
    void genericCreateRespectsExplicitRemoveEvenWhenNamedCombo() {
        when(menuItemRepository.findById(14L)).thenReturn(Optional.of(menuItem(14L, 3L)));
        when(menuItemOptionRepository.findAllByMenuItemIdOrdered(14L)).thenReturn(List.of());
        when(menuItemOptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        MenuItemOptionUpsertRequest request = addonRequest("REMOVE");
        request.option_code = "combo";
        request.name_zh = "套餐";
        request.name_en = "Combo";

        assertEquals("REMOVE", service.createOption(14L, request).option_group);
        verify(menuRevisionService).incrementRevision(3L);
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
        option.name_zh = switch (code == null ? "" : code) {
            case "size_small" -> "小碗";
            case "size_regular" -> "中碗";
            default -> "大碗";
        };
        option.name_en = switch (code == null ? "" : code) {
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
