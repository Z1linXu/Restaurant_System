package com.restaurant.system.printing.semantic;

import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.entity.OrderItemOption;
import com.restaurant.system.printing.dto.PrintRenderRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class HotKitchenPrintEligibilityService {

    private static final int COMBO_SIDE_TASK_PRIORITY = 100;
    private static final String COMBO_ROLE_SIDE = "combo_side";
    private static final Set<String> DEEP_FRIED_CATEGORY_CODES = Set.of("FRIED", "DEEPFRIED");
    private static final Set<String> WOK_CATEGORY_CODES = Set.of("FRIED_NOODLE");
    private static final Set<String> WOK_SKUS = Set.of(
        "beef_chow_mein",
        "chicken_chow_mein",
        "tomato_chow_mein",
        "vegetable_chow_mein"
    );

    private final MenuItemRepository menuItemRepository;
    private final OptionSemanticResolver optionSemanticResolver;

    public HotKitchenPrintEligibilityService(
        MenuItemRepository menuItemRepository,
        OptionSemanticResolver optionSemanticResolver
    ) {
        this.menuItemRepository = menuItemRepository;
        this.optionSemanticResolver = optionSemanticResolver;
    }

    public boolean hasHotKitchenContent(PrintRenderRequest request) {
        return !resolveHotKitchenTasks(request).isEmpty();
    }

    public List<KitchenTask> resolveHotKitchenTasks(PrintRenderRequest request) {
        if (request == null || request.kitchen_tasks == null || request.kitchen_tasks.isEmpty()) {
            return List.of();
        }

        Map<Long, OrderItem> itemsById = new HashMap<>();
        if (request.order_items != null) {
            for (OrderItem item : request.order_items) {
                if (item != null && item.id != null) {
                    itemsById.put(item.id, item);
                }
            }
        }

        Map<Long, List<OrderItemOption>> optionsByItemId = new HashMap<>();
        if (request.order_item_options != null) {
            for (OrderItemOption option : request.order_item_options) {
                if (option != null && option.order_item_id != null) {
                    optionsByItemId.computeIfAbsent(option.order_item_id, ignored -> new ArrayList<>()).add(option);
                }
            }
        }

        Map<Long, String> skuByMenuItemId = new HashMap<>();
        List<KitchenTask> hotTasks = new ArrayList<>();
        Set<Long> seenTaskIds = new HashSet<>();
        for (KitchenTask task : request.kitchen_tasks) {
            if (task == null || "cancelled".equals(task.status)) {
                continue;
            }
            OrderItem item = itemsById.get(task.order_item_id);
            List<OrderItemOption> options = optionsByItemId.getOrDefault(task.order_item_id, List.of());
            if (isHotKitchenTask(task, item, options, skuByMenuItemId)) {
                if (task.id == null || seenTaskIds.add(task.id)) {
                    hotTasks.add(task);
                }
            }
        }
        return hotTasks;
    }

    private boolean isHotKitchenTask(
        KitchenTask task,
        OrderItem item,
        List<OrderItemOption> options,
        Map<Long, String> skuByMenuItemId
    ) {
        boolean hotStation = isDeepFriedStation(task) || isWokStation(task);
        if (isSyntheticComboSideTask(task)) {
            return hotStation;
        }

        boolean hotItem = isDeepFriedItem(item) || isWokItem(item, skuByMenuItemId);
        if (isComboSideOrderItem(item)) {
            return hotStation || hotItem;
        }

        return hotStation
            || hotItem
            || hasFriedEggOption(options);
    }

    private boolean isDeepFriedStation(KitchenTask task) {
        return task != null && "DEEPFRIED".equals(task.station_code);
    }

    private boolean isDeepFriedItem(OrderItem item) {
        return item != null && item.category_code_snapshot != null && DEEP_FRIED_CATEGORY_CODES.contains(item.category_code_snapshot);
    }

    private boolean isWokStation(KitchenTask task) {
        return task != null && "WOK".equals(task.station_code);
    }

    private boolean isWokItem(OrderItem item, Map<Long, String> skuByMenuItemId) {
        if (item == null) {
            return false;
        }
        if (item.category_code_snapshot != null && WOK_CATEGORY_CODES.contains(item.category_code_snapshot)) {
            return true;
        }
        String sku = resolveSku(item, skuByMenuItemId);
        return sku != null && WOK_SKUS.contains(sku);
    }

    private boolean isSyntheticComboSideTask(KitchenTask task) {
        return task != null && Integer.valueOf(COMBO_SIDE_TASK_PRIORITY).equals(task.priority);
    }

    private boolean isComboSideOrderItem(OrderItem item) {
        return item != null && item.combo_role != null && COMBO_ROLE_SIDE.equalsIgnoreCase(item.combo_role);
    }

    private boolean hasFriedEggOption(List<OrderItemOption> options) {
        for (OrderItemOption option : options) {
            if (optionSemanticResolver.isFriedEgg(option) || optionSemanticResolver.isComboFriedEgg(option)) {
                return true;
            }
        }
        return false;
    }

    private String resolveSku(OrderItem item, Map<Long, String> skuByMenuItemId) {
        if (item == null || item.menu_item_id == null) {
            return null;
        }
        if (skuByMenuItemId.containsKey(item.menu_item_id)) {
            return skuByMenuItemId.get(item.menu_item_id);
        }
        String sku = menuItemRepository.findById(item.menu_item_id)
            .map((MenuItem menuItem) -> menuItem.sku)
            .orElse(null);
        skuByMenuItemId.put(item.menu_item_id, sku);
        return sku;
    }
}
