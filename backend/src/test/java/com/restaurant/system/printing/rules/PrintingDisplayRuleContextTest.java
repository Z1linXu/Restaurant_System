package com.restaurant.system.printing.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.restaurant.system.printing.PrintModuleCode;
import org.junit.jupiter.api.Test;

class PrintingDisplayRuleContextTest {

    @Test
    void defaultRulesResolveIndependentOutputAliasesAndDictionaryTokens() {
        PrintingDisplayRuleContext context = PrintingDisplayRuleContext.defaultContext();

        assertThat(context.resolveItemAlias(PrintModuleCode.GRAB, "beef_chow_mein", "牛肉炒面"))
            .isEqualTo("牛炒");
        assertThat(context.resolveItemAlias(PrintModuleCode.HOT_KITCHEN, "beef_chow_mein", "牛肉炒面"))
            .isEqualTo("牛炒");
        assertThat(context.resolveItemAlias(PrintModuleCode.FRONTDESK_RECEIPT, "traditional_beef_noodle", "传统牛肉面"))
            .isEqualTo("牛肉面");
        assertThat(context.resolveItemAlias(PrintModuleCode.FRONTDESK_RECEIPT, "beef_chow_mein", "牛肉炒面"))
            .isEqualTo("牛肉炒面");

        assertThat(context.resolveDictionaryOutput("SIZE", PrintModuleCode.GRAB, "size_large", null, null, "大碗"))
            .isEqualTo("大");
        assertThat(context.resolveDictionaryOutputKey("SIZE", "FRONTDESK_RECEIPT_EN", "size_large", null, null, "Large"))
            .isEqualTo("Large");
        assertThat(context.resolveModifierToken("MODIFIER_ADD", "tea_egg", null))
            .isEqualTo("+蛋");
        assertThat(context.resolveModifierToken("MODIFIER_REMOVE", "green_onion", null))
            .isEqualTo("走葱");
    }

    @Test
    void constrainedConditionalRulesCanOmitKnownSemanticSelections() {
        PrintingDisplayRuleContext context = PrintingDisplayRuleContext.defaultContext();

        String semanticCode = context.resolveSemanticCode("NOODLE_TYPE", null, "三细", null);

        assertThat(semanticCode).isEqualTo("SAN_XI");
        assertThat(context.shouldOmit("traditional_beef_noodle", "NOODLE_TYPE", semanticCode)).isTrue();
        assertThat(context.shouldOmit("beef_chow_mein", "NOODLE_TYPE", semanticCode)).isFalse();
    }
}
