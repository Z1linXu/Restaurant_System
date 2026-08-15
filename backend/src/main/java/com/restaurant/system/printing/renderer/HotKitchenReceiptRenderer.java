package com.restaurant.system.printing.renderer;

import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.entity.OrderItemOption;
import com.restaurant.system.printing.PrintModuleCode;
import com.restaurant.system.printing.dto.PrintRenderRequest;
import com.restaurant.system.printing.rules.PrintingDisplayRuleContext;
import com.restaurant.system.printing.semantic.HotKitchenPrintEligibilityService;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class HotKitchenReceiptRenderer implements ReceiptRenderer {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final Set<String> MODIFIER_PREFIXES = Set.of("+", "走", "少", "不要", "无");
    private static final String OPTION_TYPE_SIZE = "size";
    private static final String OPTION_TYPE_NOODLE_TYPE = "noodle_type";
    private static final String OPTION_TYPE_SPICY_LEVEL = "spicy_level";
    private static final String OPTION_TYPE_SOUP_BASE = "soup_base";
    private static final String OPTION_TYPE_ADDON = "addon";
    private static final String OPTION_TYPE_REMOVE = "remove";

    private final HotKitchenPrintEligibilityService eligibilityService;

    public HotKitchenReceiptRenderer(HotKitchenPrintEligibilityService eligibilityService) {
        this.eligibilityService = eligibilityService;
    }

    @Override
    public String getModuleCode() {
        return PrintModuleCode.HOT_KITCHEN;
    }

    @Override
    public String render(PrintRenderRequest request) {
        List<KitchenTask> tasks = eligibilityService.resolveHotKitchenTasks(request);
        PrintingDisplayRuleContext printingRules = request.printing_rules;
        if (tasks.isEmpty()) {
            return "";
        }

        Map<Long, OrderItem> itemById = new HashMap<>();
        if (request.order_items != null) {
            for (OrderItem item : request.order_items) {
                if (item != null && item.id != null) {
                    itemById.put(item.id, item);
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

        StringBuilder builder = new StringBuilder();
        builder.append("\n\n\n");
        if (Boolean.TRUE.equals(request.is_update_ticket)) {
            builder.append(PrintMarkup.large("UPDATED")).append("\n");
            builder.append("--------------------------------\n");
        }
        String topLabel = resolveTopDisplayLabel(request.order);
        if (topLabel != null) {
            builder.append(PrintMarkup.large(topLabel)).append("\n");
        }
        builder.append(PrintMarkup.large("HOT KITCHEN")).append("\n");
        builder.append("--------------------------------\n");

        for (AggregatedHotKitchenTask task : aggregateTasks(tasks, itemById, optionsByItemId, printingRules)) {
            appendTask(builder, task);
        }

        builder.append("--------------------------------\n");
        if (isTakeout(request.order)) {
            builder.append(PrintMarkup.large("外卖 / TAKEOUT")).append("\n");
        }
        builder.append(PrintMarkup.small(resolveTime(request.order))).append("\n");
        builder.append("--------------------------------\n\n");
        return builder.toString();
    }

    private void appendTask(StringBuilder builder, AggregatedHotKitchenTask task) {
        if (task.noodleConfig() != null) {
            builder.append(PrintMarkup.doubleHeight(KitchenNoodlePrintFormatter.formatLine(task.noodleConfig(), task.quantity()))).append("\n");
            builder.append("\n");
            return;
        }
        builder.append(PrintMarkup.doubleHeight(resolvePrimaryLine(task.representative(), task.quantity()))).append("\n");
        String secondary = resolveSecondaryLine(task.representative());
        if (secondary != null) {
            builder.append(PrintMarkup.doubleHeight(secondary)).append("\n");
        }
        String note = task.item() == null ? null : normalize(task.item().notes);
        if (note != null) {
            builder.append(PrintMarkup.doubleHeight("备注：" + note)).append("\n");
        }
        builder.append("\n");
    }

    private List<AggregatedHotKitchenTask> aggregateTasks(
        List<KitchenTask> tasks,
        Map<Long, OrderItem> itemById,
        Map<Long, List<OrderItemOption>> optionsByItemId,
        PrintingDisplayRuleContext printingRules
    ) {
        Map<HotKitchenGroupKey, AggregatedHotKitchenTask> grouped = new LinkedHashMap<>();
        for (KitchenTask originalTask : tasks) {
            OrderItem item = itemById.get(originalTask.order_item_id);
            List<OrderItemOption> options = optionsByItemId.getOrDefault(originalTask.order_item_id, List.of());
            KitchenTask task = applyHotKitchenDisplayRules(originalTask, item, options, printingRules);
            KitchenNoodlePrintFormatter.NoodleConfig noodleConfig = KitchenNoodlePrintFormatter.isNoodleTask(task, item)
                ? KitchenNoodlePrintFormatter.buildConfig(task, item, KitchenNoodlePrintFormatter::normalizeModifierSegment)
                : null;
            HotKitchenGroupKey key = buildGroupKey(task, item, options, noodleConfig);
            grouped.compute(key, (ignored, existing) -> {
                int quantity = task.quantity == null ? 1 : task.quantity;
                if (existing == null) {
                    return new AggregatedHotKitchenTask(task, item, quantity, noodleConfig);
                }
                return existing.addQuantity(quantity);
            });
        }
        return new ArrayList<>(grouped.values());
    }

    private KitchenTask applyHotKitchenDisplayRules(
        KitchenTask task,
        OrderItem item,
        List<OrderItemOption> options,
        PrintingDisplayRuleContext printingRules
    ) {
        if (task == null || item == null || printingRules == null) {
            return task;
        }
        String hotAlias = printingRules.resolveItemAlias(PrintModuleCode.HOT_KITCHEN, item.item_sku_snapshot, null);
        String hotSpecial = buildHotKitchenSpecialLine(item, options, printingRules);
        if ((hotAlias == null || hotAlias.isBlank()) && (hotSpecial == null || hotSpecial.isBlank())) {
            return task;
        }
        String grabAlias = printingRules.resolveItemAlias(PrintModuleCode.GRAB, item.item_sku_snapshot, null);
        KitchenTask copy = new KitchenTask();
        copy.id = task.id;
        copy.order_id = task.order_id;
        copy.order_item_id = task.order_item_id;
        copy.store_id = task.store_id;
        copy.station_code = task.station_code;
        copy.item_name_snapshot_zh = hotAlias == null || hotAlias.isBlank() ? task.item_name_snapshot_zh : hotAlias;
        copy.item_name_snapshot_en = task.item_name_snapshot_en;
        copy.special_instructions_snapshot = hotSpecial == null || hotSpecial.isBlank()
            ? replaceFirstAlias(task.special_instructions_snapshot, grabAlias, hotAlias)
            : hotSpecial;
        copy.status = task.status;
        copy.quantity = task.quantity;
        copy.priority = task.priority;
        copy.started_at = task.started_at;
        copy.completed_at = task.completed_at;
        copy.served_at = task.served_at;
        copy.cancelled_at = task.cancelled_at;
        copy.created_at = task.created_at;
        return copy;
    }

    private String buildHotKitchenSpecialLine(
        OrderItem item,
        List<OrderItemOption> options,
        PrintingDisplayRuleContext printingRules
    ) {
        if (item == null || printingRules == null) {
            return null;
        }
        List<OrderItemOption> safeOptions = options == null ? List.of() : options;
        OrderItemOption sizeOption = findOption(safeOptions, OPTION_TYPE_SIZE);
        OrderItemOption noodleOption = findOption(safeOptions, OPTION_TYPE_NOODLE_TYPE);
        OrderItemOption spicyOption = findOption(safeOptions, OPTION_TYPE_SPICY_LEVEL);
        OrderItemOption soupBaseOption = findOption(safeOptions, OPTION_TYPE_SOUP_BASE);

        String sizeCode = mapSizeCode(sizeOption, printingRules);
        String baseCode = mapItemBaseCode(item.item_sku_snapshot, printingRules);
        String soupBaseCode = mapSoupBaseCode(item.item_sku_snapshot, soupBaseOption, printingRules);
        String noodleCode = mapNoodleCode(item.item_sku_snapshot, noodleOption, printingRules);
        String spicyCode = mapSpicyCode(spicyOption, printingRules);

        List<String> inline = new ArrayList<>();
        if (sizeCode != null) {
            inline.add(sizeCode);
        }
        if (baseCode != null) {
            inline.add(baseCode);
        }
        if (soupBaseCode != null) {
            inline.add(soupBaseCode);
        }
        if (noodleCode != null) {
            inline.add(noodleCode);
        }

        String primary = String.join("", inline);
        if (primary.isBlank()) {
            primary = null;
        }
        if (spicyCode != null) {
            primary = (primary == null ? "" : primary) + spicyCode;
        }

        List<String> segments = new ArrayList<>();
        if (primary != null && !primary.isBlank()) {
            segments.add(primary);
        }
        List<String> secondary = buildHotKitchenSecondaryParts(safeOptions, printingRules);
        if (!secondary.isEmpty()) {
            segments.add(String.join(" ", secondary));
        }
        return segments.isEmpty() ? null : String.join(" | ", segments);
    }

    private OrderItemOption findOption(List<OrderItemOption> options, String optionType) {
        for (OrderItemOption option : options) {
            if (option != null && optionType.equals(option.option_type_snapshot)) {
                return option;
            }
        }
        return null;
    }

    private String mapSizeCode(OrderItemOption option, PrintingDisplayRuleContext printingRules) {
        String sizeZh = option == null ? null : option.option_name_snapshot_zh;
        if (sizeZh == null || sizeZh.isBlank()) {
            return null;
        }
        String configured = printingRules.resolveDictionaryOutput(
            "SIZE",
            PrintModuleCode.HOT_KITCHEN,
            option.option_code_snapshot,
            option.option_name_snapshot_zh,
            option.option_name_snapshot_en,
            null
        );
        if (configured != null) {
            return configured;
        }
        if (sizeZh.contains("大")) {
            return "大";
        }
        if (sizeZh.contains("小")) {
            return "小";
        }
        return "中";
    }

    private String mapItemBaseCode(String sku, PrintingDisplayRuleContext printingRules) {
        if (sku == null) {
            return null;
        }
        String configured = printingRules.resolveItemAlias(PrintModuleCode.HOT_KITCHEN, sku, null);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return switch (sku) {
            case "braised_beef_tendon_noodle" -> "红";
            case "pickled_vegetable_beef_noodle" -> "酸";
            case "beef_chow_mein" -> "牛炒";
            case "chicken_chow_mein" -> "鸡炒";
            case "tomato_chow_mein" -> "番炒";
            case "vegetable_chow_mein" -> "素炒";
            case "zha_jiang_noodle" -> "炸";
            case "dan_dan_noodle" -> "担";
            case "cold_noodle_shredded_chicken" -> "鸡凉";
            case "cucumber_salad" -> "黄瓜";
            case "edamame" -> "毛豆";
            case "shredded_potato" -> "土豆";
            case "braised_beef_shank_salad" -> "牛展";
            default -> null;
        };
    }

    private String mapNoodleCode(String sku, OrderItemOption option, PrintingDisplayRuleContext printingRules) {
        String noodleZh = option == null ? null : option.option_name_snapshot_zh;
        if (noodleZh == null || noodleZh.isBlank()) {
            return null;
        }
        String semanticCode = printingRules.resolveSemanticCode(
            "NOODLE_TYPE",
            option.option_code_snapshot,
            option.option_name_snapshot_zh,
            option.option_name_snapshot_en
        );
        if (printingRules.shouldOmit(sku, "NOODLE_TYPE", semanticCode) || isDefaultNoodleType(sku, noodleZh)) {
            return null;
        }
        String configured = printingRules.resolveDictionaryOutput(
            "NOODLE_TYPE",
            PrintModuleCode.HOT_KITCHEN,
            option.option_code_snapshot,
            option.option_name_snapshot_zh,
            option.option_name_snapshot_en,
            null
        );
        if (configured != null) {
            return configured;
        }
        return switch (noodleZh) {
            case "二细" -> "二";
            case "三细" -> "三";
            case "细" -> "细";
            case "毛细" -> "毛";
            case "韭叶" -> "韭";
            case "宽" -> "宽";
            case "大宽" -> "大宽";
            default -> noodleZh;
        };
    }

    private boolean isDefaultNoodleType(String sku, String noodleZh) {
        if (sku == null) {
            return false;
        }
        return switch (sku) {
            case "traditional_beef_noodle",
                 "braised_beef_tendon_noodle",
                 "pickled_vegetable_beef_noodle",
                 "vegetable_noodle",
                 "dan_dan_noodle" -> "三细".equals(noodleZh);
            case "zha_jiang_noodle" -> "韭叶".equals(noodleZh);
            case "cold_noodle_shredded_chicken" -> "细".equals(noodleZh) || "细面".equals(noodleZh);
            default -> false;
        };
    }

    private String mapSpicyCode(OrderItemOption option, PrintingDisplayRuleContext printingRules) {
        String spicyZh = option == null ? null : option.option_name_snapshot_zh;
        if (spicyZh == null || spicyZh.isBlank() || "不辣".equals(spicyZh)) {
            return null;
        }
        String configured = printingRules.resolveDictionaryOutput(
            "SPICINESS",
            PrintModuleCode.HOT_KITCHEN,
            option.option_code_snapshot,
            option.option_name_snapshot_zh,
            option.option_name_snapshot_en,
            null
        );
        if (configured != null) {
            return configured.isBlank() ? null : configured;
        }
        return switch (spicyZh) {
            case "少辣" -> "（少s）";
            case "正常辣" -> "（s）";
            case "加辣" -> "（大s）";
            default -> "（s）";
        };
    }

    private String mapSoupBaseCode(String sku, OrderItemOption option, PrintingDisplayRuleContext printingRules) {
        if (!"vegetable_noodle".equals(sku)) {
            return null;
        }
        String soupBaseZh = option == null ? null : option.option_name_snapshot_zh;
        if (soupBaseZh == null || soupBaseZh.isBlank()) {
            return "素";
        }
        String configured = printingRules.resolveDictionaryOutput(
            "SOUP_BASE",
            PrintModuleCode.HOT_KITCHEN,
            option.option_code_snapshot,
            option.option_name_snapshot_zh,
            option.option_name_snapshot_en,
            null
        );
        if (configured != null) {
            return configured;
        }
        if ("素汤".equals(soupBaseZh)) {
            return "素";
        }
        if ("肉汤".equals(soupBaseZh) || "牛汤".equals(soupBaseZh)) {
            return "素（肉汤）";
        }
        return null;
    }

    private List<String> buildHotKitchenSecondaryParts(
        List<OrderItemOption> options,
        PrintingDisplayRuleContext printingRules
    ) {
        List<String> parts = new ArrayList<>();
        for (OrderItemOption option : options) {
            if (option == null) {
                continue;
            }
            if (OPTION_TYPE_ADDON.equals(option.option_type_snapshot)) {
                String addonCode = canonicalAddonCode(option.option_name_snapshot_zh);
                if (isComboSideCode(addonCode)) {
                    continue;
                }
                String token = mapAddonToken(option, printingRules);
                if (token != null) {
                    parts.add(token);
                }
                continue;
            }
            if (OPTION_TYPE_REMOVE.equals(option.option_type_snapshot)) {
                if ("COMBO_SIDE_REMOVE".equalsIgnoreCase(option.option_group_snapshot)) {
                    continue;
                }
                String token = mapRemoveToken(option, printingRules);
                if (token != null) {
                    parts.add(token);
                }
            }
        }
        return parts;
    }

    private String mapAddonToken(OrderItemOption option, PrintingDisplayRuleContext printingRules) {
        String code = resolveAddonCode(option);
        if (code == null || "combo".equals(code)) {
            return null;
        }
        String mapped = switch (code) {
            case "extra_noodle" -> "+面";
            case "tea_egg", "combo_tea_egg" -> "+蛋";
            case "fried_egg", "combo_fried_egg" -> "+煎";
            case "extra_meat" -> "+肉";
            case "extra_radish" -> "+萝";
            case "bok_choy" -> "加上海青";
            case "cilantro" -> "+香";
            case "green_onion" -> "+葱";
            case "extra_sauce" -> "+酱";
            case "broccoli" -> "+西兰";
            case "cabbage" -> "+包";
            case "corn" -> "+玉";
            case "seaweed" -> "+海";
            case "mushroom" -> "+菇";
            case "carrot_slice" -> "+胡";
            case "combo_edamame" -> "+毛豆";
            case "combo_shredded_potato" -> "+土豆";
            case "combo_cucumber_salad" -> "+黄瓜";
            default -> null;
        };
        if (mapped == null) {
            return null;
        }
        mapped = printingRules.resolveModifierToken("MODIFIER_ADD", code, mapped);
        int quantity = option.quantity == null ? 1 : option.quantity;
        return quantity > 1 ? mapped + "x" + quantity : mapped;
    }

    private String mapRemoveToken(OrderItemOption option, PrintingDisplayRuleContext printingRules) {
        String code = resolveRemoveCode(option);
        if (code == null) {
            return null;
        }
        String fallback = switch (code) {
            case "cilantro" -> "走香";
            case "green_onion" -> "走葱";
            case "beef" -> "走牛";
            case "radish" -> "走萝";
            case "noodle" -> "走面";
            case "less_noodle" -> "少面";
            case "bok_choy" -> "走上海青";
            case "broccoli" -> "走西兰";
            case "corn" -> "走玉米";
            case "mushroom" -> "走菇";
            case "seaweed" -> "走海";
            case "carrot" -> "走胡";
            case "cucumber" -> "走黄瓜";
            case "edamame" -> "走毛豆";
            case "peanut" -> "走花生";
            case "cabbage" -> "走包";
            case "meat" -> "走肉";
            case "green_pepper" -> "走青椒";
            default -> option.option_name_snapshot_zh;
        };
        return printingRules.resolveModifierToken("MODIFIER_REMOVE", code, fallback);
    }

    private String resolveAddonCode(OrderItemOption option) {
        if (option.option_code_snapshot != null && !option.option_code_snapshot.isBlank()) {
            return option.option_code_snapshot;
        }
        return canonicalAddonCode(option.option_name_snapshot_zh);
    }

    private String resolveRemoveCode(OrderItemOption option) {
        if (option.option_code_snapshot != null && !option.option_code_snapshot.isBlank()) {
            String code = option.option_code_snapshot;
            return code.startsWith("remove_") ? code.substring("remove_".length()) : code;
        }
        return canonicalRemoveCode(option.option_name_snapshot_zh);
    }

    private boolean isComboSideCode(String code) {
        if (code == null) {
            return false;
        }
        String normalized = code.toLowerCase(Locale.ROOT);
        return normalized.contains("combo_edamame")
            || normalized.contains("combo_shredded_potato")
            || normalized.contains("combo_cucumber_salad");
    }

    private String canonicalAddonCode(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        return switch (label) {
            case "套餐" -> "combo";
            case "加面" -> "extra_noodle";
            case "加蛋", "套餐卤蛋" -> "tea_egg";
            case "加煎蛋", "套餐煎蛋" -> "fried_egg";
            case "加肉" -> "extra_meat";
            case "加萝卜" -> "extra_radish";
            case "加上海青" -> "bok_choy";
            case "加香菜" -> "cilantro";
            case "加葱" -> "green_onion";
            case "加酱" -> "extra_sauce";
            case "加西兰花" -> "broccoli";
            case "加包菜" -> "cabbage";
            case "加玉米" -> "corn";
            case "加海菜" -> "seaweed";
            case "加蘑菇" -> "mushroom";
            case "加胡萝卜片" -> "carrot_slice";
            case "套餐毛豆" -> "combo_edamame";
            case "套餐土豆丝" -> "combo_shredded_potato";
            case "套餐拌黄瓜" -> "combo_cucumber_salad";
            default -> null;
        };
    }

    private String canonicalRemoveCode(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        return switch (label) {
            case "走香菜", "不要香菜" -> "cilantro";
            case "走葱", "不要葱", "走洋葱" -> "green_onion";
            case "走牛肉" -> "beef";
            case "走萝卜" -> "radish";
            case "走面", "No Noodle" -> "noodle";
            case "少面" -> "less_noodle";
            case "走上海青" -> "bok_choy";
            case "走西兰花" -> "broccoli";
            case "走玉米" -> "corn";
            case "走蘑菇" -> "mushroom";
            case "走海菜" -> "seaweed";
            case "走胡萝卜片", "走胡萝卜" -> "carrot";
            case "走黄瓜" -> "cucumber";
            case "走毛豆" -> "edamame";
            case "走花生", "走花生碎" -> "peanut";
            case "走包菜" -> "cabbage";
            case "走肉" -> "meat";
            case "走青椒" -> "green_pepper";
            default -> null;
        };
    }

    private String replaceFirstAlias(String value, String fromAlias, String toAlias) {
        if (value == null || value.isBlank() || fromAlias == null || fromAlias.isBlank() || fromAlias.equals(toAlias)) {
            return value;
        }
        int index = value.indexOf(fromAlias);
        if (index < 0) {
            return value;
        }
        return value.substring(0, index) + toAlias + value.substring(index + fromAlias.length());
    }

    private HotKitchenGroupKey buildGroupKey(
        KitchenTask task,
        OrderItem item,
        List<OrderItemOption> options,
        KitchenNoodlePrintFormatter.NoodleConfig noodleConfig
    ) {
        if (noodleConfig != null) {
            KitchenNoodlePrintFormatter.NoodleGroupKey noodleKey = KitchenNoodlePrintFormatter.buildGroupKey(
                task,
                item,
                options,
                noodleConfig
            );
            return new HotKitchenGroupKey(
                noodleKey.menuItemId(),
                noodleKey.categoryCode(),
                item == null ? null : item.combo_role,
                noodleKey.stationCode(),
                noodleKey.displayText(),
                noodleKey.notes(),
                noodleKey.optionKeys()
            );
        }

        List<String> optionKeys = options.stream()
            .sorted(Comparator
                .comparing((OrderItemOption option) -> stable(option.option_group_snapshot))
                .thenComparing(option -> stable(option.option_type_snapshot))
                .thenComparing(option -> stable(option.option_code_snapshot))
                .thenComparing(option -> option.option_id == null ? Long.MAX_VALUE : option.option_id)
                .thenComparing(option -> option.id == null ? Long.MAX_VALUE : option.id))
            .map(this::buildStableOptionKey)
            .toList();
        return new HotKitchenGroupKey(
            item == null ? null : item.menu_item_id,
            item == null ? null : item.category_code_snapshot,
            item == null ? null : item.combo_role,
            stable(task.station_code),
            stable(task.special_instructions_snapshot),
            stable(item == null ? null : item.notes),
            optionKeys
        );
    }

    private String buildStableOptionKey(OrderItemOption option) {
        return String.join("|",
            stable(option.option_group_snapshot),
            stable(option.option_type_snapshot),
            stable(option.option_code_snapshot),
            String.valueOf(option.option_id),
            String.valueOf(option.parent_option_id_snapshot),
            String.valueOf(option.quantity == null ? 1 : option.quantity),
            String.valueOf(option.price_delta == null ? BigDecimal.ZERO : option.price_delta.stripTrailingZeros())
        );
    }

    private String resolvePrimaryLine(KitchenTask task, int quantity) {
        String itemName = fallback(task.item_name_snapshot_zh, task.item_name_snapshot_en, "Item");
        String special = normalize(task.special_instructions_snapshot);
        if (special != null && shouldUseSpecialAsPrimary(itemName, special)) {
            return special + " ×" + quantity;
        }
        return itemName + " ×" + quantity;
    }

    private String resolveSecondaryLine(KitchenTask task) {
        String itemName = fallback(task.item_name_snapshot_zh, task.item_name_snapshot_en, "Item");
        String special = normalize(task.special_instructions_snapshot);
        if (special == null || shouldUseSpecialAsPrimary(itemName, special)) {
            return null;
        }
        return special;
    }

    private boolean shouldUseSpecialAsPrimary(String itemName, String special) {
        if (special.contains("|")) {
            return true;
        }
        if (itemName.contains(special) && !special.equals(itemName)) {
            return true;
        }
        for (String prefix : MODIFIER_PREFIXES) {
            if (special.startsWith(prefix)) {
                return false;
            }
        }
        return special.length() <= 12;
    }

    private String resolveTopDisplayLabel(Order order) {
        if (order == null) {
            return null;
        }
        if (isTakeout(order)) {
            if (order.pickup_no != null && !order.pickup_no.isBlank()) {
                return order.pickup_no;
            }
            return null;
        }
        if (order.table_no != null && !order.table_no.isBlank()) {
            return "桌号：" + PrintTableDisplayFormatter.formatSplitTableLabel(order.table_no);
        }
        return "桌号：Walk-in";
    }

    private boolean isTakeout(Order order) {
        return order != null && ("pickup".equalsIgnoreCase(order.order_type) || "takeout".equalsIgnoreCase(order.order_type));
    }

    private String resolveTime(Order order) {
        if (order != null && order.submitted_at != null) {
            return order.submitted_at.format(TIME_FORMATTER);
        }
        if (order != null && order.created_at != null) {
            return order.created_at.format(TIME_FORMATTER);
        }
        return java.time.LocalTime.now().format(TIME_FORMATTER);
    }

    private String fallback(String primary, String secondary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (secondary != null && !secondary.isBlank()) {
            return secondary.trim();
        }
        return fallback;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String stable(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private record HotKitchenGroupKey(
        Long menuItemId,
        String categoryCode,
        String comboRole,
        String stationCode,
        String specialInstructions,
        String notes,
        List<String> optionKeys
    ) {
    }

    private record AggregatedHotKitchenTask(
        KitchenTask representative,
        OrderItem item,
        int quantity,
        KitchenNoodlePrintFormatter.NoodleConfig noodleConfig
    ) {
        AggregatedHotKitchenTask addQuantity(int delta) {
            return new AggregatedHotKitchenTask(representative, item, quantity + delta, noodleConfig);
        }
    }
}
