package com.restaurant.system.printing.semantic;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.restaurant.system.order.entity.OrderItemOption;
import com.restaurant.system.printing.PrintModuleCode;
import com.restaurant.system.printing.rules.PrintingDisplayRuleContext;
import org.junit.jupiter.api.Test;

class KitchenSpicinessDisplayResolverTest {

    @Test
    void explicitNoSpicyWordingWinsBeforeLegacySuppressionForEachKitchen() {
        PrintingDisplayRuleContext rules = customRules("（走G）", "（走H）");
        OrderItemOption option = option("spicy_none", "不辣");

        assertThat(KitchenSpicinessDisplayResolver.resolve(PrintModuleCode.GRAB, option, rules)).isEqualTo("（走G）");
        assertThat(KitchenSpicinessDisplayResolver.resolve(PrintModuleCode.HOT_KITCHEN, option, rules)).isEqualTo("（走H）");
        assertThat(rules.resolveDictionaryOutput("SPICINESS", PrintModuleCode.FRONTDESK_RECEIPT,
            "spicy_none", "不辣", null, null)).isEqualTo("不辣");
    }

    @Test
    void stableCodeStillResolvesWhenFrozenChineseLabelIsRenamedOrMissing() {
        for (String label : new String[] {"无辣椒", null, ""}) {
            assertThat(KitchenSpicinessDisplayResolver.resolve(PrintModuleCode.GRAB,
                option("spicy_none", label), customRules("（走s）", "（走s）"))).isEqualTo("（走s）");
        }
    }

    @Test
    void emptyOutputSuppressesWhileNullAndMissingOutputsInheritLegacyWording() {
        PrintingDisplayRuleContext rules = customRules("", null);
        ObjectNode noSpicyOutputs = (ObjectNode) rules.content().path("dictionaries").path("SPICINESS").get(0).path("outputs");
        noSpicyOutputs.remove("HOT_KITCHEN");
        assertThat(KitchenSpicinessDisplayResolver.resolve(PrintModuleCode.GRAB, option("spicy_none", "不辣"), rules)).isNull();
        assertThat(KitchenSpicinessDisplayResolver.resolve(PrintModuleCode.HOT_KITCHEN, option("spicy_none", "不辣"), rules)).isNull();

        ObjectNode lessSpicyOutputs = (ObjectNode) rules.content().path("dictionaries").path("SPICINESS").get(1).path("outputs");
        lessSpicyOutputs.put("GRAB", "");
        lessSpicyOutputs.putNull("HOT_KITCHEN");
        assertThat(KitchenSpicinessDisplayResolver.resolve(PrintModuleCode.GRAB, option("spicy_less", "少辣"), rules)).isNull();
        assertThat(KitchenSpicinessDisplayResolver.resolve(PrintModuleCode.HOT_KITCHEN, option("spicy_less", "少辣"), rules)).isEqualTo("（少s）");
        lessSpicyOutputs.remove("HOT_KITCHEN");
        assertThat(KitchenSpicinessDisplayResolver.resolve(PrintModuleCode.HOT_KITCHEN, option("spicy_less", "少辣"), rules)).isEqualTo("（少s）");
    }

    @Test
    void customizationDoesNotChangeAnotherStoresDefaultRulesOrLegacyFallback() {
        OrderItemOption noSpicy = option("spicy_none", "不辣");
        PrintingDisplayRuleContext otherStore = PrintingDisplayRuleContext.defaultContext();
        assertThat(KitchenSpicinessDisplayResolver.resolve(PrintModuleCode.GRAB, noSpicy, customRules("（走s）", "（走s）"))).isEqualTo("（走s）");
        assertThat(KitchenSpicinessDisplayResolver.resolve(PrintModuleCode.GRAB, noSpicy, otherStore)).isNull();
        assertThat(KitchenSpicinessDisplayResolver.resolve(PrintModuleCode.HOT_KITCHEN, noSpicy, otherStore)).isNull();
        assertThat(KitchenSpicinessDisplayResolver.resolve(PrintModuleCode.GRAB, null, otherStore)).isNull();
        assertThat(KitchenSpicinessDisplayResolver.resolve(PrintModuleCode.GRAB, option("old", "加辣"), null)).isEqualTo("（大s）");
        assertThat(KitchenSpicinessDisplayResolver.resolve(PrintModuleCode.GRAB, option("old", "旧辣度"), null)).isEqualTo("（s）");
    }

    private OrderItemOption option(String code, String label) {
        OrderItemOption option = new OrderItemOption();
        option.option_code_snapshot = code;
        option.option_name_snapshot_zh = label;
        return option;
    }

    private PrintingDisplayRuleContext customRules(String grab, String hot) {
        PrintingDisplayRuleContext rules = PrintingDisplayRuleContext.defaultContext();
        ObjectNode outputs = (ObjectNode) rules.content().path("dictionaries").path("SPICINESS").get(0).path("outputs");
        outputs.put("GRAB", grab);
        outputs.put("HOT_KITCHEN", hot);
        return rules;
    }
}
