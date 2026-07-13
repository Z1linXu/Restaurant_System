package com.restaurant.system.menu.service.impl;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.dto.MenuItemOptionAdminResponse;
import com.restaurant.system.menu.dto.MenuItemOptionReorderRequest;
import com.restaurant.system.menu.dto.MenuItemOptionUpsertRequest;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.OwnerMenuOptionService;
import com.restaurant.system.menu.service.MenuRevisionService;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerMenuOptionServiceImpl implements OwnerMenuOptionService {

    public static final String GROUP_COMBO_SIDE = "COMBO_SIDE";
    public static final String GROUP_COMBO_SIDE_REMOVE = "COMBO_SIDE_REMOVE";

    private final MenuItemRepository menuItemRepository;
    private final MenuItemOptionRepository menuItemOptionRepository;
    private final MenuRevisionService menuRevisionService;

    public OwnerMenuOptionServiceImpl(
        MenuItemRepository menuItemRepository,
        MenuItemOptionRepository menuItemOptionRepository,
        MenuRevisionService menuRevisionService
    ) {
        this.menuItemRepository = menuItemRepository;
        this.menuItemOptionRepository = menuItemOptionRepository;
        this.menuRevisionService = menuRevisionService;
    }

    @Override
    public List<MenuItemOptionAdminResponse> getOptions(Long itemId) {
        loadMenuItem(itemId);
        return menuItemOptionRepository.findAllByMenuItemIdOrdered(itemId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    @Transactional
    public MenuItemOptionAdminResponse createOption(Long itemId, MenuItemOptionUpsertRequest request) {
        MenuItem menuItem = loadMenuItem(itemId);
        MenuItemOption option = new MenuItemOption();
        option.menu_item_id = itemId;
        applyRequest(option, request, true);
        MenuItemOption saved = menuItemOptionRepository.save(option);
        menuRevisionService.incrementRevision(menuItem.store_id);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public MenuItemOptionAdminResponse updateOption(Long itemId, Long optionId, MenuItemOptionUpsertRequest request) {
        MenuItem menuItem = loadMenuItem(itemId);
        MenuItemOption option = loadOption(itemId, optionId);
        applyRequest(option, request, false);
        MenuItemOption saved = menuItemOptionRepository.save(option);
        menuRevisionService.incrementRevision(menuItem.store_id);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public MenuItemOptionAdminResponse deactivateOption(Long itemId, Long optionId) {
        MenuItem menuItem = loadMenuItem(itemId);
        MenuItemOption option = loadOption(itemId, optionId);
        option.is_active = false;
        option.updated_at = LocalDateTime.now();
        MenuItemOption saved = menuItemOptionRepository.save(option);
        menuRevisionService.incrementRevision(menuItem.store_id);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public List<MenuItemOptionAdminResponse> reorderOptions(Long itemId, MenuItemOptionReorderRequest request) {
        MenuItem menuItem = loadMenuItem(itemId);
        if (request == null || request.options == null) {
            throw new BusinessException("Options reorder payload is required");
        }
        Map<Long, MenuItemOption> optionsById = menuItemOptionRepository.findAllByMenuItemIdOrdered(itemId).stream()
            .collect(Collectors.toMap(option -> option.id, Function.identity()));
        LocalDateTime now = LocalDateTime.now();
        for (MenuItemOptionReorderRequest.OptionOrder optionOrder : request.options) {
            if (optionOrder == null || optionOrder.id == null) {
                throw new BusinessException("Option id is required for reorder");
            }
            MenuItemOption option = optionsById.get(optionOrder.id);
            if (option == null) {
                throw new BusinessException("Cannot reorder option from another menu item: " + optionOrder.id);
            }
            option.sort_order = optionOrder.sort_order;
            option.updated_at = now;
            menuItemOptionRepository.save(option);
        }
        menuRevisionService.incrementRevision(menuItem.store_id);
        return getOptions(itemId);
    }

    private void applyRequest(MenuItemOption option, MenuItemOptionUpsertRequest request, boolean creating) {
        if (request == null) {
            throw new BusinessException("Menu item option payload is required");
        }
        if (request.name_zh == null || request.name_zh.isBlank()) {
            throw new BusinessException("Option Chinese name is required");
        }
        String optionGroup = normalizeGroup(request.option_group);
        Long parentOptionId = request.parent_option_id;
        validateParent(option.menu_item_id, option.id, optionGroup, parentOptionId);

        option.option_type = normalizeOptionType(request.option_type, optionGroup);
        option.option_code = blankToNull(request.option_code);
        option.option_group = optionGroup;
        option.parent_option_id = parentOptionId;
        option.sort_order = request.sort_order;
        option.name_zh = request.name_zh.trim();
        option.name_en = blankToNull(request.name_en);
        option.price_delta = request.price_delta == null ? java.math.BigDecimal.ZERO : request.price_delta;
        option.is_active = request.is_active == null ? true : request.is_active;
        option.updated_at = LocalDateTime.now();
        if (creating) {
            option.created_at = option.updated_at;
        }
    }

    private void validateParent(Long itemId, Long optionId, String optionGroup, Long parentOptionId) {
        if (parentOptionId == null) {
            if (GROUP_COMBO_SIDE_REMOVE.equals(optionGroup)) {
                throw new BusinessException("COMBO_SIDE_REMOVE requires a COMBO_SIDE parent option");
            }
            return;
        }
        if (optionId != null && optionId.equals(parentOptionId)) {
            throw new BusinessException("Parent option cannot point to itself");
        }
        MenuItemOption parent = loadOption(itemId, parentOptionId);
        String parentGroup = normalizeGroup(parent.option_group);
        if (GROUP_COMBO_SIDE_REMOVE.equals(optionGroup) && !GROUP_COMBO_SIDE.equals(parentGroup)) {
            throw new BusinessException("COMBO_SIDE_REMOVE parent must be COMBO_SIDE");
        }
        Set<Long> visited = new HashSet<>();
        Long cursor = parent.parent_option_id;
        while (cursor != null) {
            if (!visited.add(cursor)) {
                throw new BusinessException("Parent option cycle detected");
            }
            if (optionId != null && optionId.equals(cursor)) {
                throw new BusinessException("Parent option cycle detected");
            }
            MenuItemOption ancestor = loadOption(itemId, cursor);
            cursor = ancestor.parent_option_id;
        }
    }

    private MenuItem loadMenuItem(Long itemId) {
        if (itemId == null) {
            throw new BusinessException("Menu item id is required");
        }
        return menuItemRepository.findById(itemId)
            .orElseThrow(() -> new BusinessException("Menu item not found: " + itemId));
    }

    private MenuItemOption loadOption(Long itemId, Long optionId) {
        if (optionId == null) {
            throw new BusinessException("Option id is required");
        }
        MenuItemOption option = menuItemOptionRepository.findById(optionId)
            .orElseThrow(() -> new BusinessException("Menu item option not found: " + optionId));
        if (!itemId.equals(option.menu_item_id)) {
            throw new BusinessException("Menu item option does not belong to item " + itemId);
        }
        return option;
    }

    private String normalizeOptionType(String optionType, String optionGroup) {
        if (optionType != null && !optionType.isBlank()) {
            return optionType.trim().toLowerCase();
        }
        if (optionGroup == null || optionGroup.isBlank()) {
            return "addon";
        }
        return switch (optionGroup) {
            case "SIZE" -> "size";
            case "SOUP_BASE" -> "soup_base";
            case "NOODLE_TYPE" -> "noodle_type";
            case "SPICY_LEVEL" -> "spicy_level";
            case "REMOVE", "COMBO_SIDE_REMOVE" -> "remove";
            default -> "addon";
        };
    }

    private String normalizeGroup(String optionGroup) {
        if (optionGroup == null || optionGroup.isBlank()) {
            return null;
        }
        return optionGroup.trim().toUpperCase();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private MenuItemOptionAdminResponse toResponse(MenuItemOption option) {
        MenuItemOptionAdminResponse response = new MenuItemOptionAdminResponse();
        response.id = option.id;
        response.menu_item_id = option.menu_item_id;
        response.option_type = option.option_type;
        response.option_code = option.option_code;
        response.option_group = option.option_group;
        response.parent_option_id = option.parent_option_id;
        response.sort_order = option.sort_order;
        response.name_zh = option.name_zh;
        response.name_en = option.name_en;
        response.price_delta = option.price_delta;
        response.is_active = option.is_active;
        response.created_at = option.created_at;
        response.updated_at = option.updated_at;
        return response;
    }
}
