package com.restaurant.system.printing.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.restaurant.system.menu.remove.RemoveSemantics;
import com.restaurant.system.owner.profile.StoreProfileCanonicalJson;
import com.restaurant.system.printing.semantic.KitchenModifierTokenResolver;
import org.junit.jupiter.api.Test;

class PrintingRemoveAliasesTest {
    @Test
    void proposalOnlyDeduplicatesEqualExplicitPrefixAliasesWithoutMutatingSource() {
        var content = content("[[\"remove_beef\",\"牛定制\"],[\"beef\",\"牛定制\"],[\"meat\",\"肉定制\"],[\"remove_radish\",\"萝定制\"],[\"carrot\",\"胡定制\"],[\"noodle\",\"走面\"],[\"less_noodle\",\"少面\"]]");
        String original = content.toString();
        var analysis = PrintingRemoveAliases.analyze(content);
        assertThat(analysis.conflicts()).isEmpty();
        assertThat(analysis.proposed_aliases()).hasSize(6);
        assertThat(analysis.proposed_aliases().toString()).contains("[\"beef\",\"牛定制\"]", "[\"meat\",\"肉定制\"]",
            "[\"radish\",\"萝定制\"]", "[\"carrot\",\"胡定制\"]", "[\"noodle\",\"走面\"]", "[\"less_noodle\",\"少面\"]");
        assertThat(content.toString()).isEqualTo(original);
    }

    @Test
    void conflictingAliasesRemainUnchangedAndCanonicalPublishedValueKeepsPrecedence() {
        var content = content("[[\"remove_beef\",\"旧牛\"],[\"beef\",\"新牛\"],[\"remove_remove_beef\",\"另一语义\"]]");
        var analysis = PrintingRemoveAliases.analyze(content);
        assertThat(analysis.conflicts()).extracting(PrintingRemoveAliases.Conflict::code).containsExactly("beef");
        assertThat(analysis.proposed_aliases().get(0)).isEqualTo(content.path("dictionaries").path("MODIFIER_REMOVE").get(0));
        assertThat(analysis.proposed_aliases().get(1)).isEqualTo(content.path("dictionaries").path("MODIFIER_REMOVE").get(1));
        assertThat(PrintingRemoveAliases.resolve(content, "remove_beef", "走牛")).isEqualTo("新牛");
        assertThat(PrintingRemoveAliases.resolve(content, "remove_remove_beef", "fallback")).isEqualTo("另一语义");
    }

    @Test
    void resetUsesDefaultAndAmbiguousIngredientCodesNeverBorrowOtherAliases() {
        var rules = new PrintingDisplayRuleContext(10L, 6, "frozen", content("[[\"remove_beef\",\"牛自定\"],[\"carrot\",\"胡自定\"],[\"cucumber\",\"瓜自定\"],[\"meat\",null]]"));
        assertThat(KitchenModifierTokenResolver.resolveRemove("beef", "旧牛名", null, rules)).isEqualTo("牛自定");
        assertThat(KitchenModifierTokenResolver.resolveRemove("remove_meat", "旧肉名", null, rules)).isEqualTo("走肉");
        assertThat(KitchenModifierTokenResolver.resolveRemove("remove_carrot_slice", "不要胡萝卜片", null, rules)).isEqualTo("不要胡萝卜片");
        assertThat(KitchenModifierTokenResolver.resolveRemove("remove_cucumber_shred", "不要黄瓜丝", null, rules)).isEqualTo("不要黄瓜丝");
        assertThat(KitchenModifierTokenResolver.resolveRemove("new_ingredient", "不要豆腐", null, rules)).isEqualTo("不要豆腐");
        assertThat(KitchenModifierTokenResolver.resolveRemove(null, "旧快照去除项", null, rules)).isNotBlank();
    }

    @Test
    void allEighteenDefaultsWorkWithoutAliasesAndStoreRulesRemainIndependent() {
        var empty = new PrintingDisplayRuleContext(null, 1, "empty", content("[]"));
        RemoveSemantics.baselineDefaults().forEach((code, text) ->
            assertThat(KitchenModifierTokenResolver.resolveRemove("remove_" + code, "旧名", null, empty)).isEqualTo(text));
        var other = new PrintingDisplayRuleContext(5L, 2, "other", content("[[\"beef\",\"店一牛\"]]"));
        assertThat(KitchenModifierTokenResolver.resolveRemove("remove_beef", "旧名", null, other)).isEqualTo("店一牛");
        assertThat(KitchenModifierTokenResolver.resolveRemove("remove_beef", "旧名", null, empty)).isEqualTo("走牛");
    }

    @Test
    void malformedLegacyPairsAreRetainedByDryRunRatherThanSilentlyDeleted() {
        var content = content("[[\"beef\",\"走牛\"],[\"\",\"blank code\"],[\"future\",{\"unexpected\":true}],[\"broken\"]]");
        assertThat(PrintingRemoveAliases.analyze(content).proposed_aliases()).isEqualTo(content.path("dictionaries").path("MODIFIER_REMOVE"));
    }

    static ObjectNode content(String pairs) {
        var content = (ObjectNode) StoreProfileCanonicalJson.parse(PrintingDisplayRuleDefaults.DEFAULT_CONTENT_JSON);
        content.with("dictionaries").set("MODIFIER_REMOVE", StoreProfileCanonicalJson.parse(pairs));
        return content;
    }
}
