package com.restaurant.system.printing.semantic;

import com.restaurant.system.order.entity.OrderItemOption;
import com.restaurant.system.printing.rules.PrintingDisplayRuleContext;

/** Shared wording for frozen GRAB instructions, HOT_KITCHEN and rule preview. */
public final class KitchenSpicinessDisplayResolver {

    private KitchenSpicinessDisplayResolver() {
    }

    public static String resolve(String outputType, OrderItemOption option, PrintingDisplayRuleContext rules) {
        if (option == null) {
            return null;
        }
        return resolve(outputType, option.option_code_snapshot, option.option_name_snapshot_zh,
            option.option_name_snapshot_en, rules);
    }

    public static String resolve(
        String outputType,
        String optionCode,
        String optionZh,
        String optionEn,
        PrintingDisplayRuleContext rules
    ) {
        if (rules != null) {
            String configured = rules.resolveDictionaryOutput(
                "SPICINESS", outputType, optionCode, optionZh, optionEn, null
            );
            if (configured != null) {
                // An explicit empty output suppresses the token; missing/null inherits legacy wording.
                return configured.isBlank() ? null : configured;
            }
        }
        if (optionZh == null || optionZh.isBlank() || "不辣".equals(optionZh)) {
            return null;
        }
        return switch (optionZh) {
            case "少辣" -> "（少s）";
            case "正常辣" -> "（s）";
            case "加辣" -> "（大s）";
            default -> "（s）";
        };
    }
}
