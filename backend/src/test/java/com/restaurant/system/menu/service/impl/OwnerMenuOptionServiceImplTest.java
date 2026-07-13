package com.restaurant.system.menu.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurant.system.menu.dto.MenuItemOptionUpsertRequest;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.MenuRevisionService;
import java.math.BigDecimal;
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
}
